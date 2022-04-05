package cz.mzk.mapseries.update;

import cz.mzk.mapseries.dao.interfaces.VersionedData;
import cz.mzk.mapseries.managers.UpdateTaskManager;
import cz.mzk.mapseries.github.GithubService;
import cz.mzk.mapseries.github.GithubServiceUnauthorized;
import cz.mzk.mapseries.jsf.beans.Configuration;
import cz.mzk.mapseries.oai.marc.MarcRecord;
import cz.mzk.mapseries.oai.marc.OaiMarcXmlReader;
import cz.mzk.mapseries.dao.SerieDAO;
import cz.mzk.mapseries.dao.SheetDAO;
import cz.mzk.mapseries.dao.UpdateTaskDAO;
import cz.mzk.mapseries.managers.ContentDefinitionItem;
import cz.mzk.mapseries.managers.ContentDefinitionManager;
import cz.mzk.mapseries.oai.marc.MarcIdentifier;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Lock;
import static javax.ejb.LockType.READ;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Queue;
import javax.jms.TextMessage;
import org.jboss.logging.Logger;

/**
 * @author Erich Duda <dudaerich@gmail.com>
 */
@Singleton
public class UpdateEJB {
    
    private static final Logger LOG = Logger.getLogger(UpdateEJB.class);
    
    public static final String TASK_ID_KEY = "taskId";
    
    private volatile UpdateTaskDAO runningTask = null;
    
    @EJB
    private UpdateTaskManager updateTaskManager;
    
    @Inject
    private JMSContext context;
    
    @Inject
    private GithubService githubService;
    
    @Inject
    private GithubServiceUnauthorized githubServiceUnauthorized;

    @Resource(lookup = "java:/jms/queue/UpdateTasks")
    private Queue queue;
    
    private PrintStream log;

    private ExecutorService executor;
    
    @Lock(READ)
    public void scheduleUpdateTask() throws Exception {
        
        List<UpdateTaskDAO> unfinishedTasks = updateTaskManager.getUnfinishedTasks();
        if (unfinishedTasks.size() >= 2) {
            return;
        }
        
        UpdateTaskDAO updateTaskDAO = new UpdateTaskDAO();
        updateTaskManager.persistTask(updateTaskDAO);
        
        TextMessage msg = context.createTextMessage(githubService.loadFile("/" + Configuration.CONTENT_DEFINITION_PATH));
        msg.setLongProperty(TASK_ID_KEY, updateTaskDAO.getId());
        LOG.info("Schedule Update Task. ");
        context.createProducer().send(queue, msg);
    }
    
//    @Schedule(second = "0", minute = "0", hour = "3", persistent = false)
    public void scheduledAutomatically() {
        try {
            List<UpdateTaskDAO> unfinishedTasks = updateTaskManager.getUnfinishedTasks();
            if (unfinishedTasks.size() >= 2) {
                return;
            }

            UpdateTaskDAO updateTaskDAO = new UpdateTaskDAO();
            updateTaskManager.persistTask(updateTaskDAO);

            TextMessage msg = context.createTextMessage(githubServiceUnauthorized.loadFile(Configuration.CONTENT_DEFINITION_PATH));
            msg.setLongProperty(TASK_ID_KEY, updateTaskDAO.getId());
            context.createProducer().send(queue, msg);
        } catch (Exception e) {
            LOG.error("Error thrown when automatically scheduled the task.", e);
            throw new RuntimeException("Error thrown when automatically scheduled the task.", e);
        }
    }
    
    @Lock(READ)
    public synchronized UpdateTaskResult runUpdateTask(UpdateTaskDAO updateTaskDAO, String definitionJson) {
        
        runningTask = updateTaskDAO;
        
        File logFile = createTempFile();
        LOG.info("LOGFILE= " + logFile);
        log = createPrintStream(logFile);
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        
        try {
            List<VersionedData> data = doUpdate(definitionJson);
            return new UpdateTaskResult(logFile, data);
            
        } catch (Exception e) {
            log.println("[TASK FAILED] " + e);
            e.printStackTrace(log);
            
            return new UpdateTaskResult(logFile);
            
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace(log);
            }

            log.close();
            runningTask = null;
            log = null;
            executor = null;
        }
    }
    
    private File createTempFile() {
        try {
            return File.createTempFile("/mapseries_logs/mapseries", ".log");
        } catch (IOException e) {
            throw new RuntimeException("Error while creating temp file.", e);
        }
    }
    
    private PrintStream createPrintStream(File f) {
        try {
            return new PrintStream(f);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Lock(READ)
    public UpdateTaskDAO getRunningTask() {
        return runningTask;
    }
    
    private List<VersionedData> doUpdate(String definitionJson) throws Exception {
        log.println("Starting update.");
        LOG.info("Starting update"); 
        List<VersionedData> result = new ArrayList<>();
        
        ContentDefinitionManager contentDefinitionManager = ContentDefinitionManager.fromJsonString(definitionJson);
        List<ContentDefinitionItem> definitions = contentDefinitionManager.getDefinitions();
        Map<ContentDefinitionItem, SerieDAO> series = new HashMap<>();
        
        OaiMarcXmlReader oaiMarcXmlReader = new OaiMarcXmlReader("http://aleph.mzk.cz/OAI", "MZK01-MAPY");
        
        for (MarcRecord marcRecord : oaiMarcXmlReader) {
            //LOG.info(marcRecord.toString());
            Optional<ContentDefinitionItem> definition = findDefinitionForRecord(definitions, marcRecord);
            if (!definition.isPresent()) {
                continue;
            }
            
            SerieDAO serie = series.get(definition.get());
            if (serie == null) {
                SerieBuilder builder = new SerieBuilder(definition.get());
                serie = builder.buildSerie();
                series.put(definition.get(), serie);
                result.add(serie);
                
                DescriptionBuilder descriptionBuilder = new DescriptionBuilder(definition.get(), serie);
                result.addAll(descriptionBuilder.buildDescriptions());
            }
            LOG.info(marcRecord.toString());
            LOG.info("\n");
            SheetBuilder sheetBuilder = new SheetBuilder(definition.get(), marcRecord, log, executor);
            Optional<SheetDAO> optSheetDAO = sheetBuilder.buildSheet();
            
            if (!optSheetDAO.isPresent()) {
                continue;
            }
            
            SheetDAO sheetDAO = optSheetDAO.get();
            sheetDAO.setSerie(serie.getName());
            
            result.add(sheetDAO);
        }
        
        log.println("Update finished successfully");
        LOG.info("Update finished successfully");
        return result;
    }
    
    private Optional<ContentDefinitionItem> findDefinitionForRecord(List<ContentDefinitionItem> definitions, MarcRecord marcRecord) throws Exception {
        
        for (ContentDefinitionItem definition : definitions) {
            if (isDefinitionSuitableForRecord(definition, marcRecord)) {
                return Optional.of(definition);
            }
        }
        
        return Optional.empty();
    }
    
    private boolean isDefinitionSuitableForRecord(ContentDefinitionItem definition, MarcRecord marcRecord) throws Exception {
        String field = definition.getField();
        MarcIdentifier marcId = MarcIdentifier.fromString(field);
        return marcRecord
                .getDataFields(marcId.getField())
                .stream()
                .map(dataField -> dataField.getSubfield(marcId.getSubfields().get(0)))
                .anyMatch(subfield -> subfield.isPresent() && subfield.get().equals(definition.getName()));
    }
    
}

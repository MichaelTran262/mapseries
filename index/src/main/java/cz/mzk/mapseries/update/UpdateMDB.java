package cz.mzk.mapseries.update;

import cz.mzk.mapseries.dao.CurrentVersionDAO;
import cz.mzk.mapseries.dao.interfaces.VersionedData;
import cz.mzk.mapseries.managers.UpdateTaskManager;
import cz.mzk.mapseries.dao.UpdateTaskDAO;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Topic;
import javax.persistence.EntityManager;
import org.hibernate.Session;
import org.jboss.logging.Logger;

/**
 * @author Erich Duda <dudaerich@gmail.com>
 */
@MessageDriven(name = "UpdateMDB", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "jms/queue/UpdateTasks"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")})
public class UpdateMDB implements MessageListener {
    
    private static final Logger LOG = Logger.getLogger(UpdateMDB.class);
    private static final int HISTORY = 10;
    
    @EJB
    private UpdateTaskManager updateTaskManager;
    
    @Inject
    private EntityManager em;
    
    @EJB
    private UpdateEJB updateEJB;
    
    @Inject
    private JMSContext context;
    
    @Resource(lookup = "java:/jms/queue/UpdateTasksNotifications")
    private Topic topic;

    @Override
    public void onMessage(Message msg) {
        try {
            long taskId = msg.getLongProperty(UpdateEJB.TASK_ID_KEY);
            UpdateTaskDAO updateTaskDAO = updateTaskManager.findById(taskId);
            updateTaskDAO.setStartDate(ZonedDateTime.now());
            LOG.info("UpdateMDB.java, onMessage");
            UpdateTaskResult result = updateEJB.runUpdateTask(updateTaskDAO, msg.getBody(String.class));
            
            // Only if the task passed, persist the series
            if (result.getData().isPresent()) {

                clearOldData();

                updateTaskDAO.setResult(true);

                for (VersionedData d : result.getData().get()) {
                    d.setVersion(taskId);
                    em.persist(d);
                }

                CurrentVersionDAO currentVersionDAO = new CurrentVersionDAO();
                currentVersionDAO.setValue(taskId);
                em.merge(currentVersionDAO);

            } else {
                updateTaskDAO.setResult(false);
            }
            
            Session session = em.unwrap(Session.class);
            
            try {
                Reader reader = new FileReader(result.getLogFile());
                Clob clob = session.getLobHelper().createClob(reader, result.getLogSize());
                updateTaskDAO.setLog(clob);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                result.getLogFile().delete();
            }
            
            updateTaskDAO.setEndDate(ZonedDateTime.now());
            
            context.createProducer().send(topic, context.createMessage());
            
        } catch (JMSException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void clearOldData() {

        for (Long taskToDelete : getIdsOfTasksToDelete()) {

            em.createQuery("DELETE FROM SheetDAO s WHERE s.version = :task").setParameter("task", taskToDelete).executeUpdate();
            em.createQuery("DELETE FROM SerieDAO s WHERE s.version = :task").setParameter("task", taskToDelete).executeUpdate();
            em.createQuery("DELETE FROM DescriptionDAO d WHERE d.version = :task").setParameter("task", taskToDelete).executeUpdate();
            em.createQuery("DELETE FROM UpdateTaskDAO t WHERE t.id = :task").setParameter("task", taskToDelete).executeUpdate();
        }

    }

    private List<Long> getIdsOfTasksToDelete() {
        List<Long> ids = em.createQuery(
                "SELECT t.id FROM UpdateTaskDAO t WHERE t.id <> (SELECT value FROM CurrentVersionDAO) ORDER BY t.id ASC", Long.class)
                .getResultList();

        int numberOfTasksToDelete = ids.size() - HISTORY;

        if (numberOfTasksToDelete > 0) {
            return ids.subList(0, numberOfTasksToDelete);
        } else {
            return Collections.emptyList();
        }
    }
    
}

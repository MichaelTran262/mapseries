package cz.mzk.mapseries.jsf.beans;

import cz.mzk.mapseries.update.UpdateEJB;
import cz.mzk.mapseries.dao.UpdateTaskDAO;
import cz.mzk.mapseries.managers.ContentDefinitionItem;
import cz.mzk.mapseries.managers.ContentDefinitionManager;
import cz.mzk.mapseries.managers.UpdateTaskManager;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.ejb.EJB;
import javax.enterprise.inject.Model;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.jboss.logging.Logger;

/**
 * @author Erich Duda <dudaerich@gmail.com>
 */
@Model
public class Configuration {
    
    private static final Logger LOG = Logger.getLogger(Configuration.class);
    
    public static final String CONTENT_DEFINITION_PATH = "content-definition.json";
    
    private Long updateTaskId;
    
    private Long currentVersion;
    
    @Inject
    private ContentDefinitionManager contentDefinitionManager;
    
    @EJB
    private UpdateTaskManager updateTaskManager;
    
    @EJB
    private UpdateEJB updateEJB;
    
    private UpdateTaskDAO updateTaskDAO;

    public Long getUpdateTaskId() {
        return updateTaskId;
    }

    public void setUpdateTaskId(Long updateTaskId) {
        this.updateTaskId = updateTaskId;
        updateTaskDAO = updateTaskManager.findById(updateTaskId);
    }
    
    public UpdateTaskDAO getUpdateTask() {
        return updateTaskDAO;
    }
    
    public boolean isUpdateTaskLogAvailable() {
        return updateTaskDAO.getLog() != null;
    }
    
    public String getUpdateTaskLog() {
        
        try {
            if (updateTaskDAO.getLog().length() > 10l * 1024 * 1024) {
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                String contextPath = externalContext.getApplicationContextPath();
                externalContext.redirect(contextPath + "/" + "admin/log?taskId=" + updateTaskId);
                return null;
            } else {
                return IOUtils.toString(updateTaskDAO.getLog().getCharacterStream());
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public String getContentDefinitionData() {
        return contentDefinitionManager.getRawData();
    }
    
    public List<ContentDefinitionItem> getContentDefinitions() {
        return contentDefinitionManager.getDefinitions();
    }
    
    public List<UpdateTaskDAO> getUpdateTasks() {
        return updateTaskManager.getTasks();
    }
    
    public boolean isTaskActive(UpdateTaskDAO task) {
        //LOG.infof("Comparing %d = %d", task.getId(), getCurrentVersion());
        return task.getId() == getCurrentVersion();
    }
    
    private long getCurrentVersion() {
        if (currentVersion == null) {
            currentVersion = updateTaskManager.getCurrentVersion();
        }
        
        return currentVersion;
    }
    
    public String computeDuration(UpdateTaskDAO task) {
        if (task.getStartDate() == null || task.getEndDate() == null) {
            return "";
        }
        Duration duration = Duration.between(task.getStartDate(), task.getEndDate());
        return DurationFormatUtils.formatDuration(duration.getSeconds() * 1000, "HH:mm:ss");
    }
    
    public String formatDate(ZonedDateTime date) {
        if (date == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("dd. MM. yyyy - HH:mm:ss", Locale.forLanguageTag("cs")).format(date);
    }
    
    public String printStatusOfTask(UpdateTaskDAO task) {
        if (task.equals(updateEJB.getRunningTask())) {
            return "running";
        }
        if (task.getStartDate() == null) {
            return "scheduled";
        }
        if (task.isResult()) {
            return "passed";
        } else {
            return "failed";
        }
    }
}

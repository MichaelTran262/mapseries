package cz.mzk.mapseries.rest;

import cz.mzk.mapseries.managers.AdminManager;
import cz.mzk.mapseries.github.GithubService;
import cz.mzk.mapseries.jsf.beans.Configuration;
import cz.mzk.mapseries.managers.UpdateTaskManager;
import cz.mzk.mapseries.update.UpdateEJB;
import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

/**
 * @author Erich Duda <dudaerich@gmail.com>
 */
@Path("/ajax")
public class AjaxRestApi {
    
    private static final Logger LOG = Logger.getLogger(AjaxRestApi.class);
    
    @Inject
    private GithubService githubService;
    
    @EJB
    private UpdateEJB updateEJB;
    
    @EJB
    private AdminManager adminManager;
    
    @EJB
    private UpdateTaskManager updateTaskManager;
    
    @Path("/contentDefinition/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AjaxResult contentDefinitionUpdate(ContentDefinitionUpdate update) {
        LOG.info("Running update:");
        LOG.info(update);
        try {
            githubService.saveFile(update.getCommitMessage(), Configuration.CONTENT_DEFINITION_PATH, update.getContent());
            return new AjaxResult();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return new AjaxResult(false, e.getMessage());
        }
    }
    
    @Path("/updateSettings/updateAction")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AjaxResult contentDefinitionRunUpdate() {
        LOG.info("Running update schedule");
        try {
            
            if (!githubService.isAuthenticate()) {
                return new AjaxResult(false, "You are not authenticated.");
            }
            
            if (!githubService.isAdmin()) {
                return new AjaxResult(false, "You are not authorized.");
            }
            
            updateEJB.scheduleUpdateTask();
            
            return new AjaxResult();
            
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return new AjaxResult(false, e.getMessage());
        }
    }
    
    @Path("/updateSettings/restoreVersion")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AjaxResult contentDefinitionRestoreVersion(@QueryParam("version") long version) {
        try {
            
            if (!githubService.isAuthenticate()) {
                return new AjaxResult(false, "You are not authenticated.");
            }
            
            if (!githubService.isAdmin()) {
                return new AjaxResult(false, "You are not authorized.");
            }
            
            updateTaskManager.setCurrentVersion(version);
            
            return new AjaxResult();
            
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return new AjaxResult(false, e.getMessage());
        }
    }
    
    @Path("/usersSettings/addAdmin")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AjaxResult usersSettingsAddAdmin(@QueryParam("adminName") String adminName) {
        try {
            
            if (!githubService.isAuthenticate()) {
                return new AjaxResult(false, "You are not authenticated.");
            }
            
            if (!githubService.isAdmin()) {
                return new AjaxResult(false, "You are not authorized.");
            }
            
            adminManager.addAdmin(adminName);
            
            return new AjaxResult();
            
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return new AjaxResult(false, e.getMessage());
        }
    }
    
    @Path("/usersSettings/removeAdmin")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AjaxResult usersSettingsRemoveAdmin(@QueryParam("adminName") String adminName) {
        try {
            
            if (!githubService.isAuthenticate()) {
                return new AjaxResult(false, "You are not authenticated.");
            }
            
            if (!githubService.isAdmin()) {
                return new AjaxResult(false, "You are not authorized.");
            }
            
            adminManager.removeAdmin(adminName);
            
            return new AjaxResult();
            
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return new AjaxResult(false, e.getMessage());
        }
    }
    
}

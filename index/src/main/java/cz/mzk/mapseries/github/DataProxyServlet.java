package cz.mzk.mapseries.github;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.logging.Logger;

/**
 * @author Erich Duda <dudaerich@gmail.com>
 */
@WebServlet("/server/data/*")
public class DataProxyServlet extends HttpServlet {
    
    private final static Logger LOG = Logger.getLogger(DataProxyServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String filePath = req.getPathInfo();
            File repoPath = new File("/MichaelTran262/mapseries-data/master");

            URI githubUri = new URIBuilder()
                    .setScheme("https")
                    .setHost("raw.githubusercontent.com")
                    .setPath(new File(repoPath, filePath).getAbsolutePath())
                    .build();

            if (filePath.endsWith(".js")) {
                resp.setContentType("application/javascript");
            } else if (filePath.endsWith(".json")) {
                resp.setContentType("application/json");
            }
            
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(githubUri);
            CloseableHttpResponse response = httpClient.execute(httpGet);
            response.getEntity().writeTo(resp.getOutputStream());

            response.close();
            httpClient.close();

        } catch (URISyntaxException e) {
            LOG.error("Failed to generate URI.", e);
        }
    }
    
}

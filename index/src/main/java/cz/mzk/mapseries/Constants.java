package cz.mzk.mapseries;

/**
 *
 * @author Erich Duda <dudaerich@gmail.com>
 */
public class Constants {
    
    public static final String REPO_USER = "MichaelTran262";
    public static final String REPO_NAME = "mapseries-data";
    
    public static final String GITHUB_CLIENT_ID = getProperty("GITHUB_CLIENT_ID");
    public static final String GITHUB_CLIENT_SECRET = getProperty("GITHUB_CLIENT_SECRET");
    
    public static final String THUMBNAIL_COPYRIGHTED = "copyrighted";
    public static final String THUMBNAIL_UNAVAILABLE = "unavailable";
    
    public static String getProperty(String key)  {
        String property = System.getProperty(key);
        if (property != null) {
            return property;
        }
        return System.getenv(key);
    }
    
}

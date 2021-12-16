package org.sobotics.userstalker;


import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UserStalker
{
   private static final String LOGIN_PROPERTIES_FILE = "./properties/login.properties";
   private static final String ROOMS_PROPERTIES_FILE = "./properties/rooms.properties";
   private static final String SITES_PROPERTIES_FILE = "./properties/sites.properties";

   private static final Logger LOGGER = LoggerFactory.getLogger(UserStalker.class);

   public static String VERSION;


   public static void main(String[] args) throws IOException
   {
      try
      {
         // Attempt to load version number from properties.
         try (InputStream is = UserStalker.class.getClassLoader().getResourceAsStream("project.properties"))
         {
            Properties properties = new Properties();
            properties.load(is);

            VERSION = properties.getProperty("version");
         }
         catch (IOException ex)
         {
            VERSION = "<unknown>";
         }

         // Determine if should run in testing mode. If so, append a note to the version number.
         boolean testMode = ((args != null) && (args.length > 0) && (args[0].equals("test")));
         if (testMode)
         {
            VERSION += " [TEST]";
         }

         LOGGER.info("Starting User Stalker v" + VERSION + "...");

         // Load "login" properties file.
         Properties propLogin = new Properties();
         try (FileInputStream fis = new FileInputStream(LOGIN_PROPERTIES_FILE))
         {
            propLogin.load(fis);
         }
         catch (IOException ex)
         {
            LOGGER.error("Failed to open \"login\" property file: \"" + LOGIN_PROPERTIES_FILE + "\".");
            throw ex;
         }

         // Get chat room IDs.
         int seRoomID = -1;
         int soRoomID = -1;
         try (FileInputStream fis = new FileInputStream(ROOMS_PROPERTIES_FILE))
         {
            // Attempt to load "rooms" properties file.
            Properties properties = new Properties();
            try
            {
               properties.load(fis);
            }
            catch (IOException ex)
            {
               LOGGER.warn("Failed to open \"rooms\" property file: \"" + ROOMS_PROPERTIES_FILE + "\".");
               throw ex;
            }

            // Attempt to load and parse the chat room ID numbers from the properties file.
            String seRoomProperty = "se" + (testMode ? "_test" : "");
            try
            {
               seRoomID = Integer.parseInt(properties.getProperty(seRoomProperty));
            }
            catch (NumberFormatException ex)
            {
               seRoomID = -1;

               LOGGER.warn("Missing or invalid value for \"" + seRoomProperty + "\" property in \"rooms\" property file;"
                         + " will not report in that room.");
            }

            String soRoomProperty = "so" + (testMode ? "_test" : "");
            try
            {
               soRoomID = Integer.parseInt(properties.getProperty(soRoomProperty));
            }
            catch (NumberFormatException ex)
            {
               soRoomID = -1;

               LOGGER.warn("Missing or invalid value for \"" + soRoomProperty + "\" property in \"rooms\" property file;"
                         + " will not report in that room.");
            }
         }
         catch (IOException ex)
         {
            LOGGER.error("Failed to open or parse \"rooms\" property file: \"" + ROOMS_PROPERTIES_FILE + "\".");
            throw ex;
         }

         // Get site information.
         TreeSet<String> fastSites       = null;
         TreeSet<String> slowSites       = null;
         TreeSet<String> nonEnglishSites = null;
         try (FileInputStream fis = new FileInputStream(SITES_PROPERTIES_FILE))
         {
            // Attempt to load "sites" properties file.
            Properties properties = new Properties();
            try
            {
               properties.load(fis);
            }
            catch (IOException ex)
            {
               LOGGER.warn("Failed to open \"sites\" property file: \"" + SITES_PROPERTIES_FILE + "\".");
               throw ex;
            }

            // Attempt to load the list of "fast" sites from the properties file.
            String fast = properties.getProperty("sitesFast");
            if ((fast != null) && !fast.isBlank())
            {
               fastSites = new TreeSet<String>(Arrays.asList(fast.split("\\s*,\\s*")));
            }
            else
            {
               LOGGER.warn("Missing or invalid value for \"sitesFast\" property in \"sites\" property file;"
                         + " will not monitor any \"fast\" sites.");
            }

            // Attempt to load the list of "slow" sites from the properties file.
            String slow = properties.getProperty("sitesSlow");
            if ((slow != null) && !slow.isBlank())
            {
               slowSites = new TreeSet<String>(Arrays.asList(slow.split("\\s*,\\s*")));
            }
            else
            {
               LOGGER.warn("Missing or invalid value for \"sitesSlow\" property in \"sites\" property file;"
                         + " will not monitor any \"slow\" sites.");
            }

            // Attempt to load the list of non-English sites from the properties file.
            String nonEnglish = properties.getProperty("nonEnglish");
            if ((nonEnglish != null) && !nonEnglish.isBlank())
            {
               nonEnglishSites = new TreeSet<String>(Arrays.asList(nonEnglish.split("\\s*,\\s*")));
            }
            else
            {
               LOGGER.warn("Missing or invalid value for \"nonEnglish\" property in \"sites\" property file;"
                         + " will not exclude any sites from non-Latin character checks.");
            }
         }
         catch (IOException ex)
         {
            LOGGER.error("Failed to open \"sites\" property file: \"" + SITES_PROPERTIES_FILE + "\".");
            throw ex;
         }

         // Create an instance of the ChatBot class, which will automatically try to log in to
         // Stack Exchange with the specified credentials.
         try (ChatBot bot = new ChatBot(propLogin.getProperty("email"),
                                        propLogin.getProperty("password")))
         {
            // Join the appropriate rooms.
            if (soRoomID != -1)
            {
               bot.JoinSO(soRoomID);
            }
            if (seRoomID != -1)
            {
               bot.JoinSE(seRoomID);
            }

            // Start stalking.
            bot.Run(fastSites, slowSites, nonEnglishSites);
         }
      }
      catch (Exception ex)
      {
         LOGGER.error("Failed to initialize User Stalker.");
         ex.printStackTrace();
      }
   }
}

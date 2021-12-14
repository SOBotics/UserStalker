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
   private static final String SO_PROPERTIES_FILE    = "./properties/StackOverflow.properties";
   private static final String SE_PROPERTIES_FILE    = "./properties/StackExchange.properties";

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

         // Load "StackOverflow" properties file.
         int soRoomID = -1;
         try (FileInputStream fis = new FileInputStream(SO_PROPERTIES_FILE))
         {
            // Attempt to load Stack Overflow properties file.
            Properties propSO = new Properties();
            try
            {
               propSO.load(fis);
            }
            catch (IOException ex)
            {
               LOGGER.warn("Failed to open \"StackOverflow\" property file: \"" + SO_PROPERTIES_FILE + "\".");
               throw ex;
            }

            // Attempt to load and parse the room number from the properties file.
            soRoomID = Integer.parseInt(propSO.getProperty("roomID" + (testMode ? "_test" : "")));
         }
         catch (Exception ex)
         {
            soRoomID = -1;

            LOGGER.warn("Something went wrong trying to set up stalking for Stack Overflow; will not stalk new users on that site.");
            ex.printStackTrace();
         }

         // Load "StackExchange" properties file.
         int             seRoomID        = -1;
         TreeSet<String> seSites         = null;
         TreeSet<String> nonEnglishSites = null;
         try (FileInputStream fis = new FileInputStream(SE_PROPERTIES_FILE))
         {
            // Attempt to load Stack Exchange properties file.
            Properties propSE = new Properties();
            try
            {
               propSE.load(fis);
            }
            catch (IOException ex)
            {
               LOGGER.warn("Failed to open \"StackExchange\" property file: \"" + SE_PROPERTIES_FILE + "\".");
               throw ex;
            }

            // Attempt to load the list of sites from the properties file.
            String sites = propSE.getProperty("sites");
            if ((sites != null) && !sites.isBlank())
            {
               seSites = new TreeSet<String>(Arrays.asList(sites.split("\\s*,\\s*")));
            }
            else
            {
               LOGGER.warn("Missing or invalid value for \"sites\" property in \"StackExchange\" property file;"
                         + " will not monitor any sites.");
            }

            // Attempt to load the list of non-English sites from the properties file.
            String nonEnglish = propSE.getProperty("nonEnglish");
            if ((nonEnglish != null) && !nonEnglish.isBlank())
            {
               nonEnglishSites = new TreeSet<String>(Arrays.asList(nonEnglish.split("\\s*,\\s*")));
            }
            else
            {
               LOGGER.warn("Missing or invalid value for \"nonEnglish\" property in \"StackExchange\" property file;"
                         + " will not exclude any sites from non-Latin character checks.");
            }

            // Attempt to load and parse the room number from the properties file.
            seRoomID = Integer.parseInt(propSE.getProperty("roomID" + (testMode ? "_test" : "")));
         }
         catch (Exception ex)
         {
            seRoomID = -1;

            LOGGER.warn("Something went wrong trying to set up stalking for Stack Exchange;"
                      + " will not stalk new users on these sites.");
            ex.printStackTrace();
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
            bot.Run(seSites, nonEnglishSites);
         }
      }
      catch (Exception ex)
      {
         LOGGER.error("Failed to initialize User Stalker.");
         ex.printStackTrace();
      }
   }
}

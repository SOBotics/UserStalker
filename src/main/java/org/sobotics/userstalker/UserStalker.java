package org.sobotics.userstalker;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sobotics.userstalker.ChatBot;


public class UserStalker
{
   public static String VERSION;
      static
      {
         // Attempt to load version number from properties.
         try
         {
            Properties properties = new Properties();
            properties.load(UserStalker.class.getClassLoader().getResourceAsStream("project.properties"));
            VERSION = properties.getProperty("version");
         }
         catch (IOException ex)
         {
            VERSION = "<unknown>";
         }
      }

   private static final String LOGIN_PROPERTIES_FILE = "./properties/login.properties";
   private static final String SO_PROPERTIES_FILE    = "./properties/StackOverflow.properties";
   private static final String SE_PROPERTIES_FILE    = "./properties/StackExchange.properties";

   private static final Logger LOGGER = LoggerFactory.getLogger(UserStalker.class);


   public static void main(String[] args) throws IOException
   {
      try
      {
         LOGGER.info("Starting User Stalker v" + VERSION + "...");

         // Load "login" properties file.
         Properties propLogin = new Properties();
         try
         {
            propLogin.load(new FileInputStream(LOGIN_PROPERTIES_FILE));
         }
         catch (IOException ex)
         {
            LOGGER.error("Failed to open \"login\" property file: \"" + LOGIN_PROPERTIES_FILE + "\".");
            throw ex;
         }

         // Load "StackOverflow" properties file.
         boolean joinSO   = true;
         int     soRoomID = 239107;
         try
         {
            // Attempt to load Stack Overflow properties file.
            Properties propSO = new Properties();
            try
            {
               propSO.load(new FileInputStream(SO_PROPERTIES_FILE));
            }
            catch (IOException ex)
            {
               LOGGER.warn("Failed to open \"StackOverflow\" property file: \"" + SO_PROPERTIES_FILE + "\".");
               throw ex;
            }

            // Attempt to load the room number from the properties file.
            // If this fails or it is not available, fall back to the default room number.
            try
            {
               soRoomID = Integer.parseInt(propSO.getProperty("roomID"));
            }
            catch (NumberFormatException ex)
            {
               LOGGER.warn("Missing or invalid value for \"roomID\" property in \"StackOverflow\" property file; will use default room " + soRoomID + ".");
            }
         }
         catch (Exception ex)
         {
            joinSO = false;

            LOGGER.warn("Something went wrong trying to set up stalking for Stack Overflow; will not stalk new users on that site.");
            ex.printStackTrace();
         }

         // Load "StackExchange" properties file.
         boolean      joinSE          = true;
         int          seRoomID        = 59667;
         List<String> seSites         = null;
         List<String> nonEnglishSites = null;
         try
         {
            // Attempt to load Stack Exchange properties file.
            Properties propSE = new Properties();
            try
            {
               propSE.load(new FileInputStream(SE_PROPERTIES_FILE));
            }
            catch (IOException ex)
            {
               LOGGER.warn("Failed to open \"StackExchange\" property file: \"" + SE_PROPERTIES_FILE + "\".");
               throw ex;
            }

            // Attempt to load the room number from the properties file.
            // If this fails or it is not available, fall back to the default room number.
            try
            {
               seRoomID = Integer.parseInt(propSE.getProperty("roomID"));
            }
            catch (NumberFormatException ex)
            {
               LOGGER.warn("Missing or invalid value for \"roomID\" property in \"StackExchange\" property file; will use default room " + seRoomID + ".");
            }

            // Attempt to load the list of sites from the properties file.
            try
            {
               String sites = propSE.getProperty("sites");
               if ((sites != null) && !sites.isBlank())
               {
                  seSites = Arrays.asList(sites.split("\\s*,\\s*"));
               }
            }
            catch (Exception ex)
            {
               LOGGER.warn("Missing or invalid value for \"sites\" property in \"StackExchange\" property file; will not monitor any sites.");
            }

            // Attempt to load the list of non-English sites from the properties file.
            try
            {
               String sites = propSE.getProperty("nonEnglish");
               if ((sites != null) && !sites.isBlank())
               {
                  nonEnglishSites = Arrays.asList(sites.split("\\s*,\\s*"));
               }
            }
            catch (Exception ex)
            {
               LOGGER.warn("Missing or invalid value for \"nonEnglish\" property in \"StackExchange\" property file; will not exclude any sites from non-Latin character checks.");
            }
         }
         catch (Exception ex)
         {
            joinSE = false;

            LOGGER.warn("Something went wrong trying to set up stalking for Stack Exchange; will not stalk new users on these sites.");
            ex.printStackTrace();
         }

         // Create an instance of the ChatBot class, which will automatically try to log in to
         // Stack Exchange with the specified credentials.
         ChatBot bot = new ChatBot(propLogin.getProperty("email"),
                                   propLogin.getProperty("password"));

         // Join the appropriate rooms.
         if (joinSO)
         {
            bot.JoinSO(soRoomID);
         }
         if (joinSE)
         {
            bot.JoinSE(seRoomID, seSites, nonEnglishSites);
         }

         // Start stalking.
         bot.Run();
      }
      catch (Exception ex)
      {
         LOGGER.error("Failed to initialize User Stalker.");
         ex.printStackTrace();
      }
   }
}

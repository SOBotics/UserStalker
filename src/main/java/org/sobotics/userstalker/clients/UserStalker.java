package org.sobotics.userstalker.clients;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sobotics.chatexchange.chat.ChatHost;
import org.sobotics.chatexchange.chat.Room;
import org.sobotics.chatexchange.chat.StackExchangeClient;

import org.sobotics.userstalker.services.BotService;


public class UserStalker {

    public  static final String BOT_URL               = "https://git.io/v5CGT";
    public  static final String CHAT_MSG_PREFIX       = "[ [User Stalker](" + UserStalker.BOT_URL + ") ]";
    private static final String LOGIN_PROPERTIES_FILE = "./properties/login.properties";
    private static final String SO_PROPERTIES_FILE    = "./properties/StackOverflow.properties";
    private static final String SE_PROPERTIES_FILE    = "./properties/StackExchange.properties";

    private static final Logger LOGGER = LoggerFactory.getLogger(UserStalker.class);


    public static void main(String[] args) throws IOException {
        // Load "login" properties file.
        Properties propLogin = new Properties();
        try {
            propLogin.load(new FileInputStream(LOGIN_PROPERTIES_FILE));
        } catch (IOException ex) {
            LOGGER.error("ERROR: Failed to open \"login\" property file (" + LOGIN_PROPERTIES_FILE + ").");
            ex.printStackTrace();
            return;
        }

        // Log in to Stack Exchange with the specified credentials.
        StackExchangeClient client;
        try {
            client = new StackExchangeClient(propLogin.getProperty("email"),
                                             propLogin.getProperty("password"));
        } catch (Exception ex) {
            LOGGER.error("ERROR: Failed to initialize Stack Exchange chat client.");
            ex.printStackTrace();
            return;
        }

        // Attempt to load Stack Overflow properties file and initialize stalking for SO.
        try {
            Properties propSO = new Properties();
            try {
                propSO.load(new FileInputStream(SO_PROPERTIES_FILE));
            } catch (IOException ex) {
                LOGGER.warn("WARNING: Failed to open \"StackOverflow\" property file (" + SO_PROPERTIES_FILE + "); will not start for SO.");
                throw ex;
            }

            int roomID = 239107;
            try {
                roomID = Integer.parseInt(propSO.getProperty("roomID"));
            } catch (NumberFormatException ex) {
                LOGGER.warn("WARNING: Invalid value for \"roomID\" property in \"StackOverflow\" property file; will use default room " + roomID + ".");
            }

            Room       room       = client.joinRoom(ChatHost.STACK_OVERFLOW, roomID);
            BotService botService = new BotService(Collections.singletonList("stackoverflow"));
            botService.stalk(room);
        } catch (Exception ex) {
            LOGGER.warn("WARNING: Failed to start stalker service for Stack Overflow.");
            ex.printStackTrace();
        }

        // Attempt to load Stack Exchange properties file and initialize stalking for SE.
        try {
            Properties propSE = new Properties();
            try {
                propSE.load(new FileInputStream(SE_PROPERTIES_FILE));
            } catch (IOException ex) {
                LOGGER.warn("WARNING: Failed to open \"StackExchange\" property file (" + SE_PROPERTIES_FILE + "); will not start for SE.");
                throw ex;
            }

            int roomID = 59667;
            try {
                roomID = Integer.parseInt(propSE.getProperty("roomID"));
            } catch (NumberFormatException ex) {
                LOGGER.warn("WARNING: Invalid value for \"roomID\" property in \"StackExchange\" property file; will use default room " + roomID + ".");
            }

            List<String> sites;
            try {
                String str = propSE.getProperty("sites");
                sites      = new ArrayList<String>(!str.isBlank()
                                                     ? Arrays.asList(str.split("\\s*,\\s*"))
                                                     : Collections.emptyList());
            } catch (Exception ex) {
                LOGGER.error("ERROR: Failed to parse \"sites\" property in \"StackExchange\" property file.");
                throw ex;
            }

            Room       room       = client.joinRoom(ChatHost.STACK_EXCHANGE, roomID);
            BotService botService = new BotService(sites);
            botService.stalk(room);
        } catch (Exception ex) {
            LOGGER.warn("WARNING: Failed to start stalker service for Stack Exchange.");
            ex.printStackTrace();
        }
    }

}

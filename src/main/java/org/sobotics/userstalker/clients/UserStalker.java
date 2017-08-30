package org.sobotics.userstalker.clients;

import fr.tunaki.stackoverflow.chat.ChatHost;
import fr.tunaki.stackoverflow.chat.Room;
import fr.tunaki.stackoverflow.chat.StackExchangeClient;
import org.sobotics.userstalker.services.BotService;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class UserStalker {

    public static void main(String[] args) throws IOException {
        StackExchangeClient client;
        Properties prop = new Properties();

        try {
            prop.load(new FileInputStream("./properties/login.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        client = new StackExchangeClient(prop.getProperty("email"), prop.getProperty("password"));
        Room room = client.joinRoom(ChatHost.STACK_EXCHANGE, 59667);
        new BotService().stalk(room);
    }

}
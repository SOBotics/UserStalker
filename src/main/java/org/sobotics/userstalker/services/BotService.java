package org.sobotics.userstalker.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sobotics.chatexchange.chat.Room;
import org.sobotics.chatexchange.chat.event.EventType;
import org.sobotics.chatexchange.chat.event.PingMessageEvent;
import org.sobotics.chatexchange.chat.event.UserEnteredEvent;

public class BotService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotService.class);
    private List<String> fastSites;
    private List<String> slowSites;
    private StalkerService stalker;
    private ScheduledExecutorService executorService;
    private boolean addListeners;

    public BotService() {
        fastSites = new ArrayList<>();
        slowSites = new ArrayList<>();

        // Tech Sites

        slowSites.add("stackoverflow");
        slowSites.add("superuser");
        slowSites.add("askubuntu");
        slowSites.add("drupal");
        slowSites.add("ru.stackoverflow");
        slowSites.add("arduino");

        // other sites

        slowSites.add("puzzling");
        slowSites.add("travel");

        // english sites

        slowSites.add("literature");
        slowSites.add("english");
        slowSites.add("ell");

        // religion sites

        slowSites.add("christianity");
        slowSites.add("judaism");
        slowSites.add("hinduism");
        slowSites.add("islam");

        executorService = Executors.newSingleThreadScheduledExecutor();
        addListeners = true;
    }

    private BotService(List<String> fastSites, List<String> slowSites) {
        this.fastSites = fastSites;
        this.slowSites = slowSites;
        executorService = Executors.newSingleThreadScheduledExecutor();
        addListeners = false;
    }

    public void stalk(Room room) {

        LOGGER.info("Initializing");
        LOGGER.info("Gathering smokey data");
        String smokey_data = "https://raw.githubusercontent.com/Charcoal-SE/SmokeDetector/master/blacklisted_usernames.txt";

        LOGGER.info("Gathering HD data");
        String heat_data_1 = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/a0582982f61644ec2d9e29ead440f0bbfd32d219/SOCVDBService/ini/regex_high_score.txt";
        String heat_data_2 = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/a0582982f61644ec2d9e29ead440f0bbfd32d219/SOCVDBService/ini/regex_medium_score.txt";
        String heat_data_3 = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/a0582982f61644ec2d9e29ead440f0bbfd32d219/SOCVDBService/ini/regex_low_score.txt";

        LOGGER.info("Gathering Stalker data");
        String stalker_data = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/data/blacklistRegex.txt";

        LOGGER.info("Started");
        room.send("[User Stalker](https://git.io/v5CGT) started");

        List<String> bur = getData(smokey_data, new ArrayList<>(), false);
        List<String> blr = getData(stalker_data, new ArrayList<>(), false);

        List<String> ofr = new ArrayList<>();

        ofr = getData(heat_data_1, ofr, true);
        // ofr = getData(heat_data_2, ofr, true);
        // ofr = getData(heat_data_3, ofr, true);

        List<String> finalOfr = ofr;

        stalker = new StalkerService(bur, blr, finalOfr);

        Runnable stalker1 = () -> stalker.stalkOnce(room, fastSites);
        Runnable stalker2 = () -> stalker.stalkOnce(room, slowSites);

        if (addListeners) {
            room.addEventListener(EventType.USER_ENTERED, event -> userEntered(room, event));
            room.addEventListener(EventType.MESSAGE_REPLY, event -> mention(room, event, true));
            room.addEventListener(EventType.USER_MENTIONED, event -> mention(room, event, false));
        }

        // executorService.scheduleAtFixedRate(stalker1, 0, 2, TimeUnit.MINUTES);
        executorService.scheduleAtFixedRate(stalker2, 0, 5, TimeUnit.MINUTES);

    }

    private void mention(Room room, PingMessageEvent event, boolean isReply) {
        String message = event.getMessage().getPlainContent();
        LOGGER.info("New mention: " + message);
        LOGGER.debug("Content: [" + event.getMessage().getContent() + "]");
        String[] parts = message.toLowerCase().split(" ");

        if (message.toLowerCase().contains("alive")) {
            room.send("Yep");
        }
        if (message.toLowerCase().contains("tracked")) {
            String returnString = "";
            for (String site: fastSites) {
                returnString += "    " + site + " (every 2 minutes)\n";
            }
            for (String site : slowSites) {
                returnString += "    " + site + " (every 5 minutes)\n";
            }
            room.send(returnString);
        }
        if (message.toLowerCase().contains("quota")) {
            room.send("The remaining quota is " + stalker.getQuota());
        }
        if (message.toLowerCase().contains("reboot")) {
            reboot(room);
        }
        if (!isReply && parts[1].equals("check") && parts.length == 3) {
            String[] url_parts = parts[2].split("/");
            if (!(url_parts[3].equals("users") || url_parts[2].equals("u"))) {
                room.send("Wrong url pattern provided");
            } else {
                room.send(stalker.checkUser(Integer.parseInt(url_parts[4]), url_parts[2]));
            }

        }
        if (!isReply && parts[1].equals("add") && parts.length == 4) {
            String sitename = parts[2];
            String speed = parts[3];
            switch (speed) {
                case "fast":
                    fastSites.add(sitename);
                    break;
                case "slow":
                    slowSites.add(sitename);
                    break;
            }
            room.send("Temporarily adding " + sitename + " to the list of " + speed + " sites. (cc @BhargavRao)");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            stop(room);
            new BotService(fastSites, slowSites).stalk(room);
        }
    }

    private void stop(Room room) {
        executorService.shutdown();
        room.send("stopping...");
        LOGGER.info("Stopping the bot");
        stalker = null;
        // room.leave();
    }

    private void reboot(Room room) {
        stop(room);
        LOGGER.info("Rebooting");
        stalk(room);
    }

    private void userEntered(Room room, UserEnteredEvent event) {
        LOGGER.info("Welcome " + event.getUserName() + " to room " + room.getRoomId());
    }

    private List<String> getData(String url, List<String> string_list, boolean header) {
        try {
            URL data = new URL(url);
            BufferedReader in = new BufferedReader(new InputStreamReader(data.openStream()));
            String word;
            if (header) {
                word = in.readLine();
                LOGGER.debug("Ignoring the header: " + word);
            }
            while ((word = in.readLine()) != null) {
                if (!word.contains("#")) {
                    string_list.add(word.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return string_list;
    }
}

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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sobotics.chatexchange.chat.Message;
import org.sobotics.chatexchange.chat.Room;
import org.sobotics.chatexchange.chat.event.EventType;
import org.sobotics.chatexchange.chat.event.PingMessageEvent;
import org.sobotics.chatexchange.chat.event.UserEnteredEvent;

import org.sobotics.userstalker.clients.UserStalker;


public class BotService {

   //CSOFF: Indentation
    private static final int    FAST_TIME_MINUTES        = 2;
    private static final int    SLOW_TIME_MINUTES        = 5;
    private static final String OFFENSIVE_REGEX_HI_URL   = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_high_score.txt";
    private static final String OFFENSIVE_REGEX_MD_URL   = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_medium_score.txt";
  //private static final String OFFENSIVE_REGEX_LO_URL   = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_low_score.txt";
    private static final String SMOKEY_NAME_REGEX_URL    = "https://raw.githubusercontent.com/Charcoal-SE/SmokeDetector/master/blacklisted_usernames.txt";
    private static final String INTERNAL_NAME_REGEX_URL  = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/DisplayNameBlacklist.txt";
    private static final String INTERNAL_ABOUT_REGEX_URL = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/AboutMeBlacklist.txt";
    private static final String INTERNAL_URL_REGEX_URL   = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/UrlBlacklist.txt";
    private static final String INTERNAL_PHONE_REGEX_URL = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/PhoneNumber.txt";
    private static final String INTERNAL_EMAIL_REGEX_URL = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/EmailAddress.txt";
    private static final String HELP_MSG                 =
  "I'm User Stalker (" + UserStalker.BOT_URL + "), a bot that periodically queries "
+ "the Stack Exchange /users API (https://api.stackexchange.com/docs/users) "
+ "to track all newly-created user accounts. If a suspicious pattern is detected "
+ "in a newly-created user account, the bot will post a message in this room so that "
+ "the account can be manually reviewed by a moderator. If you confirm that the user "
+ "account merits any further action, such as removal, you can do so."
+ "\n"
+ "In addition to \"help\", I recognize some additional commands:\n"
+ "  \u25CF \"alive\": Replies in the affirmative if the bot is up and running.\n"
+ "  \u25CF \"reboot\": Stops the tracking service, and then recreates and restarts it with the same settings. Any changes to blacklist pattern files will also be picked up at this time.\n"
+ "  \u25CF \"restart\": Same as \"reboot\".\n"
+ "  \u25CF \"stop\": Stops the tracking service, and causes the bot to leave the room.\n"
+ "  \u25CF \"quota\": Replies with the currently remaining size of the API quota for the tracking service.\n"
+ "  \u25CF \"track*\": Replies with the list of Stack Exchange sites that are currently being tracking.\n"
+ "  \u25CF \"check <user URL>\": Runs the pattern-detection checks on the specified user account and replies with the results.\n"
+ "  \u25CF \"test <user URL>\": Same as \"check\".\n"
+ "  \u25CF \"add <sitename> <fast/slow>\": Temporarily adds the specified SE site (short name) to the specified tracking list. (This is temporary in the sense that it will not persist across an unexpected server reboot.)\n"
+ "  \u25CF \"remove <sitename> <fast/slow>\": Temporarily removes the specified SE site (short name) from the specified tracking list. (This is temporary in the sense that it will not persist across an unexpected server reboot.)\n"
+ "If you're still confused or need more help, you can ping Cody Gray (but he may not be as nice as me!)."
;
    //CSON: Indentation

    private static final Logger LOGGER = LoggerFactory.getLogger(BotService.class);

    private List<String>             fastSites;
    private List<String>             slowSites;
    private List<Pattern>            regexOffensiveHi;
    private List<Pattern>            regexOffensiveMd;
    private List<Pattern>            regexNameSmokeyBlacklist;
    private List<Pattern>            regexNameBlacklist;
    private List<Pattern>            regexAboutBlacklist;
    private List<Pattern>            regexUrlBlacklist;
    private List<Pattern>            regexEmailPatterns;
    private List<Pattern>            regexPhonePatterns;
    private ScheduledExecutorService executorService;
    private StalkerService           stalkerService;


    public BotService(List<String> fastSites, List<String> slowSites) {
        LOGGER.info("Initializing and loading patterns...");
        this.fastSites                = fastSites;
        this.slowSites                = slowSites;
        this.regexOffensiveHi         = compileRegexFromPatternList(loadPatternsFromUrl(OFFENSIVE_REGEX_HI_URL  ), ""  , ""  );
        this.regexOffensiveMd         = compileRegexFromPatternList(loadPatternsFromUrl(OFFENSIVE_REGEX_MD_URL  ), ""  , ""  );
        this.regexNameSmokeyBlacklist = compileRegexFromPatternList(loadPatternsFromUrl(SMOKEY_NAME_REGEX_URL   ), ".*", ".*");
        this.regexNameBlacklist       = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_NAME_REGEX_URL ), ""  , ""  );
        this.regexAboutBlacklist      = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_ABOUT_REGEX_URL), ""  , ""  );
        this.regexUrlBlacklist        = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_URL_REGEX_URL  ), ""  , ""  );
        this.regexEmailPatterns       = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_PHONE_REGEX_URL), ""  , ""  );
        this.regexPhonePatterns       = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_EMAIL_REGEX_URL), ""  , ""  );
        this.executorService          = Executors.newSingleThreadScheduledExecutor();
    }


    public void stalk(Room room) { this.stalk(room, true); }

    private void stalk(Room room, boolean addListeners) {
        LOGGER.info("Starting the bot...");

        boolean multipleSites = ((this.fastSites.size() + this.slowSites.size()) > 1);
        this.stalkerService   = new StalkerService(multipleSites,
                                                   regexOffensiveHi,
                                                   regexOffensiveMd,
                                                   regexNameSmokeyBlacklist,
                                                   regexNameBlacklist,
                                                   regexAboutBlacklist,
                                                   regexUrlBlacklist,
                                                   regexEmailPatterns,
                                                   regexPhonePatterns);

        if (addListeners) {
            room.addEventListener(EventType.USER_ENTERED  , event -> onUserEntered(room, event));
            room.addEventListener(EventType.USER_MENTIONED, event -> onMentioned  (room, event, false));
          //room.addEventListener(EventType.MESSAGE_REPLY , event -> onMentioned  (room, event, true));
        }

        if (!this.fastSites.isEmpty()) {
            this.executorService.scheduleAtFixedRate(() -> this.stalkerService.stalkOnce(room,
                                                                                         this.fastSites),
                                                     0,
                                                     FAST_TIME_MINUTES,
                                                     TimeUnit.MINUTES);
        }
        if (!this.slowSites.isEmpty()) {
            this.executorService.scheduleAtFixedRate(() -> this.stalkerService.stalkOnce(room,
                                                                                         this.slowSites),
                                                     0,
                                                     SLOW_TIME_MINUTES,
                                                     TimeUnit.MINUTES);
        }

        LOGGER.info("Started the bot...");
        room.send(UserStalker.CHAT_MSG_PREFIX + " Started...");
    }


    private void onUserEntered(Room room, UserEnteredEvent event) {
        LOGGER.info("User \"" + event.getUserName() + "\" has entered room " + room.getRoomId() + ".");
    }

    private void onMentioned(Room room, PingMessageEvent event, boolean isReply) {
        Message  message       = event.getMessage();
        long     replyID       = message.getId();
        String   messageString = message.getPlainContent();
        String[] messageParts  = messageString.trim().toLowerCase().split(" ");

        LOGGER.info("New mention: " + messageString);
        LOGGER.debug("Content: [" + message.getContent() + "]");

        if (messageParts.length == 2) {
            if (messageParts[1].equals("help")) {
                room.replyTo(replyID, HELP_MSG);
                return;
            }
            if (messageParts[1].equals("reboot") ||
                messageParts[1].equals("restart")) {
                reboot(room);
                return;
            }
            else if (messageParts[1].equals("stop")) {
                stop(room);
                return;
            }
            else if (messageParts[1].equals("alive")) {
                room.replyTo(replyID, "Yep, I'm alive!");
                return;
            }
            else if (messageParts[1].equals("quota")) {
                room.replyTo(replyID, "The remaining quota is " + stalkerService.getQuota() + ".");
                return;
            }
            else if (messageParts[1].contains("track")) {
                room.replyTo(replyID, "\nSites tracked (fast): " + String.join(", ", fastSites)
                                    + "\nSites tracked (slow): " + String.join(", ", slowSites));
                return;
            }
        }
        else if (messageParts.length == 3) {
            if (messageParts[1].equals("check") || messageParts[1].equals("test")) {
                String response = "The specified URL was not recognized as a user profile.";

                String urlParts[] = messageParts[2].split("/");
                if ((urlParts[3].equals("u") || urlParts[3].equals("users"))) {
                    response = stalkerService.checkUser(Integer.parseInt(urlParts[4]), urlParts[2]);
                }

                room.replyTo(replyID, response);
                return;
            }
        }
        else if (messageParts.length == 4) {
            String command  = messageParts[1];
            String sitename = messageParts[2];
            String speed    = messageParts[3];

            boolean isFast;
            if      (speed.equals("fast"))  { isFast = true;  }
            else if (speed.equals("slow"))  { isFast = false; }
            else {
                room.replyTo(replyID,
                             "The specified speed (\"" + speed + "\") was not recognized (must be either \"fast\" or \"slow\").");
                return;
            }

            String reply = UserStalker.CHAT_MSG_PREFIX;
            if (command.equals("add")) {
                if (isFast)  { fastSites.add(sitename); }
                else         { slowSites.add(sitename); }
                reply += " Temporarily adding `" + sitename + "` to the list of \"" + speed + "\" sites.";
            }
            else if (command.equals("remove")) {
                if (isFast)  { fastSites.remove(sitename); }
                else         { slowSites.remove(sitename); }
                reply += " Temporarily removing `" + sitename + "` from the list of \"" + speed + "\" sites.";
            }
            else {
                room.replyTo(replyID,
                             "The specified command (\"" + command + "\") was not recognized (must be either \"add\" or \"remove\".)");
                return;
            }
            room.send(reply).thenRun(() -> reboot(room));
            return;
        }

        room.replyTo(replyID,
                     "You talkin' to me? Psst\u2026ping me and say \"help\".");
    }

    private void stop(Room room, boolean leave) {
        this.executorService.shutdown();
        LOGGER.info("Stopping the bot...");
        room.send(UserStalker.CHAT_MSG_PREFIX + " Stopping...")
            .thenRun(() ->
            {
                this.stalkerService = null;
                if (leave) { room.leave(); }
            });
    }

    private void stop(Room room) { this.stop(room, true); }

    private void reboot(Room room) {
        stop(room, false);

        LOGGER.info("Rebooting the bot...");

        new BotService(this.fastSites, this.slowSites).stalk(room, false);
    }


    private static ArrayList<String> loadPatternsFromUrl(String url) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            URL            data   = new URL(url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(data.openStream()));
            String         line;
            while (((line = reader.readLine()) != null) && !line.startsWith("#")) {
                String expression = line.trim();
                if (!expression.isBlank()) {
                    list.add(expression);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    private static List<Pattern> compileRegexFromPatternList(List<String> patternList,
                                                             String       prefix,
                                                             String       suffix) {
        return patternList.stream().map(m ->
        {
            try {
                return Pattern.compile(prefix + m + suffix, Pattern.CASE_INSENSITIVE);
            }
            catch (PatternSyntaxException ex) {
                LOGGER.warn("Invalid pattern syntax in regex: " + m);
                return null;
            }
        })
        .filter(output -> output != null)
        .collect(Collectors.toList());
    }

}

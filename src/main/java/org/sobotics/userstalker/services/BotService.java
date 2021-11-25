package org.sobotics.userstalker.services;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
    private static final int     POLL_TIME_MINUTES              = 3;
    private static final String  OFFENSIVE_REGEX_HI_URL         = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_high_score.txt";
    private static final String  OFFENSIVE_REGEX_MD_URL         = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_medium_score.txt";
    private static final String  OFFENSIVE_REGEX_LO_URL         = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_low_score.txt";
    private static final String  SMOKEY_NAME_REGEX_URL          = "https://raw.githubusercontent.com/Charcoal-SE/SmokeDetector/master/blacklisted_usernames.txt";
    private static final String  INTERNAL_NAME_REGEX_URL        = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/DisplayNameBlacklist.txt";
    private static final String  INTERNAL_ABOUT_REGEX_URL       = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/AboutMeBlacklist.txt";
    private static final String  INTERNAL_URL_REGEX_URL         = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/UrlBlacklist.txt";
    private static final String  INTERNAL_PHONE_REGEX_URL       = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/PhoneNumber.txt";
    private static final String  INTERNAL_EMAIL_REGEX_URL       = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/EmailAddress.txt";
    private static final Pattern REGEX_COMMENT_PATTERN          = Pattern.compile("\\(\\?#.*\\)"                        , Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_POSITIVE_LOOKBEHIND_STAR = Pattern.compile("(\\(\\?\\<\\=.*?)(?:\\*{1,2})(.*\\))", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_POSITIVE_LOOKBEHIND_PLUS = Pattern.compile("(\\(\\?\\<\\=.*?)(?:\\+{1,2})(.*\\))", Pattern.CASE_INSENSITIVE);
    private static final String  HELP_MSG                       =
  "I'm User Stalker (" + UserStalker.BOT_URL + "), a bot that continuously queries the "
+ "Stack Exchange \"/users\" API (https://api.stackexchange.com/docs/users) in order to track "
+ "all newly-created user accounts. If a suspicious pattern is detected in one of these "
+ "newly-created user accounts, the bot will post a message in this room so that the account "
+ "can be manually reviewed by a moderator. If you confirm that the user account merits any "
+ "further action, such as removal, you can do so."
+ "\n\n"
+ "In addition to \"help\", I recognize some additional commands:"
+ "\n"
+ "\u3000\u25CF \"alive\": Replies in the affirmative if the bot is up and running."
+ "\n"
+ "\u3000\u25CF \"reboot\": Stops the tracking service, and then recreates and restarts it."
+                         " (Any changes to blacklist pattern files will also be picked up at this time. The current list of tracked sites will be preserved.)"
+ "\n"
+ "\u3000\u25CF \"restart\": Same as \"reboot\"."
+ "\n"
+ "\u3000\u25CF \"stop\": Stops the tracking service, and causes the bot to leave the room."
+ "\n"
+ "\u3000\u25CF \"quota\": Replies with the currently remaining size of the API quota for the tracking service."
+ "\n"
+ "\u3000\u25CF \"track*\": Replies with the list of Stack Exchange sites that are currently being tracking."
+ "\n"
+ "\u3000\u25CF \"check <user URL>\": Runs the pattern-detection checks on the specified user account and replies with the results."
+ "\n"
+ "\u3000\u25CF \"test <user URL>\": Same as \"check\"."
+ "\n"
+ "\u3000\u25CF \"add <sitename>\": Temporarily adds the specified SE site (short name) to the tracking list."
+                                 " (This is temporary in the sense that it will not persist across an unexpected server reboot. However, it will be preserved if the \"reboot\"/\"restart\" command is given.)"
+ "\n"
+ "\u3000\u25CF \"remove <sitename>\": Temporarily removes the specified SE site (short name) from the tracking list."
+                                    " (This is temporary in the sense that it will not persist across an unexpected server reboot. However, it will be preserved if the \"reboot\"/\"restart\" command is given.)"
+ "\n\n"
+ "If you're still confused or need more help, you can ping Cody Gray (but he may not be as nice as me!)."
;
    //CSON: Indentation

    private static final Logger LOGGER = LoggerFactory.getLogger(BotService.class);

    private List<String>             sites;
    private List<Pattern>            regexOffensiveHi;
    private List<Pattern>            regexOffensiveMd;
    private List<Pattern>            regexOffensiveLo;
    private List<Pattern>            regexNameSmokeyBlacklist;
    private List<Pattern>            regexNameBlacklist;
    private List<Pattern>            regexAboutBlacklist;
    private List<Pattern>            regexUrlBlacklist;
    private List<Pattern>            regexEmailPatterns;
    private List<Pattern>            regexPhonePatterns;
    private ScheduledExecutorService executorService;
    private StalkerService           stalkerService;


    public BotService(List<String> sites) {
        LOGGER.info("Initializing and loading patterns...");
        this.sites                    = sites;
        this.regexOffensiveHi         = compileRegexFromPatternList(loadPatternsFromUrl(OFFENSIVE_REGEX_HI_URL  ), "", "");
        this.regexOffensiveMd         = compileRegexFromPatternList(loadPatternsFromUrl(OFFENSIVE_REGEX_MD_URL  ), "", "");
        this.regexOffensiveLo         = compileRegexFromPatternList(loadPatternsFromUrl(OFFENSIVE_REGEX_LO_URL  ), "", "");
        this.regexNameSmokeyBlacklist = compileRegexFromPatternList(loadPatternsFromUrl(SMOKEY_NAME_REGEX_URL   ), "", "");
        this.regexNameBlacklist       = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_NAME_REGEX_URL ), "", "");
        this.regexAboutBlacklist      = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_ABOUT_REGEX_URL), "", "");
        this.regexUrlBlacklist        = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_URL_REGEX_URL  ), "", "");
        this.regexEmailPatterns       = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_EMAIL_REGEX_URL), "", "");
        this.regexPhonePatterns       = compileRegexFromPatternList(loadPatternsFromUrl(INTERNAL_PHONE_REGEX_URL), "", "");
        this.executorService          = Executors.newSingleThreadScheduledExecutor();
    }


    public void stalk(Room room) { this.stalk(room, true); }

    private void stalk(Room room, boolean addListeners) {
        LOGGER.info("Starting the bot...");

        boolean multipleSites = (this.sites.size() > 1);
        this.stalkerService   = new StalkerService(multipleSites,
                                                   regexOffensiveHi,
                                                   regexOffensiveMd,
                                                   regexOffensiveLo,
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

        if (!this.sites.isEmpty()) {
            this.executorService.scheduleAtFixedRate(() -> this.stalkerService.stalkOnce(room, this.sites),
                                                     0,
                                                     POLL_TIME_MINUTES,
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
                room.replyTo(replyID, "Sites tracked: " + String.join(", ", sites));
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

            String reply = UserStalker.CHAT_MSG_PREFIX;
            if (command.equals("add")) {
                sites.add(sitename);
                reply += " Temporarily adding `" + sitename + "` to the list of sites.";
            }
            else if (command.equals("remove")) {
                sites.remove(sitename);
                reply += " Temporarily removing `" + sitename + "` from the list of sites.";
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

        new BotService(this.sites).stalk(room, false);
    }


    private static ArrayList<String> loadPatternsFromUrl(String url) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            URL            data   = new URL(url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(data.openStream(),
                                                                             StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#")) {
                    String expression = line.trim();

                    // Java's regular-expression engine doesn't support embedded comment groups, so
                    // when we try to compile such a regex later, the attempt will fail. Therefore,
                    // we strip out any embedded comments here, using a regex, of all things!
                    expression = REGEX_COMMENT_PATTERN.matcher(expression).replaceAll("");

                    if (!expression.isBlank()) {
                        list.add(expression);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    private static Pattern compileRegexFromPattern(String pattern, String prefix, String suffix) {
        return Pattern.compile(prefix + pattern + suffix,
                               Pattern.CASE_INSENSITIVE);
    }

    private static List<Pattern> compileRegexFromPatternList(List<String> patternList,
                                                             String       prefix,
                                                             String       suffix) {
        return patternList.stream().map(pattern ->
        {
            try {
                return compileRegexFromPattern(pattern, prefix, suffix);
            }
            catch (PatternSyntaxException exOuter) {
                LOGGER.warn("Invalid pattern syntax in regex: " + pattern + " => trying to rewrite it.");

                try {
                    // Java's regular-expression engine doesn't support certain types of
                    // variable-length look-behinds. Patterns on SmokeDetector's username blacklist
                    // are often constructed using look-behinds in order to match the entire name
                    // for the purposes of displaying it in the "why" reason. Since we don't care
                    // about capturing the exact match, but only positive detection, we can do a
                    // rote transformation of these look-behinds, replacing the unlimited
                    // quantifiers * and + with {0,40} and {1,40}, respectively, since usernames
                    // on SO/SE have a maximum length of 40 characters. This will have the same
                    // effect, ensuring that the exact semantics are preserved, without requiring
                    // a regex engine that supports fully variable-length look-behinds.
                    // Note that these * and + quantifiers might also be possessive quantifiers
                    // (which are heavily used by Charcoal in SmokeDetector as a performance
                    // optimization). Although the Java regex engine does support possessive
                    // quantifiers, they cannot occur inside of look-behinds because they would
                    // make the length of the look-behind subject to vary, which is not supported.
                    // Thus, the "matcher" regexes allow either 1 or 2 occurrences of these
                    // quantifiers, in order to ensure that both greedy and possessive quantifiers
                    // are matched.
                    //
                    // This translation is done only after the first attempt to compile the regex
                    // fails, and if it still fails to compile after this transformation, no other
                    // attempts at recovery are made; the unsupported regex will simply be skipped.
                    //
                    // See also related discussion in Charcoal HQ, starting here:
                    // <https://chat.stackexchange.com/transcript/message/59665065#59665065>
                    pattern = REGEX_POSITIVE_LOOKBEHIND_STAR.matcher(pattern).replaceAll("$1{0,40}$2");
                    pattern = REGEX_POSITIVE_LOOKBEHIND_PLUS.matcher(pattern).replaceAll("$1{1,40}$2");

                    return compileRegexFromPattern(pattern, prefix, suffix);
                }
                catch (PatternSyntaxException exInner) {
                    LOGGER.warn("Invalid pattern syntax in regex: " + pattern + " => failed to rewrite it, so skipping.");
                }
            }

            return null;
        })
        .filter(output -> output != null)
        .collect(Collectors.toList());
    }

}

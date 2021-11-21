package org.sobotics.userstalker.services;


import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.sobotics.chatexchange.chat.Room;

import org.sobotics.userstalker.clients.UserStalker;
import org.sobotics.userstalker.entities.User;
import org.sobotics.userstalker.utils.JsonUtils;


public class StalkerService {

    private static final String USERS_API_URL = "https://api.stackexchange.com/2.2/users";
    private static final String API_KEY       = "XKgBTF5nztGvMnDoI8gPgA((";
    private static final String API_FILTER    = "!Ln3l_2int_VA.0Iu5wL3aW";

    private static final Logger LOGGER = LoggerFactory.getLogger(StalkerService.class);

    private boolean       showSite;
    private Instant       previousTime;
    private int           quota;
    private List<Pattern> regexOffensiveHi;
    private List<Pattern> regexOffensiveMd;
    private List<Pattern> regexNameSmokeyBlacklist;
    private List<Pattern> regexNameBlacklist;
    private List<Pattern> regexAboutBlacklist;
    private List<Pattern> regexUrlBlacklist;
    private List<Pattern> regexEmailPatterns;
    private List<Pattern> regexPhonePatterns;


    public StalkerService(boolean       showSite,
                          List<Pattern> regexOffensiveHi,
                          List<Pattern> regexOffensiveMd,
                          List<Pattern> regexNameSmokeyBlacklist,
                          List<Pattern> regexNameBlacklist,
                          List<Pattern> regexAboutBlacklist,
                          List<Pattern> regexUrlBlacklist,
                          List<Pattern> regexEmailPatterns,
                          List<Pattern> regexPhonePatterns) {
        this.showSite                 = showSite;
        this.previousTime             = Instant.now().minusSeconds(60);
        this.quota                    = 10000;
        this.regexOffensiveHi         = regexOffensiveHi;
        this.regexOffensiveMd         = regexOffensiveMd;
        this.regexNameSmokeyBlacklist = regexNameSmokeyBlacklist;
        this.regexNameBlacklist       = regexNameBlacklist;
        this.regexAboutBlacklist      = regexAboutBlacklist;
        this.regexUrlBlacklist        = regexUrlBlacklist;
        this.regexEmailPatterns       = regexEmailPatterns;
        this.regexPhonePatterns       = regexPhonePatterns;
    }


    public int getQuota() {
        return quota;
    }

    public void stalkOnce(Room room, List<String> sites) {
        LOGGER.info("Stalking " + sites + " at " + Instant.now() + "...");
        for (String site : sites) {
            detectBadGuys(room, site);
        }
        previousTime = Instant.now();
    }

    public String checkUser(int userId, String site) {
        JsonObject json = null;
        try {
            json = JsonUtils.get(USERS_API_URL + "/" + userId,
                    "sort"    , "creation",
                    "site"    ,  site,
                    "pagesize", "100",
                    "page"    , "1",
                    "filter"  , "!-*jbN*IioeFP",
                    "order"   , "desc",
                    "key"     , API_KEY);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        JsonUtils.handleBackoff(json);
        if (json != null) {
            quota = json.get("quota_remaining").getAsInt();
        }
        if (json.has("items")) {
            JsonArray array = json.get("items").getAsJsonArray();
            if (array.size() > 0) {
                User   user    = new User(array.get(0).getAsJsonObject());
                String reasons = getReason(user);
                String result  = " [" + user.getDisplayName() + "](" + user.getLink() + "?tab=profile)";
                if (!reasons.isBlank()) {
                    result += " would be caught for: ";
                    result += reasons;
                }
                else {
                    result += " would not be caught.";
                }
                return result;
            }
        }
        return "No such user found.";
    }


    private void detectBadGuys(Room room, String site) {
        JsonObject json = callAPI(site);
        if ((json != null) && json.has("items")) {
            LOGGER.debug("JSON returned from SE API: " + json.toString());
            for (JsonElement element: json.get("items").getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                User       user   = new User(object);
                user.setSite(site);
                LOGGER.info("New user detected: \"" + user.getDisplayName() + "\" (" + user.getLink() + ").");
                LOGGER.debug(user.toString());
                String reason = getReason(user);
                if (!reason.isBlank()) {
                    reportUser(room, user, reason);
                }
            }
        }
    }

    private String getReason(User user) {
        String            name     = user.getDisplayName();
        String            location = user.getLocation();
        String            url      = user.getWebsiteUrl();
        String            about    = user.getAboutMe();
        ArrayList<String> reasons  = new ArrayList<String>(20);

        // Check for an active suspension.
        if (user.getTimedPenaltyDate() != null) {

            reasons.add("suspended until "
                        + getDateTimeStampToNearestMinuteFromApiDateTime(user.getTimedPenaltyDate()));
        }

        // Check the display name.
        if ((name != null) && !name.isBlank()) {
            if (anyRegexMatches(name, this.regexNameBlacklist)) {
                reasons.add("username on blacklist");
            }

            if (anyRegexMatches(name, this.regexNameSmokeyBlacklist)) {
                reasons.add("username on Smokey's blacklist");
            }

            if (anyRegexMatches(name, this.regexOffensiveHi)) {
                reasons.add("username is very offensive");
            }

            if (anyRegexMatches(name, this.regexOffensiveMd)) {
                reasons.add("username is offensive");
            }

            if (name.contains(Integer.toString(Year.now().getValue()))) {
                reasons.add("username contains current year");
            }
            if (name.contains(Integer.toString(Year.now().getValue() + 1))) {
                reasons.add("username contains next year");
            }

            if ((url != null) && !url.isBlank()) {
                String normalizedName = name.replaceAll("[^a-zA-Z ]", "").toLowerCase();
                String normalizedUrl  = url .replaceAll("[^a-zA-Z ]", "").toLowerCase();
                if (name.toLowerCase().contains(normalizedUrl ) ||
                    url .toLowerCase().contains(normalizedName)) {
                    reasons.add("URL similar to username");
                }
            }
        }

        // Check the URL.
        if ((url != null) && !url.isBlank()) {
            if (anyRegexMatches(url, this.regexUrlBlacklist)) {
                reasons.add("URL on blacklist");
            }
        }

        // Check the location.
        if ((location != null) && !location.isBlank()) {
            if (anyRegexMatches(location, this.regexOffensiveHi)) {
                reasons.add("location is very offensive");
            }

            if (anyRegexMatches(location, this.regexOffensiveMd)) {
                reasons.add("location is offensive");
            }
        }

        // Check the "About Me".
        if ((about != null) && !about.isBlank()) {
            if (anyRegexMatches(about, this.regexAboutBlacklist)) {
                reasons.add("\"About Me\" contains blacklisted pattern");
            }

            if (anyRegexMatches(about, this.regexOffensiveHi)) {
                reasons.add("\"About Me\" is very offensive");
            }

            if (anyRegexMatches(about, this.regexOffensiveMd)) {
                reasons.add("\"About Me\" is offensive");
            }

            if (anyRegexMatches(about, this.regexPhonePatterns)) {
                reasons.add("\"About Me\" contains phone number");
            }

            if (anyRegexMatches(about, this.regexEmailPatterns)) {
                reasons.add("\"About Me\" contains email");
            }

            if (anyRegexMatches(about, this.regexUrlBlacklist)) {
                reasons.add("\"About Me\" contains blacklisted URL");
            }

            if (about.toLowerCase().contains("</a>")) {
                reasons.add("\"About Me\" contains a link");
            }
        }

        return String.join("; ", reasons);
    }

    private void reportUser(Room room, User user, String reason) {
        LOGGER.info("Detected user \"" + user + "\".");

        boolean isSuspended = (user.getTimedPenaltyDate() != null);

        StringBuilder builder = new StringBuilder();
        builder.append(UserStalker.CHAT_MSG_PREFIX);
        builder.append(" [");
        if (isSuspended) {
            builder.append("*");
        }
        builder.append(user.getDisplayName().trim());
        if (isSuspended) {
            builder.append("*");
        }
        builder.append("](");
        builder.append(user.getLink());
        builder.append("?tab=profile \"");
        builder.append(user.getDisplayName());
        builder.append("\") ");
        if (showSite) {
            builder.append("on **`");
            builder.append(user.getSite());
            builder.append("`** ");
        }
        builder.append("(");
        builder.append(reason);
        builder.append(")");

        room.send(builder.toString());
    }

    private JsonObject callAPI(String site) {
        JsonObject json = null;
        try {
            json = JsonUtils.get(USERS_API_URL,
                    "sort"     , "creation",
                    "site"     , site,
                    "pagesize" , "100",
                    "page"     , "1",
                    "filter"   , API_FILTER,
                    "order"    , "desc",
                    "fromdate" , String.valueOf(previousTime.minusSeconds(1).getEpochSecond()),
                    "key"      , API_KEY);
        } catch (SocketTimeoutException ex) {
            ex.printStackTrace();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex2) {
                ex2.printStackTrace();
            }
            return null;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }

        JsonUtils.handleBackoff(json);
        if (json != null) {
            quota = json.get("quota_remaining").getAsInt();
        }
        return json;
    }


    private static boolean anyRegexMatches(String string, List<Pattern> patternList) {
        return patternList.parallelStream().anyMatch(pattern -> pattern.matcher(string).matches());
    }

    private static String getDateTimeStampToNearestMinuteFromApiDateTime(long apiDateTime) {
        // SE API date-time fields are Unix epochs in UTC format.
        ZonedDateTime dateTime = Instant.ofEpochSecond(apiDateTime).atZone(ZoneOffset.UTC);

        // Round to the nearest whole minute.
        if (dateTime.getSecond() >= 30)
        {
            dateTime = dateTime.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d yyyy 'at' HH:mm");
        return dateTime.format(fmt);
    }

}

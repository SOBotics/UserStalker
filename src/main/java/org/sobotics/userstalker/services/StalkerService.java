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
import java.util.regex.PatternSyntaxException;

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

    private static final String USERS_API_URL      = "https://api.stackexchange.com/2.2/users";
    private static final String API_KEY            = "XKgBTF5nztGvMnDoI8gPgA((";
    private static final String API_FILTER         = "!Ln3l_2int_VA.0Iu5wL3aW";
    private static final String PHONE_NUMBER_REGEX = ".*\\d{10}.*|.*(?:\\d{3}-){2}\\d{4}.*|.*\\(\\d{3}\\)\\d{3}-?\\d{4}.*";  // https://stackoverflow.com/questions/42104546
    private static final Logger LOGGER             = LoggerFactory.getLogger(StalkerService.class);

    private boolean      showSite;
    private Instant      previousTime;
    private List<String> regexOffensive;
    private List<String> regexUserNameInternalBlacklist;
    private List<String> regexUserNameSmokeyBlacklist;
    private int          quota;


    public StalkerService(boolean      showSite,
                          List<String> offensiveRegex,
                          List<String> userNameRegex,
                          List<String> smokeyRegex) {
        this.showSite                       = showSite;
        this.previousTime                   = Instant.now().minusSeconds(60);
        this.regexOffensive                 = offensiveRegex;
        this.regexUserNameInternalBlacklist = userNameRegex;
        this.regexUserNameSmokeyBlacklist   = smokeyRegex;
        this.quota                          = 10000;
    }


    public int getQuota() {
        return quota;
    }

    public void stalkOnce(Room room, List<String> sites) {
        LOGGER.info("Stalking " + sites + " at " + Instant.now());
        for (String site: sites) {
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
            LOGGER.info("Input JSON: {}", json.toString());
            for (JsonElement element: json.get("items").getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                User       user   = new User(object);
                user.setSite(site);
                LOGGER.info("New user detected : {} - {}.", user.getDisplayName(), user.getLink());
                LOGGER.debug(user.toString());
                String reason = getReason(user);
                if (!reason.isBlank()) {
                    sendUser(room, user, reason);
                }
            }
        }
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

    private String getReason(User user) {
        ArrayList<String> reasons = new ArrayList<String>(20);

        if (user.getTimedPenaltyDate() != null) {

            reasons.add("suspended until "
                        + getDateTimeStampToNearestMinuteFromApiDateTime(user.getTimedPenaltyDate()));
        }

        if ((user.getDisplayName() != null) && !user.getDisplayName().isBlank()) {
            String displayNameLower = user.getDisplayName().toLowerCase();

            if ((user.getWebsiteUrl() != null)  &&
                !user.getWebsiteUrl().isBlank() &&
                user.getWebsiteUrl().toLowerCase().contains(displayNameLower.replaceAll("[^a-zA-Z ]", ""))) {
                reasons.add("URL similar to username");
            }

            if (regexUserNameInternalBlacklist.stream().anyMatch(m -> regexMatch(m, displayNameLower))) {
                reasons.add("username on blacklist");
            }
            if (regexUserNameSmokeyBlacklist.stream().anyMatch(m -> regexMatch(".*" + m + ".*", displayNameLower))) {
                reasons.add("username on Smokey's blacklist");
            }
            if (displayNameLower.contains(Integer.toString(Year.now().getValue()))) {
                reasons.add("username contains current year");
            }
            if (displayNameLower.contains(Integer.toString(Year.now().getValue() + 1))) {
                reasons.add("username contains next year");
            }
        }

        if ((user.getAboutMe() != null) && !user.getAboutMe().isBlank()) {
            String aboutMeActual = user.getAboutMe();
            String aboutMe       = aboutMeActual.toLowerCase();
            if (aboutMe.contains("insurance")) {
                reasons.add("insurance spammer");
            }
            if (regexOffensive.stream().anyMatch(m -> regexMatch(m, aboutMeActual))) {
                reasons.add("\"About Me\" is offensive");
            }
            if (regexMatch(PHONE_NUMBER_REGEX, aboutMeActual)) {
                reasons.add("\"About Me\" contains phone number");
            }
            if (aboutMe.contains("</a>")) {
                reasons.add("\"About Me\" contains link");
            }
        }

        // TODO: Fix the .* stuff by using a Pattern
        return String.join("; ", reasons);
    }

    private boolean regexMatch(String regex, String input) {
        try {
            return input.matches(regex);
        }
        catch (PatternSyntaxException ex) {
            LOGGER.debug("Invalid Pattern: {}", regex);
            return false;
        }
    }

    private void sendUser(Room room, User user, String reason) {
        LOGGER.info("Detected user "+ user);

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

}

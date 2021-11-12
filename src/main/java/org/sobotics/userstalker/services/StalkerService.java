package org.sobotics.userstalker.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sobotics.chatexchange.chat.Room;
import org.sobotics.userstalker.entities.User;
import org.sobotics.userstalker.utils.JsonUtils;
import org.sobotics.userstalker.clients.UserStalker;

import java.lang.String;
import java.lang.StringBuilder;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.PatternSyntaxException;

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
               User   user    = getUser(array.get(0).getAsJsonObject());
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
        return "No such user found";
    }


    private void detectBadGuys(Room room, String site) {
        JsonObject json = callAPI(site);
        if ((json != null) && json.has("items")) {
            LOGGER.info("Input JSON: {}", json.toString());
            for (JsonElement element: json.get("items").getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                User       user   = getUser(object);
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

    private String getReason(User user) {
        ArrayList<String> reasons = new ArrayList<String>(20);

        if (user.getTimedPenaltyDate() != null) {
            reasons.add("suspended user");
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

        StringBuilder builder = new StringBuilder();
        builder.append(UserStalker.CHAT_MSG_PREFIX);
        builder.append(" [");
        builder.append(user.getDisplayName());
        builder.append("](");
        builder.append(user.getLink());
        builder.append("?tab=profile) ");
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

    private User getUser(JsonObject object) {
        User user = new User();
        if (object.has("account_id"))          { user.setAccountId       (object.get("account_id"        ).getAsInt());     }
        if (object.has("is_employee"))         { user.setIsEmployee      (object.get("is_employee"       ).getAsBoolean()); }
        if (object.has("creation_date"))       { user.setCreationDate    (object.get("creation_date"     ).getAsInt());     }
        if (object.has("user_id"))             { user.setUserId          (object.get("user_id"           ).getAsInt());     }
        if (object.has("accountId"))           { user.setAccountId       (object.get("accountId"         ).getAsInt());     }
        if (object.has("link"))                { user.setLink            (object.get("link"              ).getAsString());  }
        if (object.has("profile_image"))       { user.setProfileImage    (object.get("profile_image"     ).getAsString());  }
        if (object.has("display_name"))        { user.setDisplayName     (object.get("display_name"      ).getAsString());  }
        if (object.has("about_me"))            { user.setAboutMe         (object.get("about_me"          ).getAsString());  }
        if (object.has("location"))            { user.setLocation        (object.get("location"          ).getAsString());  }
        if (object.has("website_url"))         { user.setWebsiteUrl      (object.get("website_url"       ).getAsString());  }
        if (object.has("timed_penalty_date"))  { user.setTimedPenaltyDate(object.get("timed_penalty_date").getAsInt());     }
        return user;
    }

}

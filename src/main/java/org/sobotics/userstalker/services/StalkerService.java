package org.sobotics.userstalker.services;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sobotics.chatexchange.chat.Room;
import org.sobotics.userstalker.entities.User;
import org.sobotics.userstalker.utils.JsonUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class StalkerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StalkerService.class);
    private Instant previousTime;
    private List<String> smokey_blacklist_regex;
    private List<String> offensive_regex;
    private List<String> blacklisted_username_regex;
    private int quota;

    private String url = "https://api.stackexchange.com/2.2/users";
    private String apiKey = "XKgBTF5nztGvMnDoI8gPgA((";
    private String filter = "!Ln3l_2int_VA.0Iu5wL3aW";

    StalkerService(List<String> bur, List<String> blr, List<String> or) {
        previousTime = Instant.now().minusSeconds(60);
        smokey_blacklist_regex = bur;
        blacklisted_username_regex = blr;
        offensive_regex = or;
        quota = 10000;
    }

    void stalkOnce(Room room, List<String> sites) {
        LOGGER.info("Stalking " + sites + " at " + Instant.now());
        for (String site: sites) {
            detectBadGuys(room, site);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        previousTime = Instant.now();
    }

    private void detectBadGuys(Room room, String site) {
        JsonObject json = callAPI(site);
        if (json != null && json.has("items")) {
            LOGGER.info("Input json: {}", json.toString());
            for (JsonElement element: json.get("items").getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                User user = getUser(object);
                user.setSite(site);
                LOGGER.info("New user detected : {} - {}.", user.getDisplayName(), user.getLink());
                LOGGER.debug(user.toString());
                String tag = "[ [User Stalker](https://git.io/v5CGT) ]";
                String reason = getReason(user);
                if (!"".equals(reason)) {
                    sendUser(room, user, tag, reason);
                }
            }
        }
    }

    public String checkUser(int userId, String site) {
        JsonObject json = null;
        try {
            json = JsonUtils.get(url + "/" + userId,
                    "sort", "creation",
                    "site", site,
                    "pagesize", "100",
                    "page", "1",
                    "filter", "!-*jbN*IioeFP",
                    "order", "desc",
                    "key", apiKey);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonUtils.handleBackoff(json);
        if (json != null) {
            quota = json.get("quota_remaining").getAsInt();
        }
        if (json.has("items")) {
            JsonArray array = json.get("items").getAsJsonArray();
            if (array.size() == 0) {
                return "No such user found";
            }
            User user = getUser(array.get(0).getAsJsonObject());
            String reasons = getReason(user);
            if ("".equals(reasons)) {
                return "The user would not be caught.";
            }
            return "The user would be caught for: " + reasons;
        }
        return "No such user found";
    }

    private String getReason(User user) {
        String reason = "";
        String phoneNumberRegex = ".*\\d{10}.*|.*(?:\\d{3}-){2}\\d{4}.*|.*\\(\\d{3}\\)\\d{3}-?\\d{4}.*"; // https://stackoverflow.com/questions/42104546
        if (user.getAboutMe() != null && !user.getAboutMe().equals("") && user.getAboutMe().contains("</a>")) {
            reason += " Contains link in About Me; ";
        }
        if (user.getWebsiteUrl() != null && !user.getWebsiteUrl().equals("") && user.getWebsiteUrl().toLowerCase().contains(user.getDisplayName().toLowerCase().replaceAll("[^a-zA-Z ]", ""))) {
            reason += " URL similar to username; ";
        }
        if (user.getAboutMe() != null && offensive_regex.stream().anyMatch(e -> regexMatch(e, user.getAboutMe()))) {
            reason += " Offensive user profile; ";
        }
        if (user.getAboutMe() != null && regexMatch(phoneNumberRegex, user.getAboutMe())) {
            reason += " Phone number in user profile; ";
        }
        if (user.getAboutMe() != null && user.getAboutMe().toLowerCase().contains("insurance")) {
            reason += " Insurance Spammer; ";
        }
        if (user.getTimedPenaltyDate() != null) {
            reason += " Suspended user; ";
        }
        if (user.getDisplayName().toLowerCase().contains(Integer.toString(Year.now().getValue()))) {
            reason += " Username contains current year; ";
        }
        if (user.getDisplayName().toLowerCase().contains(Integer.toString(Year.now().getValue() + 1))) {
            reason += " Username contains next year; ";
        }
        if (regexMatch("cum\\s*juice|donald\\s*trump|.*trump.*", user.getDisplayName().toLowerCase())) {
            reason += " Manually Blacklisted Username; ";
        }
        if (blacklisted_username_regex.stream().anyMatch(e -> regexMatch(e, user.getDisplayName().toLowerCase()))) {
            reason += " Blacklisted Username; ";
        }
        if (smokey_blacklist_regex.stream().anyMatch(e -> regexMatch(".*" + e + ".*", user.getDisplayName().toLowerCase()))) {
            reason += " Blacklisted Smokey Username; ";
        }
        // TODO: Fix the .* stuff by using a Pattern
        return reason;
    }

    private boolean regexMatch(String regex, String input) {
        try {
            return input.matches(regex);
        } catch (PatternSyntaxException e) {
            LOGGER.debug("Invalid Pattern: {}", regex);
            return false;
        }
    }

    private void sendUser(Room room, User user, String tag, String reason) {
        LOGGER.info("Detected user " + user);
        room.send(tag + reason + "[" + user.getDisplayName() + "](" + user.getLink() + "?tab=profile) on " + user.getSite());
    }

    int getQuota() {
        return quota;
    }

    private JsonObject callAPI(String site) {

        JsonObject json = null;
        try {
            json = JsonUtils.get(url,
                    "sort", "creation",
                    "site", site,
                    "pagesize", "100",
                    "page", "1",
                    "filter", filter,
                    "order", "desc",
                    "fromdate", String.valueOf(previousTime.minusSeconds(1).getEpochSecond()),
                    "key", apiKey);
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
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
        if (object.has("account_id")) {
            user.setAccountId(object.get("account_id").getAsInt());
        }
        if (object.has("is_employee")) {
            user.setIsEmployee(object.get("is_employee").getAsBoolean());
        }
        if (object.has("creation_date")) {
            user.setCreationDate(object.get("creation_date").getAsInt());
        }
        if (object.has("user_id")) {
            user.setUserId(object.get("user_id").getAsInt());
        }
        if (object.has("accountId")) {
            user.setAccountId(object.get("accountId").getAsInt());
        }
        if (object.has("link")) {
            user.setLink(object.get("link").getAsString());
        }
        if (object.has("profile_image")) {
            user.setProfileImage(object.get("profile_image").getAsString());
        }
        if (object.has("display_name")) {
            user.setDisplayName(object.get("display_name").getAsString());
        }
        if (object.has("about_me")) {
            user.setAboutMe(object.get("about_me").getAsString());
        }
        if (object.has("location")) {
            user.setLocation(object.get("location").getAsString());
        }
        if (object.has("website_url")) {
            user.setWebsiteUrl(object.get("website_url").getAsString());
        }
        if (object.has("timed_penalty_date")) {
            user.setTimedPenaltyDate(object.get("timed_penalty_date").getAsInt());
        }
        return user;
    }
}

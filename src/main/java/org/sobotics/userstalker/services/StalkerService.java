package org.sobotics.userstalker.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.tunaki.stackoverflow.chat.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sobotics.userstalker.entities.User;
import org.sobotics.userstalker.utils.JsonUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public class StalkerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StalkerService.class);
    private Instant previousTime;
    private List<String> blacklisted_usernames_regex;
    private List<String> offensive_regex;
    private int quota;

    private String url = "https://api.stackexchange.com/2.2/users";
    private String apiKey = "kmtAuIIqwIrwkXm1*p3qqA((";
    private String filter = "!Ln3l_2int_VA.0Iu5wL3aW";

    StalkerService(List<String> bur, List<String> or) {
        previousTime = Instant.now().minusSeconds(60);
        blacklisted_usernames_regex = bur;
        offensive_regex = or;
        quota = 10000;
    }

    void stalkOnce(Room room, List<String> sites) {
        LOGGER.info("Stalking "+sites+" at "+Instant.now());
        for (String site: sites) detectBadGuys(room, site);
        previousTime = Instant.now();
    }

    private void detectBadGuys(Room room, String site) {
        JsonObject json = callAPI(site);
        if (json.has("items")){
            for (JsonElement element: json.get("items").getAsJsonArray()){
                JsonObject object = element.getAsJsonObject();
                User user = getUser(object);
                user.setSite(site);
                LOGGER.info("New user detected : {} - {}.", user.getDisplayName(), user.getLink());
                String tag = "[ [User Stalker](https://git.io/v5CGT) ]";
                String reason = getReason(user);
                if(!reason.equals(""))
                    sendUser(room, user, tag, reason);
            }
        }
    }

    private String checkUser(int userId, String site){
        JsonObject json = null;
        try {
            json = JsonUtils.get(url+"/"+userId,
                    "sort","creation",
                    "site", site,
                    "pagesize","100",
                    "page","1",
                    "filter",filter,
                    "order","desc",
                    "key",apiKey);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonUtils.handleBackoff(json);
        if (json != null) {
            quota = json.get("quota_remaining").getAsInt();
        }
        User user = getUser(json);
        String reasons = getReason(user);
        if(!reasons.equals(""))
            return "The user would not be caught.";
        return "The user would be caught for: ";
    }

    private String getReason(User user) {
        String reason = "";
        if ((user.getAboutMe()!=null && !user.getAboutMe().equals("") && user.getAboutMe().contains("</a>"))) {
            reason += " Contains link in About Me; ";
        }
        if ((user.getWebsiteUrl()!=null && !user.getWebsiteUrl().equals("") && user.getWebsiteUrl().toLowerCase().contains(user.getDisplayName().toLowerCase().replaceAll("[^a-zA-Z ]", "")))) {
            reason += " URL similar to username; ";
        }
        if (user.getAboutMe()!=null && offensive_regex.stream().anyMatch(e -> user.getAboutMe().matches(e))) {
            reason += " Offensive user profile; ";
        }
        if (user.getTimedPenaltyDate()!=null) {
            reason += " Suspended user; ";
        }
        if (blacklisted_usernames_regex.stream().anyMatch(e -> user.getDisplayName().toLowerCase().matches(e))){
            reason += " Blacklisted Smokey Username; ";
        }
        return reason;
    }

    private void sendUser(Room room, User user, String tag, String reason) {
        LOGGER.info("Detected user "+user);
        room.send(tag + reason + "["+user.getDisplayName()+"]("+ user.getLink()+"?tab=profile) on "+user.getSite());
    }

    int getQuota(){
        return quota;
    }

    private JsonObject callAPI(String site) {

        JsonObject json = null;
        try {
            json = JsonUtils.get(url,
                    "sort","creation",
                    "site", site,
                    "pagesize","100",
                    "page","1",
                    "filter",filter,
                    "order","desc",
                    "fromdate",String.valueOf(previousTime.minusSeconds(1).getEpochSecond()),
                    "key",apiKey);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonUtils.handleBackoff(json);
        if (json != null) {
            quota = json.get("quota_remaining").getAsInt();
        }
        return json;
    }

    private User getUser(JsonObject object){
        User user = new User();
        if(object.has("account_id")) user.setAccountId(object.get("account_id").getAsInt());
        if(object.has("is_employee")) user.setIsEmployee(object.get("is_employee").getAsBoolean());
        if(object.has("creation_date")) user.setCreationDate(object.get("creation_date").getAsInt());
        if(object.has("user_id")) user.setUserId(object.get("user_id").getAsInt());
        if(object.has("accountId")) user.setAccountId(object.get("accountId").getAsInt());
        if(object.has("link")) user.setLink(object.get("link").getAsString());
        if(object.has("profile_image")) user.setProfileImage(object.get("profile_image").getAsString());
        if(object.has("display_name")) user.setDisplayName(object.get("display_name").getAsString());
        if(object.has("about_me")) user.setAboutMe(object.get("about_me").getAsString());
        if(object.has("location")) user.setLocation(object.get("location").getAsString());
        if(object.has("website_url")) user.setWebsiteUrl(object.get("website_url").getAsString());
        if(object.has("timed_penalty_date")) user.setTimedPenaltyDate(object.get("timed_penalty_date").getAsInt());
        return user;
    }
}

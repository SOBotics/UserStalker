package org.sobotics.userstalker.entities;


import com.google.gson.JsonObject;


public class User
{
    public User(JsonObject object) {
        if (object.has("account_id"))          { this.setAccountId       (object.get("account_id"        ).getAsLong   ()); }
        if (object.has("is_employee"))         { this.setIsEmployee      (object.get("is_employee"       ).getAsBoolean()); }
        if (object.has("creation_date"))       { this.setCreationDate    (object.get("creation_date"     ).getAsLong   ()); }
        if (object.has("user_id"))             { this.setUserId          (object.get("user_id"           ).getAsLong   ()); }
        if (object.has("accountId"))           { this.setAccountId       (object.get("accountId"         ).getAsLong   ()); }
        if (object.has("link"))                { this.setLink            (object.get("link"              ).getAsString ()); }
        if (object.has("profile_image"))       { this.setProfileImage    (object.get("profile_image"     ).getAsString ()); }
        if (object.has("display_name"))        { this.setDisplayName     (object.get("display_name"      ).getAsString ()); }
        if (object.has("about_me"))            { this.setAboutMe         (object.get("about_me"          ).getAsString ()); }
        if (object.has("location"))            { this.setLocation        (object.get("location"          ).getAsString ()); }
        if (object.has("website_url"))         { this.setWebsiteUrl      (object.get("website_url"       ).getAsString ()); }
        if (object.has("timed_penalty_date"))  { this.setTimedPenaltyDate(object.get("timed_penalty_date").getAsLong   ()); }
    }


    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Boolean getIsEmployee() {
        return isEmployee;
    }

    public void setIsEmployee(Boolean isEmployee) {
        this.isEmployee = isEmployee;
    }

    public Long getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Long creationDate) {
        this.creationDate = creationDate;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAboutMe() {
        return aboutMe;
    }

    public void setAboutMe(String aboutMe) {
        this.aboutMe = aboutMe;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public Long getTimedPenaltyDate() {
        return timedPenaltyDate;
    }

    public void setTimedPenaltyDate(Long timedPenaltyDate) {
        this.timedPenaltyDate = timedPenaltyDate;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }


    @Override
    public String toString() {
        String returnText = "User{"
                          + " link='" + link + '\''
                          + ", displayName='" + displayName + '\'';

        if (aboutMe          != null)  { returnText += ", aboutMe='" + aboutMe + '\''; }
        if (location         != null)  { returnText += ", location='" + location + '\''; }
        if (websiteUrl       != null)  { returnText += ", websiteUrl='" + websiteUrl + '\''; }
        if (timedPenaltyDate != null)  { returnText += ", timedPenaltyDate=" + timedPenaltyDate; }

        returnText += '}';
        return returnText;
    }


    private Long    accountId;
    private Boolean isEmployee;
    private Long    creationDate;
    private Long    userId;
    private String  link;
    private String  profileImage;
    private String  displayName;
    private String  aboutMe;
    private String  location;
    private String  websiteUrl;
    private Long    timedPenaltyDate;
    private String  site;

}

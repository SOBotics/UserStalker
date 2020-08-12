package org.sobotics.userstalker.entities;

public class User {
    private Integer accountId;
    private Boolean isEmployee;
    private Integer creationDate;
    private Integer userId;
    private String link;
    private String profileImage;
    private String displayName;
    private String aboutMe;
    private String location;
    private String websiteUrl;
    private Integer timedPenaltyDate;
    private String site;

    public User() {
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public Boolean getIsEmployee() {
        return isEmployee;
    }

    public void setIsEmployee(Boolean isEmployee) {
        this.isEmployee = isEmployee;
    }

    public Integer getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Integer creationDate) {
        this.creationDate = creationDate;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
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

    public Integer getTimedPenaltyDate() {
        return timedPenaltyDate;
    }

    public void setTimedPenaltyDate(Integer timedPenaltyDate) {
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

        if (aboutMe != null) {
            returnText += ", aboutMe='" + aboutMe + '\'';
        }
        if (location != null) {
            returnText += ", location='" + location + '\'';
        }
        if (websiteUrl != null) {
            returnText += ", websiteUrl='" + websiteUrl + '\'';
        }
        if (timedPenaltyDate != null) {
            returnText += ", timedPenaltyDate=" + timedPenaltyDate;
        }

        returnText += '}';
        return returnText;
    }
}

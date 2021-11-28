package org.sobotics.userstalker;


import org.jsoup.parser.Parser;

import com.google.gson.JsonObject;


public class User
{
   public User(String site, JsonObject object)
   {
      this.site = site;

      if (object.has("about_me"))            { this.setAboutMe         (                        object.get("about_me"          ).getAsString ()        ); }
      if (object.has("account_id"))          { this.setAccountId       (                        object.get("account_id"        ).getAsLong   ()        ); }
      if (object.has("creation_date"))       { this.setCreationDate    (                        object.get("creation_date"     ).getAsLong   ()        ); }
      if (object.has("display_name"))        { this.setDisplayName     (Parser.unescapeEntities(object.get("display_name"      ).getAsString (), false)); }
      if (object.has("link"))                { this.setLink            (                        object.get("link"              ).getAsString ()        ); }
      if (object.has("location"))            { this.setLocation        (Parser.unescapeEntities(object.get("location"          ).getAsString (), false)); }
      if (object.has("profile_image"))       { this.setProfileImage    (                        object.get("profile_image"     ).getAsString ()        ); }
      if (object.has("timed_penalty_date"))  { this.setTimedPenaltyDate(                        object.get("timed_penalty_date").getAsLong   ()        ); }
      if (object.has("user_id"))             { this.setUserId          (                        object.get("user_id"           ).getAsLong   ()        ); }
      if (object.has("website_url"))         { this.setWebsiteUrl      (                        object.get("website_url"       ).getAsString ()        ); }
   }


   public String getSite()                                 { return site; }


   public String getAboutMe()                              { return aboutMe; }
   public void setAboutMe(String aboutMe)                  { this.aboutMe = aboutMe; }

   public Long getAccountId()                              { return accountId; }
   public void setAccountId(Long accountId)                { this.accountId = accountId; }

   public Long getCreationDate()                           { return creationDate; }
   public void setCreationDate(Long creationDate)          { this.creationDate = creationDate; }

   public String getDisplayName()                          { return displayName; }
   public void setDisplayName(String displayName)          { this.displayName = displayName; }

   public String getLink()                                 { return link; }
   public void setLink(String link)                        { this.link = link; }

   public String getLocation()                             { return location; }
   public void setLocation(String location)                { this.location = location; }

   public String getProfileImage()                         { return profileImage; }
   public void setProfileImage(String profileImage)        { this.profileImage = profileImage; }

   public Long getTimedPenaltyDate()                       { return timedPenaltyDate; }
   public void setTimedPenaltyDate(Long timedPenaltyDate)  { this.timedPenaltyDate = timedPenaltyDate; }

   public Long getUserId()                                 { return userId; }
   public void setUserId(Long userId)                      { this.userId = userId; }

   public String getWebsiteUrl()                           { return websiteUrl; }
   public void setWebsiteUrl(String websiteUrl)            { this.websiteUrl = websiteUrl; }


   @Override
   public String toString()
   {
      String returnText = "User{"
                        + " displayName='" + displayName + '\''
                        + ", link='"       + link        + '\'';

      if (timedPenaltyDate != null)  { returnText += ", timedPenaltyDate=" + timedPenaltyDate + '\''; }
      if (location         != null)  { returnText += ", location='"        + location         + '\''; }
      if (websiteUrl       != null)  { returnText += ", websiteUrl='"      + websiteUrl       + '\''; }
      if (aboutMe          != null)  { returnText += ", aboutMe='"         + aboutMe                ; }

      returnText += '}';
      return returnText;
   }


   private String  site;

   private String  aboutMe;
   private Long    accountId;
   private Long    creationDate;
   private String  displayName;
   private String  link;
   private String  location;
   private String  profileImage;
   private Long    timedPenaltyDate;
   private Long    userId;
   private String  websiteUrl;
}

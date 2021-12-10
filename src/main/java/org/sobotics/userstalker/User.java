package org.sobotics.userstalker;


import org.jsoup.parser.Parser;

import com.google.gson.JsonObject;


public class User
{
   public User(String site, JsonObject object)
   {
      this.site = site;

      if (object.has("about_me"))            { this.aboutMe          =                         object.get("about_me"          ).getAsString()        ; }
      if (object.has("account_id"))          { this.accountID        =                         object.get("account_id"        ).getAsInt   ()        ; }
      if (object.has("creation_date"))       { this.creationDate     =                         object.get("creation_date"     ).getAsLong  ()        ; }
      if (object.has("display_name"))        { this.displayName      = Parser.unescapeEntities(object.get("display_name"      ).getAsString(), false); }
      if (object.has("link"))                { this.link             =                         object.get("link"              ).getAsString()        ; }
      if (object.has("location"))            { this.location         = Parser.unescapeEntities(object.get("location"          ).getAsString(), false); }
      if (object.has("profile_image"))       { this.profileImage     =                         object.get("profile_image"     ).getAsString()        ; }
      if (object.has("timed_penalty_date"))  { this.timedPenaltyDate =                         object.get("timed_penalty_date").getAsLong  ()        ; }
      if (object.has("user_id"))             { this.userID           =                         object.get("user_id"           ).getAsInt   ()        ; }
      if (object.has("website_url"))         { this.websiteUrl       =                         object.get("website_url"       ).getAsString()        ; }
   }


   public String  getSite()              { return this.site;             }

   public String  getAboutMe()           { return this.aboutMe;          }
   public Integer getNetworkAccountID()  { return this.accountID;        }
   public Long    getCreationDate()      { return this.creationDate;     }
   public String  getDisplayName()       { return this.displayName;      }
   public String  getLink()              { return this.link;             }
   public String  getLocation()          { return this.location;         }
   public String  getProfileImage()      { return this.profileImage;     }
   public Long    getTimedPenaltyDate()  { return this.timedPenaltyDate; }
   public Integer getUserID()            { return this.userID;           }
   public String  getWebsiteUrl()        { return this.websiteUrl;       }


   @Override
   public String toString()
   {
      String returnText = "User{"
                        + " displayName='" + this.getDisplayName() + '\''
                        + ", link='"       + this.getLink()        + '\'';
      if (this.getTimedPenaltyDate() != null)  { returnText += ", timedPenaltyDate=" + this.getTimedPenaltyDate() + '\''; }
      if (this.getLocation()         != null)  { returnText += ", location='"        + this.getLocation()         + '\''; }
      if (this.getWebsiteUrl()       != null)  { returnText += ", websiteUrl='"      + this.getWebsiteUrl()       + '\''; }
      if (this.getAboutMe()          != null)  { returnText += ", aboutMe='"         + this.getAboutMe()                ; }
      returnText += '}';
      return returnText;
   }


   private String  site;

   private String  aboutMe;
   private Integer accountID;
   private Long    creationDate;
   private String  displayName;
   private String  link;
   private String  location;
   private String  profileImage;
   private Long    timedPenaltyDate;
   private Integer userID;
   private String  websiteUrl;
}

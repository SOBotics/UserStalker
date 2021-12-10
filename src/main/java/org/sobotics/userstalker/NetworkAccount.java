package org.sobotics.userstalker;


import org.jsoup.parser.Parser;

import com.google.gson.JsonObject;


public class NetworkAccount
{
   public NetworkAccount(JsonObject object)
   {
      if (object.has("account_id"))        { this.accountID      =                         object.get("account_id"        ).getAsInt   ()        ; }
      if (object.has("user_id"))           { this.userID         =                         object.get("user_id"           ).getAsInt   ()        ; }
      if (object.has("site_name"))         { this.siteName       = Parser.unescapeEntities(object.get("site_name"         ).getAsString(), false); }
      if (object.has("site_url"))          { this.siteUrl        =                         object.get("site_url"          ).getAsString()        ; }
      if (object.has("user_type"))         { this.userType       =                         object.get("user_type"         ).getAsString()        ; }
      if (object.has("reputation"))        { this.reputation     =                         object.get("reputation"        ).getAsInt   ()        ; }
      if (object.has("question_count"))    { this.questionCount  =                         object.get("question_count"    ).getAsInt   ()        ; }
      if (object.has("answer_count"))      { this.answerCount    =                         object.get("answer_count"      ).getAsInt   ()        ; }
      if (object.has("creation_date"))     { this.creationDate   =                         object.get("creation_date"     ).getAsLong  ()        ; }
      if (object.has("last_access_date"))  { this.lastAccessDate =                         object.get("last_access_date"  ).getAsLong  ()        ; }
   }

   public Integer getNetworkAccountID()  { return this.accountID;      }
   public Integer getUserID()            { return this.userID;         }
   public String  getSiteName()          { return this.siteName;       }
   public String  getSiteUrl()           { return this.siteUrl;        }
   public String  getUserType()          { return this.userType;       }
   public Integer getReputation()        { return this.reputation;     }
   public Integer getQuestionCount()     { return this.questionCount;  }
   public Integer getAnswerCount()       { return this.answerCount;    }
   public Long    getCreationDate()      { return this.creationDate;   }
   public Long    getLastAccessDate()    { return this.lastAccessDate; }

   public Integer getPostCount()
   {
      if (this.getQuestionCount() == null)
      {
         return null;
      }
      else if (this.getAnswerCount() == null)
      {
         return null;
      }
      else
      {
         return (this.getQuestionCount().intValue() + this.getAnswerCount().intValue());
      }
   }

   private Integer accountID;
   private Integer userID;
   private String  siteName;
   private String  siteUrl;
   private String  userType;
   private Integer reputation;
   private Integer questionCount;
   private Integer answerCount;
   private Long    creationDate;
   private Long    lastAccessDate;
}

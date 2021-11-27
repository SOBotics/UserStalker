package org.sobotics.userstalker;


import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class StackExchangeApiClient
{
   private static final String            API_USERS_URL  = "https://api.stackexchange.com/2.3/users";
   private static final String            API_KEY        = "XKgBTF5nztGvMnDoI8gPgA((";
   private static final String            API_FILTER     = "!Ln3l_2int_VA.0Iu5wL3aW";
   private static final DateTimeFormatter DTF_AS_MINUTES = DateTimeFormatter.ofPattern("MMM d yyyy 'at' HH:mm");

   private static final Logger LOGGER = LoggerFactory.getLogger(StackExchangeApiClient.class);

   private int quota;


   public StackExchangeApiClient()
   {
      this.quota = 10000;
   }


   public int GetQuota()
   {
      return this.quota;
   }


   public User GetUser(String site, int userID)
   {
      User user = null;
      try
      {
         JsonObject jsonObject = this.Request(Connection.Method.GET,
                                              API_USERS_URL + "/" + userID,
                                              "site"    ,  site,
                                              "sort"    , "creation",
                                              "pagesize", "100",
                                              "page"    , "1",
                                              "filter"  , "!-*jbN*IioeFP",
                                              "order"   , "desc",
                                              "key"     , API_KEY);

         if (jsonObject.has("items"))
         {
            JsonArray jsonArray = jsonObject.get("items").getAsJsonArray();
            if (jsonArray.size() > 0)
            {
               user = new User(jsonArray.get(0).getAsJsonObject());
            }
         }
      }
      catch (IOException ex)
      {
         LOGGER.warn("Failed to retrieve user information from SE API.");
         ex.printStackTrace();
      }
      return user;
   }

   public JsonArray GetAllUsersAsJson(String site, long sinceEpochSeconds)
   {
      try
      {
         JsonObject jsonObject = this.Request(Connection.Method.GET,
                                              API_USERS_URL,
                                              "site"     , site,
                                              "sort"     , "creation",
                                              "pagesize" , "100",
                                              "page"     , "1",
                                              "filter"   , API_FILTER,
                                              "order"    , "desc",
                                              "fromdate" , String.valueOf(sinceEpochSeconds),
                                              "key"      , API_KEY);
         if (jsonObject.has("items"))
         {
            JsonArray jsonArray = jsonObject.get("items").getAsJsonArray();
            if (jsonArray.size() > 0)
            {
               return jsonArray;
            }
         }
      }
      catch (IOException ex)
      {
         LOGGER.warn("Failed to retrieve user information from SE API.");
         ex.printStackTrace();
      }
      return null;
   }


    public static String FormatDateTimeToNearestMinute(long apiDateTime)
    {
       // SE API date-time fields are Unix epochs in UTC format.
       ZonedDateTime dateTime = Instant.ofEpochSecond(apiDateTime).atZone(ZoneOffset.UTC);

       // Round to the nearest whole minute.
       if (dateTime.getSecond() >= 30)
       {
          dateTime = dateTime.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
       }

       return dateTime.format(DTF_AS_MINUTES);
    }


   private static void HandleBackoff(JsonObject root)
   {
      if (root.has("backoff"))
      {
         int backoffInSeconds = root.get("backoff").getAsInt();

         LOGGER.info("Backing off API for " + backoffInSeconds + " sec.");

         try
         {
            TimeUnit.SECONDS.sleep(backoffInSeconds);
         }
         catch (InterruptedException ex)
         {
            Thread.currentThread().interrupt();
         }
      }
   }

   private void UpdateQuota(JsonObject root)
   {
      this.quota = root.get("quota_remaining").getAsInt();

      LOGGER.info("API quota left: " + this.quota + ".");
   }

   private JsonObject Request(Connection.Method method, String url, String... data) throws IOException
   {
      int MAX_ITERATIONS = 2;
      for (int i = 0; i < MAX_ITERATIONS; ++i)
      {
         try
         {
            Connection.Response response = Jsoup.connect          (url)
                                                .data             (data)
                                                .method           (method)
                                                .ignoreContentType(true)
                                                .ignoreHttpErrors (true)
                                                .execute          ();
            String              json     = response.body();
            if (response.statusCode() == 200)
            {
               JsonObject root = JsonParser.parseString(json).getAsJsonObject();
               this.HandleBackoff(root);
               this.UpdateQuota  (root);
               return root;
            }
            else
            {
               throw new IOException("HTTP "
                                   + response.statusCode()
                                   + " requesting URL \""
                                   + url
                                   + "\". Body is: "
                                   + response.body());
            }
         }
         catch (SocketTimeoutException ex)
         {
            LOGGER.warn("Caught SocketTimeoutException when making API request: " + ex);

            if (i < (MAX_ITERATIONS - 1))
            {
               try
               {
                  TimeUnit.SECONDS.sleep(1);
               }
               catch (InterruptedException ex2)
               {
                  Thread.currentThread().interrupt();
               }
            }
         }
      }
      return null;
   }
}

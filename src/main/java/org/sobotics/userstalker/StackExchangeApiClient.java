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

import org.sobotics.userstalker.StackExchangeSiteInfo;


public class StackExchangeApiClient
{
   private static final String            API_USERS_URL     = "https://api.stackexchange.com/2.3/users";
   private static final String            API_USERS_FILTER  = "!)4T7SDSXYTGhY8vIBklauF55f";
   private static final String            API_KEY           = "XKgBTF5nztGvMnDoI8gPgA((";
   private static final String            API_PAGE_SIZE_MAX = "100";
   private static final DateTimeFormatter FMT_DT_AS_MINUTES = DateTimeFormatter.ofPattern("MMM d yyyy 'at' HH:mm");

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
         JsonObject jsonObject = this.SendRequest(Connection.Method.GET,
                                                  API_USERS_URL + "/" + userID,
                                                  "site"    ,  site,
                                                  "key"     , API_KEY,
                                                  "filter"  , API_USERS_FILTER,
                                                  "sort"    , "creation",
                                                  "order"   , "desc",
                                                  "page"    , "1",
                                                  "pagesize", "1");

         if (jsonObject.has("items"))
         {
            JsonArray jsonArray = jsonObject.get("items").getAsJsonArray();
            if (jsonArray.size() > 0)
            {
               user = new User(site, jsonArray.get(0).getAsJsonObject());
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

   // TODO: Handle has_more (by calling API again to get an additional page, and merging into "items")
   public JsonArray GetAllUsersAsJson(String site, StackExchangeSiteInfo siteInfo)
   {
      try
      {
         JsonObject jsonObject = this.SendRequest(Connection.Method.GET,
                                                  API_USERS_URL,
                                                  "site"     , site,
                                                  "key"      , API_KEY,
                                                  "filter"   , API_USERS_FILTER,
                                                  "sort"     , "creation",
                                                  "order"    , "asc",
                                                  "page"     , "1",
                                                  "pagesize" , API_PAGE_SIZE_MAX,
                                                  "fromdate" , String.valueOf(siteInfo.FromDate),
                                                  "todate"   , String.valueOf(siteInfo.ToDate  ));
         if (jsonObject.has("items"))
         {
            JsonArray jsonArray = jsonObject.get("items").getAsJsonArray();
            int       count     = jsonArray.size();
            if (count > 0)
            {
               siteInfo.TotalUsers += count;
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

       return dateTime.format(FMT_DT_AS_MINUTES);
    }


   private static void HandleBackoff(JsonObject root)
   {
      if (root.has("backoff"))
      {
         int backoffInSeconds = root.get("backoff").getAsInt();

         LOGGER.info("Backing off API for " + backoffInSeconds + " sec.");

         try
         {
            // NOTE: The expected backoff time is apparently *inclusive*, so we need to add 1 extra
            //       millisecond to the requested backoff time, otherwise we risk a 502 error saying
            //       that we violated the backoff parameter. For example, from the logs:
            //          05:17:05,951  INFO StackExchangeApiClient:135 - Backing off API for 10 sec.
            //          05:17:15,951  INFO StackExchangeApiClient:152 - API quota left: 6922.
            //          05:17:15,951  INFO ChatBot:293 - Doing a thing at 2021-11-28T05:17:15.951703Z...
            //          05:17:16,008  WARN StackExchangeApiClient:107 - Failed to retrieve user information from SE API.
            //                        java.io.IOException: HTTP 400 requesting URL "https://api.stackexchange.com/2.3/users".
            //                        Body is: {"error_id":502,"error_message":"Violation of backoff parameter"
            //       These are serially-recorded events. Although I backed off the full 10 seconds,
            //       as requested in the backoff field in the reply, which you can see in the
            //       timestamps for each event, when I made the new request 10 seconds later,
            //       the SE API incorrectly said that I violated the backoff parameter.
            //       The other events in the logs that succeeded following a backoff request all
            //       seemed to have been made, by mere coincidence, one millisecond later.
            TimeUnit.MILLISECONDS.sleep((backoffInSeconds * 1000) + 1);
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

   private JsonObject SendRequest(Connection.Method method,
                                  String            url,
                                  String...         data) throws IOException
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

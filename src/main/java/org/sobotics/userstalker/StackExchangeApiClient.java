package org.sobotics.userstalker;


import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;

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

   private Consumer<Integer> fxnRollover;
   private int               quota;


   public StackExchangeApiClient(Consumer<Integer> fxnRollover)
   {
      this.fxnRollover = fxnRollover;
      this.quota       = 10000;
   }


   public int GetQuota()
   {
      return this.quota;
   }


   public User GetUser(String site, int userID)
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
      if ((jsonObject != null) && jsonObject.has("items"))
      {
         JsonArray jsonArray = jsonObject.get("items").getAsJsonArray();
         int       count     = jsonArray.size();
         if (count > 0)
         {
            return new User(site, jsonArray.get(0).getAsJsonObject());
         }
      }
      else
      {
         LOGGER.warn("Failed to retrieve information about a single user from the SE API.");
      }
      return null;
   }

   // TODO: Handle has_more (by calling API again to get an additional page, and merging into "items")
   public List<User> GetAllUsers(String site, StackExchangeSiteInfo siteInfo)
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
      if ((jsonObject != null) && jsonObject.has("items"))
      {
         JsonArray jsonArray = jsonObject.get("items").getAsJsonArray();
         LOGGER.debug("JSON returned from SE API: " + jsonArray.toString());

         int        cUsers    = jsonArray.size();
         List<User> users     = new ArrayList<User>(cUsers);
         siteInfo.TotalUsers += cUsers;

         for (JsonElement element : jsonArray)
         {
            JsonObject object = element.getAsJsonObject();
            User       user   = new User(site, object);
            LOGGER.info("New user detected: \"" + user.getDisplayName() + "\" (" + user.getLink() + ").");
            users.add(user);
         }

         return users;
      }
      else
      {
         LOGGER.warn("Failed to retrieve information about multiple users from the SE API.");
         return null;
      }
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

         LOGGER.info("Backing off SE API for " + backoffInSeconds + " sec.");

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
      int oldQuota = this.quota;
      this.quota   = root.get("quota_remaining").getAsInt();

      if (this.quota > oldQuota)
      {
         this.fxnRollover.accept(oldQuota);
      }

      LOGGER.info("Remaining SE API quota: " + this.quota + ".");
   }

   private JsonObject SendRequest(Connection.Method method, String url, String... data)
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
               LOGGER.warn("SE API returned HTTP "
                         + response.statusCode()
                         + " when requesting URL \""
                         + url
                         + "\". Body is: "
                         + response.body());

               // The error 502 ("Violation of backoff parameter") is spontaneously returned by the
               // SE API for unknown reasons, even though we are appropriately handling the logic
               // for the backoff parameter. It fails for all of the requests in that same block,
               // but will then magically start working again in the next batch (or so). There is no
               // suggested backoff value returned in the event of a backoff error (although it was
               // suggested before, many years ago: https://meta.stackexchange.com/q/256691), but
               // we can try waiting a "standard" amount of time and then try again.
               try                              { TimeUnit.SECONDS.sleep(20); }
               catch (InterruptedException ex)  { Thread.currentThread().interrupt(); }
               continue;
            }
         }
         catch (SocketTimeoutException ex)
         {
            LOGGER.warn("Caught SocketTimeoutException when making SE API request: " + ex);

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
         catch (IOException ex)
         {
            LOGGER.warn("Attempt to request URL \""
                      + url
                      + " from SE API failed.");
            ex.printStackTrace();
            return null;
         }
      }
      return null;
   }
}

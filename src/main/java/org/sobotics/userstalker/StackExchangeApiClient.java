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
import java.util.HashMap;
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


public class StackExchangeApiClient
{
   private static final String            API_USERS_URL            = "https://api.stackexchange.com/2.3/users";
   private static final String            API_SITE_USERS_FILTER    = "!)4T7SDSXYTGhY8vIBklauF55f";
   private static final String            API_NETWORK_USERS_FILTER = "!VuI2L*-aRyQz*";
   private static final String            API_KEY                  = "XKgBTF5nztGvMnDoI8gPgA((";
   private static final int               API_PAGE_SIZE_MAX        = 100;
   private static final DateTimeFormatter FMT_DT_AS_MINUTES        = DateTimeFormatter.ofPattern("MMM d yyyy 'at' HH:mm");

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
                                               "filter"  , API_SITE_USERS_FILTER,
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

   public ArrayList<User> GetAllUsers(String site, StackExchangeSiteInfo siteInfo)
   {
      ArrayList<User> users   = new ArrayList<User>(API_PAGE_SIZE_MAX);
      boolean         hasMore = true;
      int             page    = 1;
      while (hasMore)
      {
         JsonObject jsonObject = this.SendRequest(Connection.Method.GET,
                                                  API_USERS_URL,
                                                  "site"    , site,
                                                  "key"     , API_KEY,
                                                  "filter"  , API_SITE_USERS_FILTER,
                                                  "sort"    , "creation",
                                                  "order"   , "asc",
                                                  "page"    , String.valueOf(page),
                                                  "pagesize", String.valueOf(API_PAGE_SIZE_MAX),
                                                  "fromdate", String.valueOf(siteInfo.FromDate),
                                                  "todate"  , String.valueOf(siteInfo.ToDate  ));
         if ((jsonObject != null) && jsonObject.has("items"))
         {
            JsonArray jsonArray = jsonObject.get("items").getAsJsonArray();
            LOGGER.debug("JSON returned from SE API: " + jsonArray.toString());

            int cUsers           = jsonArray.size();
            siteInfo.TotalUsers += cUsers;

            for (JsonElement element : jsonArray)
            {
               JsonObject object = element.getAsJsonObject();
               User       user   = new User(site, object);
               LOGGER.info("New user detected: \"" + user.getDisplayName() + "\" (" + user.getLink() + ").");
               users.add(user);
            }

            hasMore = jsonObject.get("has_more").getAsBoolean();
            if (hasMore)
            {
               ++page;

               users.ensureCapacity(API_PAGE_SIZE_MAX * page);

               // If we are about to fetch another page from the API, add a > 10 second delay
               // to hopefully avoid the dreaded "too many requests from this IP address" error.
               try                              { TimeUnit.SECONDS.sleep(11); }
               catch (InterruptedException ex)  { Thread.currentThread().interrupt(); }
            }
         }
         else
         {
            LOGGER.warn("Failed to retrieve information about multiple users from the SE API.");
            return null;
         }
      }
      return users;
   }


   public HashMap<Integer, ArrayList<NetworkAccount>> GetAllNetworkAccounts(List<SuspiciousUser> users)
   {
      if (users == null)
      {
         return null;
      }

      int           approximateCount  = users.size();
      StringBuilder networkAccountIDs = new StringBuilder(approximateCount * 10);
      for (SuspiciousUser suspiciousUser : users)
      {
         // If the user has a network account ID, add it to the semicolon-delimited list
         // of network account IDs that we need to retrive information about.
         Integer networkAccountID = suspiciousUser.user.getNetworkAccountID();
         if (networkAccountID != null)
         {
            if (networkAccountIDs.length() > 0)
            {
               networkAccountIDs.append(";");
            }
            networkAccountIDs.append(String.valueOf(networkAccountID));
         }
      }

      return this.GetAllNetworkAccounts(networkAccountIDs.toString(), approximateCount);
   }

   public HashMap<Integer, ArrayList<NetworkAccount>> GetAllNetworkAccounts(String networkAccountIDs,
                                                                            int    approximateCount)
   {
      HashMap<Integer, ArrayList<NetworkAccount>> networkAccountsMap = new HashMap<Integer, ArrayList<NetworkAccount>>((API_PAGE_SIZE_MAX * approximateCount) / 2);
      boolean                                     hasMore            = true;
      int                                         page               = 1;
      while (hasMore)
      {
         JsonObject jsonObject = this.SendRequest(Connection.Method.GET,
                                                  API_USERS_URL + "/" + networkAccountIDs + "/associated",
                                                  "key"     , API_KEY,
                                                  "filter"  , API_NETWORK_USERS_FILTER,
                                                  "types"   , "main_site",
                                                  "page"    , String.valueOf(page),
                                                  "pagesize", String.valueOf(API_PAGE_SIZE_MAX));
         if ((jsonObject != null) && jsonObject.has("items"))
         {
            JsonArray jsonArray = jsonObject.get("items").getAsJsonArray();
            LOGGER.debug("JSON returned from SE API: " + jsonArray.toString());

            for (JsonElement element : jsonArray)
            {
               JsonObject                object           = element.getAsJsonObject();
               Integer                   networkAccountID = object.get("account_id").getAsInt();
               ArrayList<NetworkAccount> networkAccounts  = networkAccountsMap.get(networkAccountID);
               if (networkAccounts == null)
               {
                  networkAccounts = new ArrayList<NetworkAccount>(5);
               }
               networkAccounts.add(new NetworkAccount(object));
               networkAccountsMap.put(networkAccountID, networkAccounts);
            }

            hasMore = jsonObject.get("has_more").getAsBoolean();
            if (hasMore)
            {
               ++page;

               // If we are about to fetch another page from the API, add a > 10 second delay
               // to hopefully avoid the dreaded "too many requests from this IP address" error.
               try                              { TimeUnit.SECONDS.sleep(11); }
               catch (InterruptedException ex)  { Thread.currentThread().interrupt(); }
            }
         }
         else
         {
            LOGGER.warn("Failed to retrieve information about multiple users' network accounts from the SE API.");
            return null;
         }
      }
      return networkAccountsMap;
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

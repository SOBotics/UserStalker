package org.sobotics.userstalker;


import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.sobotics.chatexchange.chat.ChatHost;
import org.sobotics.chatexchange.chat.Message;
import org.sobotics.chatexchange.chat.Room;
import org.sobotics.chatexchange.chat.StackExchangeClient;
import org.sobotics.chatexchange.chat.event.EventType;
import org.sobotics.chatexchange.chat.event.PingMessageEvent;
import org.sobotics.chatexchange.chat.event.UserEnteredEvent;

import org.sobotics.userstalker.RegexManager;
import org.sobotics.userstalker.StackExchangeApiClient;
import org.sobotics.userstalker.StackExchangeSiteInfo;
import org.sobotics.userstalker.User;


public class ChatBot
{
   private static final int    POLL_TIME_MINUTES = 3;
   private static final String BOT_URL           = "https://git.io/v5CGT";
   private static final String CHAT_MSG_PREFIX   = "[ [User Stalker](" + BOT_URL + ") ]";
   private static final String UNKNOWN_CMD_MSG   = "You talkin\u2019 to me? Psst\u2026ping me and say \"help\".";
   private static final String HELP_CMD_MSG      =
  "I'm User Stalker (" + BOT_URL + "), a bot that continuously queries the "
+ "Stack Exchange \"/users\" API (https://api.stackexchange.com/docs/users) in order to "
+ "track all newly-created user accounts. If a suspicious pattern is detected in one of "
+ "these newly-created user accounts, the bot will post a message in this room so that "
+ "the account can be manually reviewed by a moderator. If you confirm upon review that "
+ "the user account merits any further action, such as removal, you can do so."
+ "\n\n"
+ "In addition to \"help\", I recognize some additional commands:"
+ "\n"
+ "\u3000\u25CF \"alive\": Replies in the affirmative if the bot is up and running; silently ignores you if it is not."
+ "\n"
+ "\u3000\u25CF \"check <user URL>\": Runs the pattern checks on the specified user account as if it were a newly-created account,"
+                                   " and then replies with the results."
+ "\n"
+ "\u3000\u25CF \"test <user URL>\": Same as \"check <user URL>\"."
+ "\n"
+ "\u3000\u25CF \"quota\": Replies with the currently remaining size of the API quota for the stalking service."
+ "\n"
+ "\u3000\u25CF \"sites\": Replies with the list of Stack Exchange sites that are currently being stalked."
+ "\n"
+ "\u3000\u25CF \"list\": Same as \"sites\"."
+ "\n"
+ "\u3000\u25CF \"add <sitename>\": Temporarily adds the specified SE site (short name) to the stalking list."
+                                 " (The modification will not persist across a reboot.)"
+ "\n"
+ "\u3000\u25CF \"remove <sitename>\": Temporarily removes the specified SE site (short name) from the stalking list."
+                                    " (The modification will not persist across a reboot.)"
+ "\n"
+ "\u3000\u25CF \"update\": Updates the cached pattern databases that are used when checking user accounts."
+ "\n"
+ "\u3000\u25CF \"stop\": Stops the stalking service."
+                       " (The bot will remain in the room in order to respond to commands.)"
+ "\n"
+ "\u3000\u25CF \"start\": Starts the stalking service from where it left off."
+                       " (Intended to be used after a \"stop\" command.)"
+ "\n"
+ "\u3000\u25CF \"restart\": Starts the stalking service fresh, beginning from the current time."
+                          " (Can be used after a \"stop\" command, or any other time.)"
+ "\n"
+ "\u3000\u25CF \"reboot\": Turns it off and back on again by rebooting the entire bot, as if the server had been rebooted."
+                         " (Any changes to pattern databases will be picked up at this time, and"
+                         " all temporary changes to the stalking lists will be lost.)"
+ "\n\n"
+ "If you're still confused or need more help, you can ping Cody Gray (but he may not be as nice as me!)."
;

   private static final Logger LOGGER = LoggerFactory.getLogger(ChatBot.class);

   private StackExchangeClient                client;
   private List<String>                       sitesSO;
   private List<String>                       sitesSE;
   private Room                               roomSO;
   private Room                               roomSE;
   private RegexManager                       regexes;
   private StackExchangeApiClient             seApi;
   private ScheduledExecutorService           executor;
   private Map<String, StackExchangeSiteInfo> siteInfoMap;


   public ChatBot(String emailAddress, String password)
   {
      // Log in to Stack Exchange with the specified credentials.
      LOGGER.info("Logging in...");
      try
      {
         this.client = new StackExchangeClient(emailAddress, password);
      }
      catch (Exception ex)
      {
         LOGGER.error("Failed to log in and initialize Stack Exchange chat client.");
         throw ex;
      }
   }


   public void JoinSO(int roomID)
   {
      LOGGER.info("Joining Stack Overflow room " + roomID + "...");
      if ((this.roomSO == null) && (this.sitesSO == null))
      {
         this.sitesSO = new ArrayList<String>();
         this.sitesSO.add("stackoverflow");

         this.roomSO = this.JoinRoom(ChatHost.STACK_OVERFLOW, roomID, true);
      }
      else
      {
         throw new IllegalStateException("The chat bot has already joined a room on Stack Overflow.");
      }
   }

   public void JoinSE(int roomID, List<String> sites)
   {
      LOGGER.info("Joining Stack Exchange room " + roomID + "...");
      if ((this.roomSE == null) && (this.sitesSE == null))
      {
         this.sitesSE = new ArrayList<String>();
         if (sites != null)
         {
            this.sitesSE.addAll(sites);
         }

         this.roomSE = this.JoinRoom(ChatHost.STACK_EXCHANGE, roomID, true);
      }
      else
      {
         throw new IllegalStateException("The chat bot has already joined a room on Stack Exchange.");
      }
   }

   private Room JoinRoom(ChatHost host, int roomID, boolean addListeners)
   {
      Room room = this.client.joinRoom(host, roomID);
      if (addListeners)
      {
         room.addEventListener(EventType.USER_ENTERED  , event -> this.OnUserEntered(room, event));
         room.addEventListener(EventType.USER_MENTIONED, event -> this.OnMentioned  (room, event, false));
       //room.addEventListener(EventType.MESSAGE_REPLY , event -> this.OnMentioned  (room, event, true ));
      }
      return room;
   }


   public void Run()
   {
      // Load the regular expressions.
      this.regexes = new RegexManager();

      // Initialize the Stack Exchange API client.
      this.seApi = new StackExchangeApiClient();

      // Start the stalking service.
      this.DoStart(false);
   }


   private boolean IsRunning()
   {
      return (this.executor != null);
   }

   private void BroadcastMessage(String message)
   {
      if (this.roomSO != null)  { this.roomSO.send(message); }
      if (this.roomSE != null)  { this.roomSE.send(message); }
   }


   private void OnUserEntered(Room room, UserEnteredEvent event)
   {
      LOGGER.info("User \""
                + event.getUserName()
                + "\" has entered room "
                + room.getRoomId()
                + " on "
                + room.getHost().getName()
                + ".");
   }

   private void OnMentioned(Room room, PingMessageEvent event, boolean isReply)
   {
      Message  message       = event.getMessage();
      long     replyID       = message.getId();
      String   messageString = message.getPlainContent();
      String[] messageParts  = messageString.trim().toLowerCase().split(" ");

      LOGGER.info("New mention in room "
                + room.getRoomId()
                + " on "
                + room.getHost().getName()
                + " ["
                + message.getContent()
                + "]");

      if (messageParts.length == 2)
      {
         if (messageParts[1].equals("help"))
         {
            this.DoHelp(room, replyID);
            return;
         }
         else if (messageParts[1].equals("alive"))
         {
            this.DoAlive(room, replyID);
            return;
         }
         else if (messageParts[1].equals("quota"))
         {
            this.DoQuota(room, replyID);
            return;
         }
         else if (messageParts[1].equals("sites") ||
                  messageParts[1].equals("list" ))
         {
            this.DoList(room, replyID);
            return;
         }
         else if (messageParts[1].equals("update"))
         {
            this.DoUpdate();
            return;
         }
         else if (messageParts[1].equals("stop"))
         {
            this.DoStop();
            return;
         }
         else if (messageParts[1].equals("start"))
         {
            this.DoStart(true);
            return;
         }
         else if (messageParts[1].equals("restart"))
         {
            this.DoStart(false);
            return;
         }
         else if (messageParts[1].equals("reboot"))
         {
            this.DoReboot();
            return;
         }
      }
      else if (messageParts.length == 3)
      {
         if (messageParts[1].equals("check") || messageParts[1].equals("test"))
         {
            String response;
            String urlParts[] = messageParts[2].split("/");
            if ((urlParts[3].equals("u") || urlParts[3].equals("users")))
            {
               response = this.CheckUser(urlParts[2],
                                         Integer.parseInt(urlParts[4]));
            }
            else
            {
               response = "The specified URL was not recognized as a user profile.";
            }
            room.replyTo(replyID, response);
            return;
         }
         else
         {
            this.DoListModify(room, replyID, messageParts[1], messageParts[2]);
            return;
         }
      }

      this.DoUnrecognized(room, replyID);
   }

   private void OnStalk()
   {
      LOGGER.info("Periodic stalker routine running...");

      if ((this.roomSO != null) && !this.sitesSO.isEmpty())
      {
         this.DoStalk(this.roomSO, this.sitesSO);
      }

      if ((this.roomSE != null) && !this.sitesSE.isEmpty())
      {
         this.DoStalk(this.roomSE, this.sitesSE);
      }
   }

   private void DoStalk(Room room, List<String> sites)
   {
      boolean showSite = (sites.size() > 1);
      for (String site : sites)
      {
         long                  startTime = Instant.now().getEpochSecond();
         StackExchangeSiteInfo siteInfo  = this.siteInfoMap.get(site);
         siteInfo.FromDate               = siteInfo.ToDate;
         siteInfo.ToDate                 = startTime;

         LOGGER.info("Stalking " + site + " at " + siteInfo.ToDate + " (last was at " + siteInfo.FromDate + ")...");

         JsonArray json = seApi.GetAllUsersAsJson(site, siteInfo);
         if (json != null)
         {
            LOGGER.debug("JSON returned from SE API: " + json.toString());
            for (JsonElement element : json)
            {
               JsonObject object = element.getAsJsonObject();
               User       user   = new User(site, object);
               LOGGER.info("New user detected: \"" + user.getDisplayName() + "\" (" + user.getLink() + ").");
               String reason = CheckUser(user);
               if (!reason.isBlank())
               {
                  LOGGER.info("Detected user \"" + user + "\": " + reason + ".");
                  siteInfo.SuspiciousUsers += 1;
                  ReportUser(room, user, reason, showSite);
               }
            }
         }
      }
   }

   private void DoUnrecognized(Room room, long replyID)
   {
      room.replyTo(replyID, UNKNOWN_CMD_MSG);
   }

   private void DoHelp(Room room, long replyID)
   {
      room.replyTo(replyID, HELP_CMD_MSG);
   }

   private void DoAlive(Room room, long replyID)
   {
      room.replyTo(replyID, "Yep, I'm alive!");
   }

   private void DoQuota(Room room, long replyID)
   {
      room.replyTo(replyID, "The remaining quota is " + this.seApi.GetQuota() + ".");
   }

   private void DoUpdate()
   {
      LOGGER.info("Beginning to update the pattern databases...");

      boolean wasRunning = this.IsRunning();
      if (wasRunning)
      {
         this.DoStop();
      }
      this.regexes.Reload();
      this.BroadcastMessage(CHAT_MSG_PREFIX + " The pattern databases have been successfully updated.");
      if (wasRunning)
      {
         this.DoStart(true);
      }
   }

   private void DoList(Room room, long replyID)
   {
      List<String> sites;
      if (room == this.roomSO)
      {
         sites = this.sitesSO;
      }
      else if (room == this.roomSE)
      {
         sites = this.sitesSE;
      }
      else
      {
         LOGGER.error("Ignoring a \"list\" command from unknown room: " + room);
         return;
      }

      String siteList = sites.stream().collect(Collectors.joining("`, `", "`", "`"));
      room.replyTo(replyID, "Stalking sites: " + sites);
   }

   private void DoListModify(Room room, long replyID, String command, String siteName)
   {
      boolean add    = command.equals("add");
      boolean remove = command.equals("remove");

      if (!add && !remove)
      {
         room.replyTo(replyID,
                      "The specified command (\"" + command + "\") was not recognized (must be either \"add\" or \"remove\".)");
         return;
      }

      if ((room != this.roomSO) && (room != this.roomSE))
      {
         LOGGER.error("Ignoring a \"" + command + "\" command from unknown room: " + room);
         return;
      }

      LOGGER.info("Beginning to modify (" + command + ") the list of sites being stalked.");

      boolean wasRunning = this.IsRunning();
      if (wasRunning)
      {
         this.DoStop();
      }

      String chatMessage = (add ? CHAT_MSG_PREFIX + " Temporarily adding `" + siteName + "` to the list of sites being stalked."
                                : CHAT_MSG_PREFIX + " Temporarily removing `" + siteName + "` from the list of sites being stalked.");
      if (room == this.roomSO)
      {
         if (add)  { this.sitesSO.add   (siteName); }
         else      { this.sitesSO.remove(siteName); }

         this.roomSO.send(chatMessage);
      }
      else if (room == this.roomSE)
      {
         if (add)  { this.sitesSE.add   (siteName); }
         else      { this.sitesSE.remove(siteName); }

         this.roomSE.send(chatMessage);
      }

      if (wasRunning)
      {
         this.DoStart(true);
      }
   }

   private void DoStop()
   {
      if (this.executor != null)
      {
         String stopMessage      = "Stopping the user stalker service...";
         String stopNowMessage   = "Failed to cleanly stop user stalker service; forcibly shutting it down...";
         String stopFinalMessage = "Forced shutdown timed out after 5 seconds; terminating anyway...";

         LOGGER.info(stopMessage);
         this.BroadcastMessage(CHAT_MSG_PREFIX + " " + stopMessage);

         this.executor.shutdown();
         try
         {
            if (!this.executor.awaitTermination(1, TimeUnit.MINUTES))
            {
               LOGGER.info(stopNowMessage);
               this.BroadcastMessage(CHAT_MSG_PREFIX + " " + stopNowMessage);

               this.executor.shutdownNow();
               if (!this.executor.awaitTermination(5, TimeUnit.SECONDS))
               {
                  LOGGER.info(stopFinalMessage);
                  this.BroadcastMessage(CHAT_MSG_PREFIX + " " + stopFinalMessage);
               }
            }
         }
         catch (InterruptedException ex)
         {
            this.executor.shutdownNow();
            Thread.currentThread().interrupt();
         }
         this.executor = null;
      }
      else
      {
         String message = "The user stalker service is not running.";
         LOGGER.info(message);
         this.BroadcastMessage(CHAT_MSG_PREFIX + " " + message);
      }
   }

   private void DoStart(boolean resume)
   {
      LOGGER.info("Attempting to start the user stalker service...");

      if (this.IsRunning())
      {
         this.DoStop();
      }

      boolean somethingToDo = (((this.roomSO != null) && !this.sitesSO.isEmpty()) ||
                               ((this.roomSE != null) && !this.sitesSE.isEmpty()));
      if (somethingToDo)
      {
         if (!resume || (this.siteInfoMap == null))
         {
            long startTime = Instant.now().minusSeconds(60).getEpochSecond();

            int sites        = (((this.sitesSO != null) ? this.sitesSO.size() : 0) +
                                ((this.sitesSE != null) ? this.sitesSE.size() : 0));
            this.siteInfoMap = new HashMap<String, StackExchangeSiteInfo>(sites * 2);

            if (this.sitesSO != null)
            {
               for (String site : this.sitesSO)
               {
                  this.siteInfoMap.put(site, new StackExchangeSiteInfo(startTime));
               }
            }
            if (this.sitesSE != null)
            {
               for (String site : this.sitesSE)
               {
                  this.siteInfoMap.put(site, new StackExchangeSiteInfo(startTime));
               }
            }
         }

         this.executor = Executors.newSingleThreadScheduledExecutor();
         this.executor.scheduleAtFixedRate(() -> { this.OnStalk(); },
                                           0,
                                           POLL_TIME_MINUTES,
                                           TimeUnit.MINUTES);
      }
      String message = (somethingToDo ? "The user stalker service has started."
                                      : "The user stalker service did not start because there is nothing to do.");
      LOGGER.info(message);
      this.BroadcastMessage(CHAT_MSG_PREFIX + " " + message);
   }

   private void DoReboot()
   {
      if (this.IsRunning())
      {
         this.DoStop();
      }

      LOGGER.info("Rebooting the bot...");
      if (this.roomSE != null)
      {
         this.roomSE.leave();
      }
      if (this.roomSO != null)
      {
         this.roomSO.leave();
      }
      System.exit(0);
   }


   private String CheckUser(User user)
   {
      String            name     = user.getDisplayName();
      String            location = user.getLocation();
      String            url      = user.getWebsiteUrl();
      String            about    = user.getAboutMe();
      ArrayList<String> reasons  = new ArrayList<String>(25);

      // Check for an active suspension.
      if (user.getTimedPenaltyDate() != null)
      {
         reasons.add("suspended until "
                   + StackExchangeApiClient.FormatDateTimeToNearestMinute(user.getTimedPenaltyDate()));
      }

      // Check the display name.
      if ((name != null) && !name.isBlank())
      {
         if (RegexManager.AnyMatches(name, this.regexes.NameBlacklist))
         {
            reasons.add("username on blacklist");
         }

         if (RegexManager.AnyMatches(name, this.regexes.NameSmokeyBlacklist))
         {
            reasons.add("username on Smokey's blacklist");
         }

         if (RegexManager.AnyMatches(name, this.regexes.OffensiveHi))
         {
            reasons.add("username contains highly offensive pattern");
         }

         if (RegexManager.AnyMatches(name, this.regexes.OffensiveMd))
         {
            reasons.add("username contains mildly offensive pattern");
         }

         if (RegexManager.AnyMatches(name, this.regexes.OffensiveLo))
         {
            reasons.add("username contains possibly offensive pattern");
         }

         if (name.contains(Integer.toString(Year.now().getValue())))
         {
            reasons.add("username contains current year");
         }
         if (name.contains(Integer.toString(Year.now().getValue() + 1)))
         {
            reasons.add("username contains next year");
         }

         if ((url != null) && !url.isBlank())
         {
            String normalizedName = name.replaceAll("[^a-zA-Z ]", "").toLowerCase();
            String normalizedUrl  = url .replaceAll("[^a-zA-Z ]", "").toLowerCase();
            if (name.toLowerCase().contains(normalizedUrl ) ||
                url .toLowerCase().contains(normalizedName))
            {
               reasons.add("URL similar to username");
            }
         }
      }

      // Check the URL.
      if ((url != null) && !url.isBlank())
      {
         if (RegexManager.AnyMatches(url, this.regexes.UrlBlacklist))
         {
            reasons.add("URL on blacklist");
         }
      }

      // Check the location.
      if ((location != null) && !location.isBlank())
      {
         if (RegexManager.AnyMatches(location, this.regexes.OffensiveHi))
         {
            reasons.add("location contains highly offensive pattern");
         }

         if (RegexManager.AnyMatches(location, this.regexes.OffensiveMd))
         {
            reasons.add("location contains mildly offensive pattern");
         }

         if (RegexManager.AnyMatches(location, this.regexes.OffensiveLo))
         {
            reasons.add("location contains possibly offensive pattern");
         }
      }

      // Check the "About Me".
      if ((about != null) && !about.isBlank())
      {
         if (RegexManager.AnyMatches(about, this.regexes.AboutBlacklist))
         {
            reasons.add("\"About Me\" contains blacklisted pattern");
         }

         if (RegexManager.AnyMatches(about, this.regexes.OffensiveHi))
         {
            reasons.add("\"About Me\" contains highly offensive pattern");
         }

         if (RegexManager.AnyMatches(about, this.regexes.OffensiveMd))
         {
            reasons.add("\"About Me\" contains mildly offensive pattern");
         }

         if (RegexManager.AnyMatches(about, this.regexes.OffensiveLo))
         {
            reasons.add("\"About Me\" contains possibly offensive pattern");
         }

         if (RegexManager.AnyMatches(about, this.regexes.PhonePatterns))
         {
            reasons.add("\"About Me\" contains phone number");
         }

         if (RegexManager.AnyMatches(about, this.regexes.EmailPatterns))
         {
            reasons.add("\"About Me\" contains email");
         }

         if (RegexManager.AnyMatches(about, this.regexes.UrlBlacklist))
         {
            reasons.add("\"About Me\" contains blacklisted URL");
         }

         if (about.toLowerCase().contains("</a>"))
         {
            reasons.add("\"About Me\" contains a link");
         }
      }

      return String.join("; ", reasons);
   }

   private String CheckUser(String site, int userID)
   {
      User user = this.seApi.GetUser(site, userID);
      if (user != null)
      {
         String name    = user.getDisplayName();
         String reasons = CheckUser(user);
         String result  = " [" + name.trim() + "](" + user.getLink() + "?tab=profile \"" + name + "\")";
         if (!reasons.isBlank())
         {
            result += " would be caught because: ";
            result += reasons;
         }
         else
         {
            result += " would not be caught.";
         }
         return result;
      }
      else
      {
         return "No such user found.";
      }
   }

   private void ReportUser(Room room, User user, String reason, boolean showSite)
   {
      boolean isSuspended = (user.getTimedPenaltyDate() != null);

      StringBuilder builder = new StringBuilder();
      builder.append(CHAT_MSG_PREFIX);
      builder.append(" [");
      if (isSuspended)
      {
         builder.append("*");
      }
      builder.append(user.getDisplayName().trim());
      if (isSuspended)
      {
         builder.append("*");
      }
      builder.append("](");
      builder.append(user.getLink());
      builder.append("?tab=profile \"");
      builder.append(user.getDisplayName());
      builder.append("\") ");
      if (showSite)
      {
         builder.append("on **`");
         builder.append(user.getSite());
         builder.append("`** ");
      }
      builder.append("(");
      builder.append(reason);
      builder.append(")");

      room.send(builder.toString());
   }
}

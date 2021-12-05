package org.sobotics.userstalker;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.Throwable;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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


public class ChatBot
{
   private static final int                 POLL_TIME_MINUTES    = 3;
   private static final Map<String, long[]> CHAT_ADMIN_USERIDS;
      static
      {
         HashMap<String, long[]> map = new HashMap<String, long[]>();
         map.put("stackoverflow.com", new long[] { 366904, });
         map.put("stackexchange.com", new long[] { 7959, });
         CHAT_ADMIN_USERIDS = Collections.unmodifiableMap(map);
      }
   private static final String              PERSISTED_STATE_FILE = "savedState.bin";
   private static final String              BOT_URL              = "https://git.io/v5CGT";
   private static final String              CHAT_MSG_PREFIX      = "[ [User Stalker](" + BOT_URL + ") ]";
   private static final String              UNKNOWN_CMD_MSG      = "You talkin\u2019 to me? Psst\u2026ping me and say \"help\".";
   private static final String              HELP_CMD_MSG         =
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
+ "\u3000\u25CF \"reboot\": Turns it off and back on again by rebooting the entire bot."
+                         " (All temporary changes to the stalking lists will be lost.)"
+ "\n"
+ "\u3000\u25CF \"upgrade\": Like \"reboot\", but pulls latest code from GitHub, performs a build, and"
+                         " replaces the current binary with the resulting new version before rebooting."
+                         " (All temporary changes to the stalking lists will be lost."
+                         "  Usage of this command is limited to users with admin privileges.)"
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
   private boolean                            stalkSE = true;


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
      this.seApi = new StackExchangeApiClient(this::OnQuotaRollover);

      // Start the stalking service.
      this.DoStart();
   }


   private boolean IsRunning()
   {
      return (this.executor != null);
   }


   private CompletableFuture<Long> SendMessage_Retry(Room      room,
                                                     String    message,
                                                     Throwable firstAttempt,
                                                     int       iAttempt)
   {
      if (iAttempt < 5)
      {
         return room.send(message)
                    .thenApply(CompletableFuture::completedFuture)
                    .exceptionally(t ->
                                   {
                                      firstAttempt.addSuppressed(t);

                                      try
                                      {
                                         TimeUnit.SECONDS.sleep(iAttempt + 1);
                                      }
                                      catch (InterruptedException ex)
                                      {
                                         Thread.currentThread().interrupt();
                                      }

                                      return this.SendMessage_Retry(room,
                                                                    message,
                                                                    firstAttempt,
                                                                    (iAttempt + 1));
                                   })
                    .thenCompose(Function.identity())
                    .toCompletableFuture();
      }
      else
      {
         return CompletableFuture.failedFuture(firstAttempt);
      }
   }

   private void SendMessage(Room room, String message)
   {
      if (room != null)
      {
         room.send(message)
             .thenApply(CompletableFuture::completedFuture)
             .exceptionally(t -> SendMessage_Retry(room, message, t, 0))
             .thenCompose(Function.identity());
      }
   }


   private void BroadcastMessage(String message)
   {
      if (this.roomSO != null)  { this.SendMessage(this.roomSO, message); }
      if (this.roomSE != null)  { this.SendMessage(this.roomSE, message); }
   }


   private void OnQuotaRollover(Integer oldQuota)
   {
      // Display quota rollover message.
      String message = "Stack Exchange API quota rolled over, leaving "
                     + String.valueOf(oldQuota)
                     + " requests remaining. New quota has "
                     + String.valueOf(seApi.GetQuota())
                     + " requests remaining.";
      LOGGER.info(message);
      this.BroadcastMessage(CHAT_MSG_PREFIX + " " + message);

      // Display statistical information on users per-site.
      if ((this.roomSO != null) && (this.sitesSO != null) && (!this.sitesSO.isEmpty()))
      {
         this.ReportStatistics(this.roomSO, this.sitesSO);
      }
      if ((this.roomSE != null) && (this.sitesSE != null) && (!this.sitesSE.isEmpty()))
      {
         this.ReportStatistics(this.roomSE, this.sitesSE);
      }

      // Reset per-site user statistics.
      for (StackExchangeSiteInfo siteInfo : this.siteInfoMap.values())
      {
         siteInfo.ResetUsers();
      }
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
            this.DoStart();
            return;
         }
         else if (messageParts[1].equals("reboot"))
         {
            this.DoReboot();
            return;
         }
         else if (messageParts[1].equals("upgrade"))
         {
            this.DoUpgrade(room, replyID, message.getUser());
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

      if (this.stalkSE)
      {
         if ((this.roomSE != null) && !this.sitesSE.isEmpty())
         {
            this.DoStalk(this.roomSE, this.sitesSE);
         }
      }
      this.stalkSE = !this.stalkSE;
   }

   private void DoStalk(Room room, List<String> sites)
   {
      boolean showSite = (sites.size() > 1);
      for (String site : sites)
      {
         StackExchangeSiteInfo siteInfo  = this.siteInfoMap.get(site);
         long                  oldTime   = siteInfo.ToDate;
         long                  startTime = Instant.now().minusSeconds(60).getEpochSecond();
         siteInfo.FromDate               = oldTime;
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
         else
         {
            LOGGER.warn("Failed to retrieve user information from SE API when stalking " + site + "; skipping this time.");
            siteInfo.ToDate = oldTime;
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
         this.DoStart();
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

         this.SendMessage(this.roomSO, chatMessage);
      }
      else if (room == this.roomSE)
      {
         if (add)  { this.sitesSE.add   (siteName); }
         else      { this.sitesSE.remove(siteName); }

         this.SendMessage(this.roomSE, chatMessage);
      }

      if (wasRunning)
      {
         this.DoStart();
      }
   }

   private void DoStop()
   {
      if (this.executor != null)
      {
         String stopMessage      = "Stopping the User Stalker service...";
         String stopNowMessage   = "Failed to cleanly stop User Stalker service; forcibly shutting it down...";
         String stopFinalMessage = "Forced shutdown of User Stalker service timed out after 5 seconds; terminating anyway...";

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

         LOGGER.info("Persisting state to file (\"" + PERSISTED_STATE_FILE + "\")...");
         try
         {
            FileOutputStream   fos = new FileOutputStream(PERSISTED_STATE_FILE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.siteInfoMap);
            oos.close();
            fos.close();
         }
         catch (IOException ex)
         {
            LOGGER.warn("Failed to persist state to file: " + ex + ".");
            ex.printStackTrace();
         }
      }
      else
      {
         String message = "The User Stalker service is not running.";
         LOGGER.info(message);
         this.BroadcastMessage(CHAT_MSG_PREFIX + " " + message);
      }
   }

   private void DoStart()
   {
      LOGGER.info("Attempting to start the User Stalker service...");

      if (this.IsRunning())
      {
         this.DoStop();
      }

      boolean somethingToDo = (((this.roomSO != null) && !this.sitesSO.isEmpty()) ||
                               ((this.roomSE != null) && !this.sitesSE.isEmpty()));
      if (somethingToDo)
      {
         if (this.siteInfoMap == null)
         {
            long startTime = Instant.now().minusSeconds(60).getEpochSecond();
            int sites      = (((this.sitesSO != null) ? this.sitesSO.size() : 0) +
                              ((this.sitesSE != null) ? this.sitesSE.size() : 0));

            LOGGER.info("Attempting to load persisted state from file (\"" + PERSISTED_STATE_FILE + "\")...");
            try
            {
               FileInputStream   fis = new FileInputStream(PERSISTED_STATE_FILE);
               ObjectInputStream ois = new ObjectInputStream(fis);

               @SuppressWarnings("unchecked")
               Map<String, StackExchangeSiteInfo> map = (Map<String, StackExchangeSiteInfo>)ois.readObject();
               this.siteInfoMap                       = map;

               ois.close();
            }
            catch (IOException | ClassNotFoundException ex)
            {
               LOGGER.warn("Failed to load persisted state from file: " + ex + ".");
               ex.printStackTrace();

               this.siteInfoMap = new HashMap<String, StackExchangeSiteInfo>(sites * 2);
            }

            if (this.sitesSO != null)
            {
               for (String site : this.sitesSO)
               {
                  this.siteInfoMap.putIfAbsent(site, new StackExchangeSiteInfo(startTime));
               }
            }
            if (this.sitesSE != null)
            {
               for (String site : this.sitesSE)
               {
                  this.siteInfoMap.putIfAbsent(site, new StackExchangeSiteInfo(startTime));
               }
            }
         }

         this.executor = Executors.newSingleThreadScheduledExecutor();
         this.executor.scheduleAtFixedRate(() -> { this.OnStalk(); },
                                           0,
                                           POLL_TIME_MINUTES,
                                           TimeUnit.MINUTES);
      }
      String message = "The User Stalker service (v" + UserStalker.VERSION + ") "
                     + (somethingToDo ? "has started."
                                      : "did not start because there is nothing to do.");
      LOGGER.info(message);
      this.BroadcastMessage(CHAT_MSG_PREFIX + " " + message);
   }

   private void DoLeave()
   {
      if (this.roomSE != null)
      {
         this.roomSE.leave();
      }

      if (this.roomSO != null)
      {
         this.roomSO.leave();
      }
   }

   private void DoReboot()
   {
      if (this.IsRunning())
      {
         this.DoStop();
      }

      LOGGER.info("Rebooting the bot...");
      this.DoLeave();
      System.exit(0);
   }

   private void DoUpgrade(Room room, long replyID, org.sobotics.chatexchange.chat.User sendingUser)
   {
      // Require that the message comes from a user whose chat user ID indicates that
      // they have admin privileges.
      if ((room != null) && (sendingUser != null))
      {
         String hostName = room.getHost().getName();
         long[] userIDs  = this.CHAT_ADMIN_USERIDS.get(hostName);
         if ((userIDs != null) &&
             (Arrays.stream(userIDs).anyMatch(id -> id == sendingUser.getId())))
         {
            if (this.IsRunning())
            {
               this.DoStop();
            }

            LOGGER.info("The bot is going down for an upgrade...");
            this.BroadcastMessage(CHAT_MSG_PREFIX + " " + "Going down for an upgrade; be back soon!");
            this.DoLeave();
            System.exit(42);
            return;
         }
      }

      String logMessage = "Unprivileged attempt to upgrade (Room: " + room.getRoomId() + " on " + room.getHost().getName();
      if (sendingUser != null)
      {
         logMessage += "; User: " + sendingUser.getId();
      }
      logMessage += ").";
      LOGGER.warn(logMessage);

      room.replyTo(replyID, "I'm sorry, Dave. I'm afraid I can't do that.");
   }


   private static boolean ContainsNonLatin(String string)
   {
      return string.codePoints().anyMatch(codepoint ->
      {
         Character.UnicodeScript script = Character.UnicodeScript.of(codepoint);
         return ((script != Character.UnicodeScript.LATIN ) &&
                 (script != Character.UnicodeScript.COMMON));
      });
   }

   private String CheckUser(User user)
   {
      String            name     = user.getDisplayName();
      String            location = user.getLocation();
      String            url      = user.getWebsiteUrl();
      String            about    = user.getAboutMe();
      ArrayList<String> reasons  = new ArrayList<String>(32);

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

         if (RegexManager.AnyMatches(name, this.regexes.KeywordSmokeyBlacklist))
         {
            reasons.add("username contains keyword on Smokey's blacklist");
         }

         if (name.contains(Integer.toString(Year.now().getValue())))
         {
            reasons.add("username contains current year");
         }
         if (name.contains(Integer.toString(Year.now().getValue() + 1)))
         {
            reasons.add("username contains next year");
         }

         if (RegexManager.AnyMatches(name, this.regexes.UrlPatterns))
         {
            reasons.add("username contains URL");
         }

         if (this.ContainsNonLatin(name))
         {
            reasons.add("username contains non-Latin character");
         }

         if ((url != null) && !url.isBlank())
         {
            String normalizedName = name.replaceAll("[^a-zA-Z]", "").toLowerCase();
            String normalizedUrl  = url .replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (normalizedName.toLowerCase().contains(normalizedUrl ) ||
                normalizedUrl .toLowerCase().contains(normalizedName))
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

         if (RegexManager.AnyMatches(url, this.regexes.UrlSmokeyBlacklist))
         {
            reasons.add("URL on Smokey's blacklist");
         }

         if (RegexManager.AnyMatches(url, this.regexes.KeywordSmokeyBlacklist))
         {
            reasons.add("URL contains keyword on Smokey's blacklist");
         }

         if (this.ContainsNonLatin(url))
         {
            reasons.add("URL contains non-Latin character");
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

         if (RegexManager.AnyMatches(location, this.regexes.KeywordSmokeyBlacklist))
         {
            reasons.add("location contains keyword on Smokey's blacklist");
         }

         if (RegexManager.AnyMatches(location, this.regexes.UrlPatterns))
         {
            reasons.add("location contains URL");
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

         if (RegexManager.AnyMatches(about, this.regexes.KeywordSmokeyBlacklist))
         {
            reasons.add("\"About Me\" contains keyword on Smokey's blacklist");
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
            reasons.add("\"About Me\" contains URL on blacklist");
         }

         if (RegexManager.AnyMatches(about, this.regexes.UrlSmokeyBlacklist))
         {
            reasons.add("\"About Me\" contains URL on Smokey's blacklist");
         }

         if (about.toLowerCase().contains("</a>"))
         {
            reasons.add("\"About Me\" contains a link");
         }
         else
         {
            // Even if there is not an actual link, check to see if there is a non-linked URL
            // (e.g., "example.com").
            if (RegexManager.AnyMatches(about, this.regexes.UrlPatterns))
            {
               reasons.add("location contains URL");
            }
         }

         if (this.ContainsNonLatin(about))
         {
            reasons.add("\"About Me\" contains non-Latin character");
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

      this.SendMessage(room, builder.toString());
   }

   private void ReportStatistics(Room room, List<String> sites)
   {
      boolean hasMultiple = (sites.size() > 1);
      String  message     = "";
      for (String site : sites)
      {
         StackExchangeSiteInfo siteInfo    = this.siteInfoMap.get(site);
         double                percentage  = (((double)siteInfo.SuspiciousUsers /
                                               (double)siteInfo.TotalUsers)
                                               * 100.0);
         if (message.isEmpty())
         {
            if (!hasMultiple)
            {
               message += CHAT_MSG_PREFIX;
            }
         }
         else
         {
            if (hasMultiple)
            {
               message += "\n";
            }
         }
         if (hasMultiple)
         {
            message += site + ":";
         }
         message += " Of "
                  + String.valueOf(siteInfo.TotalUsers)
                  + " user accounts created, "
                  + String.valueOf(siteInfo.SuspiciousUsers)
                  + " were suspicious ("
                  + String.format("%.2f", percentage)
                  + "% of total)";
      }
      this.SendMessage(room, message);
   }
}

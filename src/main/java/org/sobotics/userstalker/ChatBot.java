package org.sobotics.userstalker;


import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
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
+ "\u3000\u25CF \"alive\": Replies in the affirmative if the bot is up and running."
+ "\n"
+ "\u3000\u25CF \"check <user URL>\": Runs the pattern checks on the specified user account as if it were a newly-created account,"
+                                   " and then replies with the results."
+ "\n"
+ "\u3000\u25CF \"test <user URL>\": Same as \"check <user URL>\"."
+ "\n"
+ "\u3000\u25CF \"quota\": Replies with the currently remaining size of the API quota for the stalking service."
+ "\n"
+ "\u3000\u25CF \"list\": Replies with the list of Stack Exchange sites that are currently being stalked."
+ "\n"
+ "\u3000\u25CF \"add <sitename>\": Temporarily adds the specified SE site (short name) to the stalking list."
+                                 " (The modification will not persist across a reboot.)"
+ "\n"
+ "\u3000\u25CF \"remove <sitename>\": Temporarily removes the specified SE site (short name) from the stalking list."
+                                    " (The modification will not persist across a reboot.)"
+ "\n"
+ "\u3000\u25CF \"update\": Updates the pattern databases."
+ "\n"
+ "\u3000\u25CF \"stop\": Stops the stalking service. (The bot will remain in the room in order to respond to commands.)"
+ "\n"
+ "\u3000\u25CF \"start\": Starts the stalking service. (Intended to be used after a \"stop\" command.)"
+ "\n"
+ "\u3000\u25CF \"reboot\": Reboots the entire bot, as if the server had been rebooted."
+                         " (Any changes to pattern databases will be picked up at this time, and"
+                         " all temporary changes to the stalking lists will be lost.)"
+ "\n"
+ "\u3000\u25CF \"restart\": Same as \"reboot\"."
+ "\n\n"
+ "If you're still confused or need more help, you can ping Cody Gray (but he may not be as nice as me!)."
;

   private static final Logger LOGGER = LoggerFactory.getLogger(ChatBot.class);

   private StackExchangeClient      client;
   private List<String>             sitesSO;
   private List<String>             sitesSE;
   private Room                     roomSO;
   private Room                     roomSE;
   private RegexManager             regexes;
   private StackExchangeApiClient   seApi;
   private ScheduledExecutorService executor;
   private Instant                  previousTime;


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
      this.DoStart();
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
         else if (messageParts[1].equals("list"))
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
         else if (messageParts[1].equals("reboot") ||
                  messageParts[1].equals("restart"))
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
         this.OnStalk(this.roomSO, this.sitesSO);
      }

      if ((this.roomSE != null) && !this.sitesSE.isEmpty())
      {
         this.OnStalk(this.roomSE, this.sitesSE);
      }

      // BUG: This will almost certainly miss some users.
      previousTime = Instant.now();
   }

   private void OnStalk(Room room, List<String> sites)
   {
      boolean showSite = (sites.size() > 1);
      for (String site : sites)
      {
         LOGGER.info("Stalking " + site + " at " + Instant.now() + "...");

         JsonArray json = seApi.GetAllUsersAsJson(site,
                                                  this.previousTime.minusSeconds(1).getEpochSecond());
         if (json != null)
         {
            LOGGER.debug("JSON returned from SE API: " + json.toString());
            for (JsonElement element : json)
            {
               JsonObject object = element.getAsJsonObject();
               User       user   = new User(object);
               user.setSite(site);
               LOGGER.info("New user detected: \"" + user.getDisplayName() + "\" (" + user.getLink() + ").");
               String reason = CheckUser(user);
               if (!reason.isBlank())
               {
                  LOGGER.info("Detected user \"" + user + "\": " + reason + ".");
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
      // REVIEW: Does modifying the regexes create a race condition? Should the operator stop first?
      this.regexes.Reload();

      String chatMessage = CHAT_MSG_PREFIX + " The pattern databases have been successfully updated.";
      if (this.roomSO != null)
      {
         this.roomSO.send(chatMessage);
      }
      if (this.roomSE != null)
      {
         this.roomSE.send(chatMessage);
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
         LOGGER.error("Ignoring a \"list\" mention from unknown room: " + room);
         return;
      }

      String siteList = sites.stream().collect(Collectors.joining("`, `", "`", "`"));
      room.replyTo(replyID, "Stalking sites: " + sites);
   }

   private void DoListModify(Room room, long replyID, String command, String siteName)
   {
      // REVIEW: Does modifying the sites list create a race condition?
      if (command.equals("add"))
      {
         if (room == this.roomSO)
         {
            this.sitesSO.add(siteName);
            this.roomSO.send(CHAT_MSG_PREFIX + " Temporarily adding `" + siteName + "` to the list of sites being stalked.");
         }
         else if (room == this.roomSE)
         {
            this.sitesSE.add(siteName);
            this.roomSE.send(CHAT_MSG_PREFIX + " Temporarily adding `" + siteName + "` to the list of sites being stalked.");
         }
         else
         {
            LOGGER.error("Ignoring an \"add\" mention from unknown room: " + room);
         }
      }
      else if (command.equals("remove"))
      {
         if (room == this.roomSO)
         {
            this.sitesSO.remove(siteName);
            this.roomSO.send(CHAT_MSG_PREFIX + " Temporarily removing `" + siteName + "` from the list of sites being stalked.");
         }
         else if (room == this.roomSE)
         {
            this.sitesSE.remove(siteName);
            this.roomSE.send(CHAT_MSG_PREFIX + " Temporarily removing `" + siteName + "` from the list of sites being stalked.");
         }
         else
         {
            LOGGER.error("Ignoring a \"remove\" mention from unknown room: " + room);
         }
      }
      else
      {
         room.replyTo(replyID,
                      "The specified command (\"" + command + "\") was not recognized (must be either \"add\" or \"remove\".)");
      }
   }

   // TODO(low): Make this not stop if it is already stopped.
   private void DoStop()
   {
      String message     = "Stopping the user stalker service...";
      String chatMessage = CHAT_MSG_PREFIX + " " + message;

      LOGGER.info(message);

      ((this.roomSE != null) ? this.roomSE.send(chatMessage) : CompletableFuture.completedFuture(null)).thenRun(() ->
      {
         ((this.roomSO != null) ? this.roomSO.send(chatMessage) : CompletableFuture.completedFuture(null)).thenRun(() ->
         {
            this.executor.shutdown();
         });
      });
   }

   // TODO(low): Make this not start if it has already started.
   private void DoStart()
   {
      LOGGER.info("Attempting to start the user stalker service...");

      this.executor = Executors.newSingleThreadScheduledExecutor();
      if (((this.roomSO != null) && !this.sitesSO.isEmpty()) ||
          ((this.roomSE != null) && !this.sitesSE.isEmpty()))
      {
         this.previousTime = Instant.now().minusSeconds(60);
         this.executor.scheduleAtFixedRate(() -> { this.OnStalk(); },
                                           0,
                                           POLL_TIME_MINUTES,
                                           TimeUnit.MINUTES);

         String message     = "The user stalker service has started.";
         String chatMessage = CHAT_MSG_PREFIX + " " + message;
         ((this.roomSO != null) ? this.roomSO.send(chatMessage) : CompletableFuture.completedFuture(null)).thenRun(() ->
         {
            ((this.roomSE != null) ? this.roomSE.send(chatMessage) : CompletableFuture.completedFuture(null)).thenRun(() ->
            {
               LOGGER.info(message);
            });
         });
      }
   }

   private void DoReboot()
   {
      this.DoStop();

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
         if (RegexManager.AnyMatches(name, this.regexes.regexNameBlacklist))
         {
            reasons.add("username on blacklist");
         }

         if (RegexManager.AnyMatches(name, this.regexes.regexNameSmokeyBlacklist))
         {
            reasons.add("username on Smokey's blacklist");
         }

         if (RegexManager.AnyMatches(name, this.regexes.regexOffensiveHi))
         {
            reasons.add("username contains highly offensive pattern");
         }

         if (RegexManager.AnyMatches(name, this.regexes.regexOffensiveMd))
         {
            reasons.add("username contains mildly offensive pattern");
         }

         if (RegexManager.AnyMatches(name, this.regexes.regexOffensiveLo))
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
         if (RegexManager.AnyMatches(url, this.regexes.regexUrlBlacklist))
         {
            reasons.add("URL on blacklist");
         }
      }

      // Check the location.
      if ((location != null) && !location.isBlank())
      {
         if (RegexManager.AnyMatches(location, this.regexes.regexOffensiveHi))
         {
            reasons.add("location contains highly offensive pattern");
         }

         if (RegexManager.AnyMatches(location, this.regexes.regexOffensiveMd))
         {
            reasons.add("location contains mildly offensive pattern");
         }

         if (RegexManager.AnyMatches(location, this.regexes.regexOffensiveLo))
         {
            reasons.add("location contains possibly offensive pattern");
         }
      }

      // Check the "About Me".
      if ((about != null) && !about.isBlank())
      {
         if (RegexManager.AnyMatches(about, this.regexes.regexAboutBlacklist))
         {
            reasons.add("\"About Me\" contains blacklisted pattern");
         }

         if (RegexManager.AnyMatches(about, this.regexes.regexOffensiveHi))
         {
            reasons.add("\"About Me\" contains highly offensive pattern");
         }

         if (RegexManager.AnyMatches(about, this.regexes.regexOffensiveMd))
         {
            reasons.add("\"About Me\" contains mildly offensive pattern");
         }

         if (RegexManager.AnyMatches(about, this.regexes.regexOffensiveLo))
         {
            reasons.add("\"About Me\" contains possibly offensive pattern");
         }

         if (RegexManager.AnyMatches(about, this.regexes.regexPhonePatterns))
         {
            reasons.add("\"About Me\" contains phone number");
         }

         if (RegexManager.AnyMatches(about, this.regexes.regexEmailPatterns))
         {
            reasons.add("\"About Me\" contains email");
         }

         if (RegexManager.AnyMatches(about, this.regexes.regexUrlBlacklist))
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

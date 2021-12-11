package org.sobotics.userstalker;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.lang.Throwable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import info.debatty.java.stringsimilarity.RatcliffObershelp;

import org.sobotics.chatexchange.chat.ChatHost;
import org.sobotics.chatexchange.chat.Message;
import org.sobotics.chatexchange.chat.Room;
import org.sobotics.chatexchange.chat.StackExchangeClient;
import org.sobotics.chatexchange.chat.event.EventType;
import org.sobotics.chatexchange.chat.event.PingMessageEvent;
import org.sobotics.chatexchange.chat.event.UserEnteredEvent;


public class ChatBot implements AutoCloseable
{
   private static final int                 POLL_TIME_MINUTES        = 5;
   private static final int                 OFFSET_TIME_MINUTES      = 2;
   private static final Map<String, long[]> CHAT_ADMIN_USERIDS;
      static
      {
         HashMap<String, long[]> map = new HashMap<String, long[]>();
         map.put("stackoverflow.com", new long[] { 366904, });
         map.put("stackexchange.com", new long[] {   7959, });
         CHAT_ADMIN_USERIDS = Collections.unmodifiableMap(map);
      }
   private static final String              PERSISTED_STATE_FILE     = "savedState.bin";
   private static final String              SO_CHINESE_SPAMMERS_FILE = "SO_Chinese_Profile_Spammers.txt";
   private static final String              BOT_URL                  = "https://git.io/v5CGT";
   private static final String              CHAT_MSG_PREFIX          = "[ [User Stalker](" + BOT_URL + ") ]";
   private static final String              UNKNOWN_CMD_MSG          = "You talkin\u2019 to me? Psst\u2026ping me and say \"help\".";
   private static final String              HELP_CMD_MSG             =
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
   private Room                               roomSO;
   private Room                               roomSE;
   private List<String>                       sites;
   private List<String>                       nonEnglishSites;
   private RegexManager                       regexes;
   private HomoglyphManager                   homoglyphs;
   private StackExchangeApiClient             seApi;
   private ScheduledExecutorService           executor;
   private Map<String, StackExchangeSiteInfo> siteInfoMap;
   private boolean                            stalkSE = true;

   // HACK: This is merely to reduce the noise in the chat room logs caused by an extremely
   //       persistent wave of Chinese profile spammers on Stack Overflow, which follow an
   //       extremely predictable pattern. Remove this hack once this spammer is dealt with
   //       (e.g., by a system-level block).
   private FileOutputStream                   fosForSOChineseSpammers = null;
   private OutputStreamWriter                 oswForSOChineseSpammers = null;


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

   @Override
   public void close() throws IOException
   {
      this.oswForSOChineseSpammers.close();
      this.fosForSOChineseSpammers.close();
   }


   public void JoinSO(int roomID)
   {
      if (this.roomSO == null)
      {
         this.roomSO = this.JoinRoom(ChatHost.STACK_OVERFLOW, roomID, true);
      }
      else
      {
         throw new IllegalStateException("The chat bot has already joined a room on Stack Overflow.");
      }
   }

   public void JoinSE(int roomID)
   {
      if (this.roomSE == null)
      {
         this.roomSE = this.JoinRoom(ChatHost.STACK_EXCHANGE, roomID, true);
      }
      else
      {
         throw new IllegalStateException("The chat bot has already joined a room on Stack Exchange.");
      }
   }

   private Room JoinRoom(ChatHost host, int roomID, boolean addListeners)
   {
      LOGGER.info("Attempting to join chat room " + roomID + " on " + host.getName() + "...");
      Room room = this.client.joinRoom(host, roomID);
      if (addListeners)
      {
         room.addEventListener(EventType.USER_ENTERED  , event -> this.OnUserEntered(room, event));
         room.addEventListener(EventType.USER_MENTIONED, event -> this.OnMentioned  (room, event, false));
       //room.addEventListener(EventType.MESSAGE_REPLY , event -> this.OnMentioned  (room, event, true ));
      }
      return room;
   }


   public void Run(List<String> seSites, List<String> nonEnglishSites)
   {
      // Load the regular expressions.
      this.regexes = new RegexManager();

      // Load the homoglyphs.
      this.homoglyphs = new HomoglyphManager();

      // Initialize the Stack Exchange API client.
      this.seApi = new StackExchangeApiClient(this::OnQuotaRollover);

      // Create the site lists.
      this.sites           = new ArrayList<String>();
      this.nonEnglishSites = new ArrayList<String>();
      if (this.roomSO != null)
      {
         this.sites.add("stackoverflow");
      }
      if (this.roomSE != null)
      {
         this.sites.addAll(seSites);
      }
      if (nonEnglishSites != null)
      {
         this.nonEnglishSites.addAll(nonEnglishSites);
      }

      // Attempt to open the file into which we log the Chinese profile spammers on Stack Overflow.
      // This file is opened for appending, so that we don't overwrite its previous contents.
      try
      {
         fosForSOChineseSpammers = new FileOutputStream(SO_CHINESE_SPAMMERS_FILE, true);
         oswForSOChineseSpammers = new OutputStreamWriter(fosForSOChineseSpammers,
                                                          StandardCharsets.UTF_8);
      }
      catch (IOException ex)
      {
         LOGGER.warn("Failed to open Stack Overflow Chinese profile spammer log file: " + ex);
      }

      // Start the stalking service.
      this.DoStart();
   }


   private boolean IsRunning()
   {
      return (this.executor != null);
   }


   // TODO(low): Review the async code below for both correctness and efficiency!

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

   private CompletionStage<Long> SendMessage(Room room, String message)
   {
      if (room != null)
      {
         return room.send(message)
                    .thenApply(CompletableFuture::completedFuture)
                    .exceptionally(t -> SendMessage_Retry(room, message, t, 0))
                    .thenCompose(Function.identity());
      }
      else
      {
         return CompletableFuture.completedFuture(null);
      }
   }


   private void BroadcastMessage(String message)
   {
      if (this.roomSO != null)  { this.SendMessage(this.roomSO, message); }
      if (this.roomSE != null)  { this.SendMessage(this.roomSE, message); }
   }

   private void BroadcastMessageAndWait(String message)
   {
      CompletableFuture.allOf(this.SendMessage(this.roomSO, message).toCompletableFuture(),
                              this.SendMessage(this.roomSE, message).toCompletableFuture())
                       .join();
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

      // Display per-site statistical information on users.
      if ((this.sites != null) && !this.sites.isEmpty())
      {
         if (this.roomSO != null)
         {
            this.ReportStatistics(this.roomSO, Collections.singletonList("stackoverflow"));
         }
         if (this.roomSE != null)
         {
            this.ReportStatistics(this.roomSE, this.sites);
         }
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
      Message message        = event.getMessage();
      long    replyID        = message.getId();
      String  messageString  = message.getPlainContent();
      String  messageParts[] = messageString.trim().toLowerCase().split(" ");

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
      LOGGER.info("Starting periodic stalking (stalkSE: " + String.valueOf(this.stalkSE) + ")...");

      if ((this.roomSO != null) && this.sites.contains("stackoverflow"))
      {
         this.DoStalk(this.roomSO, Collections.singletonList("stackoverflow"), 100);
      }

      if (this.roomSE != null)
      {
         if (this.stalkSE)
         {
            ArrayList<String> seSites = new ArrayList<String>(this.sites);
            seSites.remove("stackoverflow");

            this.DoStalk(this.roomSE, seSites, (seSites.size() * 20));
         }

         this.stalkSE = !this.stalkSE;
      }
   }


   private void DoStalk(Room room, List<String> sites, int approximateUserCount)
   {
      // Get all potentially suspicious users from all sites in the list of sites to stalk.
      ArrayList<SuspiciousUser> suspiciousUsers = new ArrayList<SuspiciousUser>(approximateUserCount);
      for (String site : sites)
      {
         StackExchangeSiteInfo siteInfo  = this.siteInfoMap.get(site);
         long                  oldTime   = siteInfo.ToDate;
         long                  startTime = Instant.now().minusSeconds(OFFSET_TIME_MINUTES * 60).getEpochSecond();
         siteInfo.FromDate               = oldTime;
         siteInfo.ToDate                 = startTime;

         LOGGER.info("Stalking " + site + " at " + siteInfo.ToDate + " (last was at " + siteInfo.FromDate + ")...");

         List<User> users = this.seApi.GetAllUsers(site, siteInfo);
         if (users != null)
         {
            suspiciousUsers.ensureCapacity(suspiciousUsers.size() + users.size());
            for (User user : users)
            {
               String reason = this.CheckUser(site, user);
               if (!reason.isBlank())
               {
                  LOGGER.info("Potentially suspicious user detected: " + user + " because: { " + reason + " }.");
                  suspiciousUsers.add(new SuspiciousUser(user, reason));
               }
            }
         }
         else
         {
            LOGGER.warn("Failed to retrieve new user information from SE API when stalking site \""
                      + site
                      + "\"; skipping site this time.");
            siteInfo.ToDate = oldTime;
         }
      }

      // If no potentially suspicious users were found, bail out now.
      if (suspiciousUsers.isEmpty())
      {
         return;
      }

      // Filter the list of suspicious users, excluding those users who have accounts that are in
      // good standing on other Stack Exchange network sites to help reduce false-positives.
      // (Many of our criteria for what makes a user "suspicious", such as having links in their
      // profile, are excellent heuristics for brand-new users, but equally poor heuristics for
      // established users.)
      ArrayList<SuspiciousUser>               finalSuspiciousUsers = new ArrayList<SuspiciousUser>(suspiciousUsers.size());
      Map<Integer, ArrayList<NetworkAccount>> accountsMap          = this.seApi.GetAllNetworkAccounts(suspiciousUsers);
      if (accountsMap != null)
      {
         for (SuspiciousUser suspiciousUser : suspiciousUsers)
         {
            boolean isUserSuspicious = true;  // assume user is suspicious absent evidence to the contrary

            List<NetworkAccount> accounts = accountsMap.get(suspiciousUser.user.getNetworkAccountID());
            if (accounts != null)
            {
               // If this user has a network account, then we need to check all of their accounts
               // on other Stack Exchange network sites to see if any of those accounts are in
               // good standing. If so, this frees them from suspicion.
               int     userNetworkAccountID = suspiciousUser.user.getNetworkAccountID().intValue();

               long    stalkDate            = this.siteInfoMap.get(suspiciousUser.user.getSite()).ToDate;
               Instant stalkInstant         = Instant.ofEpochSecond(stalkDate);
               Instant oldEnoughInstant     = stalkInstant.minus(7, ChronoUnit.DAYS);
               long    oldEnoughDate        = oldEnoughInstant.getEpochSecond();
               for (NetworkAccount account : accounts)
               {
                  Integer networkAccountID = account.getNetworkAccountID();
                  String  userType         = account.getUserType();
                  Integer reputation       = account.getReputation();
                  Long    creationDate     = account.getCreationDate();
                  Integer postCount        = account.getPostCount();
                  if (((networkAccountID != null) && (networkAccountID.intValue() == userNetworkAccountID)) &&
                      ((userType         != null)))
                  {
                     boolean isModerator  = (userType.equals("moderator"));
                     boolean isRegistered = (userType.equals("registered") ||
                                             userType.equals("team_admin"));
                     if (isModerator || (isRegistered && ((reputation   != null) && (reputation  .intValue () >= 30))
                                                      && ((creationDate != null) && (creationDate.longValue() <  oldEnoughDate))
                                                      && ((postCount    != null) && (postCount   .intValue () >= 1))))
                     {
                        isUserSuspicious = false;
                        LOGGER.info("Due to account in good standing on "
                                  + account.getSiteName()
                                  + " (" + account.getLink() + "), "
                                  + "no longer suspecting the user: " + suspiciousUser.user + ".");
                        break;
                     }
                  }
               }
            }

            if (isUserSuspicious)
            {
               // Either we could not get any information about the user's network accounts, or
               // the information that we did retrieve was unable to free them from suspicion.
               finalSuspiciousUsers.add(suspiciousUser);
            }
         }
      }
      else
      {
         LOGGER.warn("Failed to retrieve network account information for suspicious users from SE API;"
                   + " reporting all potentially suspicious as suspicious.");
         finalSuspiciousUsers.addAll(suspiciousUsers);
      }

      // Report all of the actually suspicious users.
      boolean showSiteName = (sites.size() > 1);
      for (SuspiciousUser suspiciousUser : finalSuspiciousUsers)
      {
         this.siteInfoMap.get(suspiciousUser.user.getSite()).SuspiciousUsers += 1;
         this.ReportUser(room, suspiciousUser.user, suspiciousUser.reason, showSiteName);
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
      room.replyTo(replyID, "Yep, I'm alive! (This is version " + UserStalker.VERSION + ".)");
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

      this.regexes   .Reload();
      this.homoglyphs.Reload();

      this.BroadcastMessage(CHAT_MSG_PREFIX + " The pattern databases have been successfully updated.");

      if (wasRunning)
      {
         this.DoStart();
      }
   }

   private void DoList(Room room, long replyID)
   {
      String siteList = sites.stream()
                             .filter(i -> ((room != this.roomSO) | i.equals("stackoverflow")))
                             .collect(Collectors.joining("`, `", "`", "`"));
      room.replyTo(replyID, "Stalking sites: " + sites);
   }

   private void DoListModify(Room room, long replyID, String command, String siteName)
   {
      boolean add    = command.equals("add");
      boolean remove = command.equals("remove");
      if (add)
      {
         if (this.sites.contains(siteName))
         {
            room.replyTo(replyID,
                         "The site `" + siteName + "` is already on the list of sites being stalked, "
                       + "so it makes no sense to add it.");
            return;
         }
      }
      else if (remove)
      {
         if (!this.sites.contains(siteName))
         {
            room.replyTo(replyID,
                         "The site `" + siteName + "` is not currently on the list of sites being stalked, "
                       + "so it makes no sense to remove it.");
            return;
         }
      }
      else
      {
         room.replyTo(replyID,
                      "The specified command (\"" + command + "\") was not recognized (must be either \"add\" or \"remove\".)");
         return;
      }


      LOGGER.info("Beginning to modify (" + command + ") the list of sites being stalked.");

      boolean wasRunning = this.IsRunning();
      if (wasRunning)
      {
         this.DoStop();
      }

      if (add)  { this.sites.add   (siteName); }
      else      { this.sites.remove(siteName); }

      this.BroadcastMessage(add ? CHAT_MSG_PREFIX + " Temporarily adding `"   + siteName + "` to the list of sites being stalked."
                                : CHAT_MSG_PREFIX + " Temporarily removing `" + siteName + "` from the list of sites being stalked.");

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
         try (
              FileOutputStream   fos = new FileOutputStream(PERSISTED_STATE_FILE);
              ObjectOutputStream oos = new ObjectOutputStream(fos)
             )
         {
            oos.writeObject(this.siteInfoMap);
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

      boolean somethingToDo = (!this.sites.isEmpty() && ((this.roomSO != null) || (this.roomSE != null)));
      if (somethingToDo)
      {
         if (this.siteInfoMap == null)
         {
            long startTime = Instant.now().minusSeconds(OFFSET_TIME_MINUTES * 60).getEpochSecond();

            LOGGER.info("Attempting to load persisted state from file (\"" + PERSISTED_STATE_FILE + "\")...");
            try (
                 FileInputStream   fis = new FileInputStream(PERSISTED_STATE_FILE);
                 ObjectInputStream ois = new ObjectInputStream(fis);
                )
            {
               @SuppressWarnings("unchecked")
               Map<String, StackExchangeSiteInfo> map = (Map<String, StackExchangeSiteInfo>)ois.readObject();
               this.siteInfoMap                       = map;
            }
            catch (IOException | ClassNotFoundException ex)
            {
               LOGGER.warn("Failed to load persisted state from file: " + ex + ".");
               ex.printStackTrace();

               this.siteInfoMap = new HashMap<String, StackExchangeSiteInfo>((this.sites.size() * 3) / 2);
            }

            for (String site : this.sites)
            {
               this.siteInfoMap.putIfAbsent(site, new StackExchangeSiteInfo(startTime));
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
            this.BroadcastMessageAndWait(CHAT_MSG_PREFIX + " " + "Going down for an upgrade; be back soon!");
            this.DoLeave();
            System.exit(42);
            return;
         }
      }

      String logMessage = "Unprivileged attempt to upgrade"
                        + " (Room: " + room.getRoomId()
                        + " on " + room.getHost().getName();
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

   private String CheckUser(String site, User user)
   {
      Long              suspendedUntil = user.getTimedPenaltyDate();
      String            name           = user.getDisplayName();
      String            avatar         = user.getProfileImage();
      String            location       = user.getLocation();
      String            url            = user.getWebsiteUrl();
      String            aboutMe        = user.getAboutMe();
      ArrayList<String> reasons        = new ArrayList<String>(34);

      boolean isSuspended   = ((suspendedUntil != null)                       );
      boolean hasName       = ((name           != null) && !name    .isBlank());
      boolean hasAvatar     = ((avatar         != null) && !avatar  .isBlank());
      boolean hasLocation   = ((location       != null) && !location.isBlank());
      boolean hasURL        = ((url            != null) && !url     .isBlank());
      boolean hasAboutMe    = ((aboutMe        != null) && !aboutMe .isBlank());
      boolean hasAnyContent = (hasLocation || hasURL || hasAboutMe);

      // Check for an active suspension.
      if (isSuspended)
      {
         reasons.add("suspended until "
                   + StackExchangeApiClient.FormatDateTimeToNearestMinute(suspendedUntil));
      }

      // Check the avatar.
      if (hasAvatar)
      {
         if (RegexManager.AnyMatches(avatar, this.regexes.AvatarBlacklist))
         {
            reasons.add("avatar on blacklist");
         }
      }

      // Check the display name.
      if (hasName)
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

         if (!this.nonEnglishSites.contains(site) && this.ContainsNonLatin(name))
         {
            // To reduce the number of false-positives, this reason is only triggered if the user
            // profile has content in any other fields. In truth, this is the only case where it
            // would be justifiable for a moderator to take action on such a match (e.g., by
            // destroying the user account), so there's no need to even report it if there is
            // not sufficient information to motivate action.
            if (hasAnyContent)
            {
               reasons.add("username contains non-Latin character");
            }
         }
      }

      // Check the URL.
      if (hasURL)
      {
         if (RegexManager.AnyMatches(url, this.regexes.UrlBlacklist))
         {
            reasons.add("URL on blacklist");
         }

         if (RegexManager.AnyMatches(url, this.regexes.UrlSmokeyBlacklist))
         {
            reasons.add("URL on Smokey's blacklist");
         }

         if (hasName)
         {
            String normalizedName = name.replaceAll("[^a-zA-Z]", "").toLowerCase();
            String normalizedUrl  = url .replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (normalizedName.contains(normalizedUrl ) ||
                normalizedUrl .contains(normalizedName))
            {
               reasons.add("URL similar to username");
            }

            String canonicalizedName = this.homoglyphs.Canonicalize(name.replaceAll(" ", ""));
            String canonicalizedUrl  = this.homoglyphs.Canonicalize(url .replaceAll(" ", ""));
            double similarity        = new RatcliffObershelp().similarity(canonicalizedName,
                                                                          canonicalizedUrl);
            if (similarity >= 0.3)
            {
               reasons.add("URL similar to username (R/O: "
                         + String.format("%.2f", similarity)
                         + ")");
            }
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
      if (hasLocation)
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
      if (hasAboutMe)
      {
         if (RegexManager.AnyMatches(aboutMe, this.regexes.AboutBlacklist))
         {
            reasons.add("\"About Me\" contains blacklisted pattern");
         }

         if (RegexManager.AnyMatches(aboutMe, this.regexes.OffensiveHi))
         {
            reasons.add("\"About Me\" contains highly offensive pattern");
         }

         if (RegexManager.AnyMatches(aboutMe, this.regexes.OffensiveMd))
         {
            reasons.add("\"About Me\" contains mildly offensive pattern");
         }

         if (RegexManager.AnyMatches(aboutMe, this.regexes.OffensiveLo))
         {
            reasons.add("\"About Me\" contains possibly offensive pattern");
         }

         if (RegexManager.AnyMatches(aboutMe, this.regexes.KeywordSmokeyBlacklist))
         {
            reasons.add("\"About Me\" contains keyword on Smokey's blacklist");
         }

         if (RegexManager.AnyMatches(aboutMe, this.regexes.PhonePatterns))
         {
            reasons.add("\"About Me\" contains phone number");
         }

         if (RegexManager.AnyMatches(aboutMe, this.regexes.EmailPatterns))
         {
            reasons.add("\"About Me\" contains email");
         }

         if (RegexManager.AnyMatches(aboutMe, this.regexes.UrlBlacklist))
         {
            reasons.add("\"About Me\" contains URL on blacklist");
         }

         if (RegexManager.AnyMatches(aboutMe, this.regexes.UrlSmokeyBlacklist))
         {
            reasons.add("\"About Me\" contains URL on Smokey's blacklist");
         }

         if (aboutMe.toLowerCase().contains("</a>"))
         {
            reasons.add("\"About Me\" contains a link");
         }
         else
         {
            // Even if there is not an actual link, check to see if there is a non-linked URL
            // (e.g., "example.com").
            if (RegexManager.AnyMatches(aboutMe, this.regexes.UrlPatterns))
            {
               reasons.add("\"About Me\" contains URL");
            }
         }

         if (this.ContainsNonLatin(aboutMe))
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
         String reasons = CheckUser(site, user);
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

      StringBuilder str = new StringBuilder();
      str.append(CHAT_MSG_PREFIX);
      str.append(" [");
      if (isSuspended)
      {
         str.append("*");
      }
      str.append(user.getDisplayName().trim());
      if (isSuspended)
      {
         str.append("*");
      }
      str.append("](");
      str.append(user.getLink());
      str.append("?tab=profile \"");
      str.append(user.getDisplayName());
      str.append("\") ");
      if (showSite)
      {
         str.append("on **`");
         str.append(user.getSite());
         str.append("`** ");
      }
      str.append("(");
      str.append(reason);
      str.append(")");

      if ((this.oswForSOChineseSpammers != null)   &&
          (room == this.roomSO)                    &&
          (user.getSite().equals("stackoverflow")) &&
          (user.getProfileImage() != null)         &&
          user.getProfileImage().contains("gravatar.com/avatar/573ab1b5acb73217ad973eb9efa0e026"))
      {
         str.append("\n");

         try
         {
            this.oswForSOChineseSpammers.write(str.toString());
         }
         catch (IOException ex)
         {
            LOGGER.error("Failed to append to Stack Overflow Chinese profile spammer log file: " + ex);
         }
      }
      else
      {
         this.SendMessage(room, str.toString());
      }
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
            if (hasMultiple)
            {
               message += "STATISTICS:\n";
            }
            else
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

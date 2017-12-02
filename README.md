# User Stalker 

## What exactly is this?

We are scanning every user account upon creation (on a few SE sites) and report those users who fail a few checks in a chat room. 

## What data are we using?

1. [The Stack Exchange API](http://api.stackexchange.com).
2. Blacklisted usernames from the [SmokeDetector project](https://charcoal-se.org/#whats-smokey). 
3. Offensive Regular Expressions from the [Heat Detector project](https://stackapps.com/questions/7001/heat-detector-analysing-comments-to-find-heat). 

## What all are we checking?

1. If there is a link in their about me text. 
2. If the website link is similar to their display name. 
3. If the username or profile text fails the Heat Detector Offensive Regex. 
4. If the username is blacklisted on the SmokeDetector username blacklist. 
5. If the user is suspended on the site upon creation. 

## What do we do when a user is detected as a bad user? 

***Nothing***

We don't do anything when a user is detected. We are just stalkers. 

According to the [Stack Exchange policy](https://meta.stackexchange.com/questions/297993/what-is-the-policy-on-destroying-users-with-very-spammy-profiles-but-have-not-po):

> We actually don't encourage moderators to seek out these kinds of users and destroy them because it's usually a waste of time. There are lots of spam users that just create profiles and then never do anything, and actively seeking them out to destroy them rarely achieves anything. 

and 

> So if you really feel like spending your time hunting these down, go for it, but by all means you should be absolutely certain it's an attempt at spam

Moderators can destroy these accounts, if and only if they are completely sure that the profile is created only for spam. 

When we detect trends like the ["The Great Super User Spam Invasion of 2017"](https://meta.stackexchange.com/a/238975) on [16th October 2017](https://chat.stackexchange.com/transcript/59667/2017/10/16), we usually try to figure out what is happening and destroy the spam profiles. (But it is left to the site moderators discretion. We just alert the mods, if the need be. In that particular case, Community Managers were informed as the number of accounts involved was very high.) 


## Whom would it help?

Mostly the diamond moderators.

## How do I track a site? 

Use the `add` command and pass the site name and the frequency of tracking needed as parameters. Frequency can be `fast` or `slow`. `fast` calls the API every  minute, `slow` calls it every 5 minutes. 

For example, if you need to track on site Drupal Answers, every 5 minutes, use: 

    @Jam add drupal slow

## What other commands are there? 

 - `quota` - Returns the API quota available.
 - `alive` - Returns a test message if the bot is responding to commands. 
 - `tracked` - Returns the list of sites which are being tracked. 
 

# Join us in [MOBotics](https://chat.stackexchange.com/rooms/59667/mobotics) 

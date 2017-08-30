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

We don't do anything when a user is detected. We are just stalkers. The Stack Exchange policy clearly indicates that [**we should not be deleting users preemptively because of a spammy user profile**](https://meta.stackexchange.com/questions/297993/what-is-the-policy-on-destroying-users-with-very-spammy-profiles-but-have-not-po).

## Whom would it help?

Mostly the diamond moderators.

## How do I track a site? 

Use the `add` command and pass the site name and the frequency of tracking needed as parameters. Frequency can be `fast` or `slow`. `fast` calls the API every  minute, `slow` calls it every 5 minutes. 

For example, if you need to track on site Drupal Answers, every 5 minutes, use: 

    add drupal slow

## What other commands are there? 

 - `quota` - Returns the API quota available.
 - `alive` - Returns a test message if the bot is responding to commands. 
 - `tracked` - Returns the list of sites which are being tracked. 
 

# Join us in [MOBotics](https://chat.stackexchange.com/rooms/59667/mobotics) 
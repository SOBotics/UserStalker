# User Stalker 

## What exactly is this?

Certain times, it's useful to check if a particular user would soon do some illegal activity so that we can store some data about them. 

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

# Join us in [MOBotics](https://chat.stackexchange.com/rooms/59667/mobotics) 
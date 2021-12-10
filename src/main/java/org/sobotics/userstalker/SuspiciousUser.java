package org.sobotics.userstalker;


public class SuspiciousUser
{
   public User   user;
   public String reason;


   public SuspiciousUser(User user, String reason)
   {
      this.user   = user;
      this.reason = reason;
   }
}

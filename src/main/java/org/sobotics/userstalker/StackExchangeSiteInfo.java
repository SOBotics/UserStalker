package org.sobotics.userstalker;


import java.io.Serializable;


public class StackExchangeSiteInfo implements Serializable
{
   public long FromDate;
   public long ToDate;

   public int TotalUsers;
   public int SuspiciousUsers;


   public StackExchangeSiteInfo(long fromDate)
   {
      this.FromDate = fromDate;
      this.ToDate   = fromDate;

      this.ResetUsers();
   }


   public void ResetUsers()
   {
      this.TotalUsers      = 0;
      this.SuspiciousUsers = 0;
   }
}

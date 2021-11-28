package org.sobotics.userstalker;


public class StackExchangeSiteInfo
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

package org.sobotics.userstalker;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HomoglyphManager
{
   private static final String HOMOGLYPH_CHAR_CODES_URL = "https://raw.githubusercontent.com/codebox/homoglyph/master/raw_data/char_codes.txt";

   private static final Logger LOGGER = LoggerFactory.getLogger(HomoglyphManager.class);

   private HashMap<Integer, HashSet<Integer>> homoglyphs;


   public HomoglyphManager()
   {
      this.Reload();
   }


   public void Reload()
   {
      LOGGER.info("Initializing and (re-)loading homoglyphs...");

      this.homoglyphs = new HashMap<Integer, HashSet<Integer>>();
      try
      {
         URL data = new URL(HOMOGLYPH_CHAR_CODES_URL);
         try (
              InputStreamReader isr = new InputStreamReader(data.openStream(),
                                                            StandardCharsets.UTF_8);
              BufferedReader    br  = new BufferedReader(isr);
             )
         {
            String line;
            while ((line = br.readLine()) != null)
            {
               line = line.trim();

               if (line.startsWith("#") || (line.length() == 0))
               {
                  continue;
               }

               String           items[] = line.split(",");
               int              cItems  = items.length;
               HashSet<Integer> set     = new HashSet<Integer>(cItems);
               Integer          key     = null;
               for (int i = 0; i < cItems; ++i)
               {
                  try
                  {
                     int item = Integer.parseInt(items[i], 16);
                     set.add(item);
                     if ((i == 0) && (key == null))
                     {
                        key = item;
                     }
                  }
                  catch (NumberFormatException ex)
                  {
                     LOGGER.warn("When loading homoglyphs from <" + HOMOGLYPH_CHAR_CODES_URL + ">, "
                               + "the line containing \"" + line + "\" "
                               + "contained a badly-formatted item, which will be ignored.");
                  }
               }
               if (key != null)
               {
                  this.homoglyphs.put(key, set);
               }
            }
         }
      }
      catch (Exception ex)
      {
         this.homoglyphs = null;

         LOGGER.warn("Failed to load homoglyphs from <" + HOMOGLYPH_CHAR_CODES_URL + ">; "
                   + "continuing without support for them.");
         ex.printStackTrace();
      }
   }


   public String Canonicalize(String string)
   {
      if (this.homoglyphs != null)
      {
         List<Integer> codepoints = this.CodePointsFromString(string);
         StringBuilder sb         = new StringBuilder(codepoints.size());
         for (Integer codepoint : codepoints)
         {
            int canonicalCodepoint = codepoint;
            int charLower          = Character.toLowerCase(codepoint);
            int charUpper          = Character.toUpperCase(codepoint);
            for (Map.Entry<Integer, HashSet<Integer>> entry : this.homoglyphs.entrySet())
            {
               if ((entry.getValue().contains(charLower)) ||
                   (entry.getValue().contains(charUpper)))
               {
                  canonicalCodepoint = entry.getKey();
                  break;
               }
            }
            sb.appendCodePoint(canonicalCodepoint);
         }
         return sb.toString();
      }
      else
      {
         return string;
      }
   }


   private static List<Integer> CodePointsFromString(String string)
   {
      ArrayList<Integer> codepointList = new ArrayList<Integer>();
      int                codepoint;
      for (int offset = 0; offset < string.length(); )
      {
         codepoint = string.codePointAt(offset);
         codepointList.add(codepoint);
         offset += Character.charCount(codepoint);
      }
      return codepointList;
   }
}

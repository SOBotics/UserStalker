package org.sobotics.userstalker;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RegexManager
{
   private static final String  OFFENSIVE_REGEX_HI_URL         = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_high_score.txt";
   private static final String  OFFENSIVE_REGEX_MD_URL         = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_medium_score.txt";
   private static final String  OFFENSIVE_REGEX_LO_URL         = "https://raw.githubusercontent.com/SOBotics/SOCVFinder/master/SOCVDBService/ini/regex_low_score.txt";
   private static final String  SMOKEY_NAME_REGEX_URL          = "https://raw.githubusercontent.com/Charcoal-SE/SmokeDetector/master/blacklisted_usernames.txt";
   private static final String  SMOKEY_URL_REGEX_URL           = "https://raw.githubusercontent.com/Charcoal-SE/SmokeDetector/master/blacklisted_websites.txt";
   private static final String  SMOKEY_KEYWORD_REGEX_URL       = "https://raw.githubusercontent.com/Charcoal-SE/SmokeDetector/master/bad_keywords.txt";
   private static final String  SMOKEY_KEYWORD_REGEX_WATCH_URL = "https://raw.githubusercontent.com/Charcoal-SE/SmokeDetector/master/watched_keywords.txt";
   private static final String  INTERNAL_NAME_REGEX_URL        = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/DisplayNameBlacklist.txt";
   private static final String  INTERNAL_ABOUT_REGEX_URL       = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/AboutMeBlacklist.txt";
   private static final String  INTERNAL_URL_REGEX_URL         = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/UrlBlacklist.txt";
   private static final String  INTERNAL_AVATAR_REGEX_URL      = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/AvatarBlacklist.txt";
   private static final String  INTERNAL_PHONE_REGEX_URL       = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/PhoneNumber.txt";
   private static final String  INTERNAL_EMAIL_REGEX_URL       = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/EmailAddress.txt";
   private static final String  INTERNAL_URL_PATTERN_REGEX_URL = "https://raw.githubusercontent.com/SOBotics/UserStalker/master/patterns/Url.txt";
   private static final Pattern REGEX_COMMENT_PATTERN          = Pattern.compile("\\(\\?#.*\\)"                        , Pattern.CASE_INSENSITIVE);
   private static final Pattern REGEX_POSITIVE_LOOKBEHIND_STAR = Pattern.compile("(\\(\\?\\<\\=.*?)(?:\\*{1,2})(.*\\))", Pattern.CASE_INSENSITIVE);
   private static final Pattern REGEX_POSITIVE_LOOKBEHIND_PLUS = Pattern.compile("(\\(\\?\\<\\=.*?)(?:\\+{1,2})(.*\\))", Pattern.CASE_INSENSITIVE);

   private static final Logger LOGGER = LoggerFactory.getLogger(RegexManager.class);

   public List<Pattern> OffensiveHi;
   public List<Pattern> OffensiveMd;
   public List<Pattern> OffensiveLo;
   public List<Pattern> NameSmokeyBlacklist;
   public List<Pattern> UrlSmokeyBlacklist;
   public List<Pattern> KeywordSmokeyBlacklist;
   public List<Pattern> KeywordSmokeyWatchlist;
   public List<Pattern> NameBlacklist;
   public List<Pattern> AboutBlacklist;
   public List<Pattern> UrlBlacklist;
   public List<Pattern> AvatarBlacklist;
   public List<Pattern> PhonePatterns;
   public List<Pattern> EmailPatterns;
   public List<Pattern> UrlPatterns;


   public RegexManager()
   {
      this.Reload();
   }


   public void Reload()
   {
      LOGGER.info("Initializing and (re-)loading patterns...");

      this.OffensiveHi            = CompileRegexFromPatternList(LoadPatternsFromUrl(OFFENSIVE_REGEX_HI_URL        ));
      this.OffensiveMd            = CompileRegexFromPatternList(LoadPatternsFromUrl(OFFENSIVE_REGEX_MD_URL        ));
      this.OffensiveLo            = CompileRegexFromPatternList(LoadPatternsFromUrl(OFFENSIVE_REGEX_LO_URL        ));
      this.NameSmokeyBlacklist    = CompileRegexFromPatternList(LoadPatternsFromUrl(SMOKEY_NAME_REGEX_URL         ));
      this.UrlSmokeyBlacklist     = CompileRegexFromPatternList(LoadPatternsFromUrl(SMOKEY_URL_REGEX_URL          ));
      this.KeywordSmokeyBlacklist = CompileRegexFromPatternList(LoadPatternsFromUrl(SMOKEY_KEYWORD_REGEX_URL      ));
      this.KeywordSmokeyWatchlist = CompileRegexFromPatternList(LoadPatternsFromUrl(SMOKEY_KEYWORD_REGEX_WATCH_URL));
      this.NameBlacklist          = CompileRegexFromPatternList(LoadPatternsFromUrl(INTERNAL_NAME_REGEX_URL       ));
      this.AboutBlacklist         = CompileRegexFromPatternList(LoadPatternsFromUrl(INTERNAL_ABOUT_REGEX_URL      ));
      this.UrlBlacklist           = CompileRegexFromPatternList(LoadPatternsFromUrl(INTERNAL_URL_REGEX_URL        ));
      this.AvatarBlacklist        = CompileRegexFromPatternList(LoadPatternsFromUrl(INTERNAL_AVATAR_REGEX_URL     ));
      this.PhonePatterns          = CompileRegexFromPatternList(LoadPatternsFromUrl(INTERNAL_PHONE_REGEX_URL      ));
      this.EmailPatterns          = CompileRegexFromPatternList(LoadPatternsFromUrl(INTERNAL_EMAIL_REGEX_URL      ));
      this.UrlPatterns            = CompileRegexFromPatternList(LoadPatternsFromUrl(INTERNAL_URL_PATTERN_REGEX_URL));
   }


   public static boolean AnyMatches(String string, List<Pattern> patterns)
   {
      return patterns.parallelStream().anyMatch(pattern -> pattern.matcher(string).find());
   }


   private static ArrayList<String> LoadPatternsFromUrl(String url)
   {
      ArrayList<String> list = new ArrayList<String>();
      try
      {
         URL data = new URL(url);
         try (
              InputStreamReader isr = new InputStreamReader(data.openStream(),
                                                            StandardCharsets.UTF_8);
              BufferedReader    br  = new BufferedReader(isr);
             )
         {
            String line;
            while ((line = br.readLine()) != null)
            {
               if (!line.startsWith("#"))
               {
                  String expression = line.trim();
                  if (url.equals(SMOKEY_KEYWORD_REGEX_WATCH_URL))
                  {
                     expression = expression.split("\t")[2];
                  }

                  // Java's regular-expression engine doesn't support embedded comment groups, so when
                  // we later go to compile such a regex, the attempt will fail. Therefore, we strip
                  // out any embedded comments in the regex here, using a regex of all things!
                  expression = REGEX_COMMENT_PATTERN.matcher(expression).replaceAll("");

                  if (!expression.isBlank())
                  {
                     list.add(expression);
                  }
               }
            }
         }
      }
      catch (Exception ex)
      {
         LOGGER.warn("Failed to load regex patterns from <" + url + ">.");
         ex.printStackTrace();
      }
      return list;
   }

   private static Pattern CompileRegexFromPattern(String pattern, String prefix, String suffix)
   {
      return Pattern.compile(prefix + pattern + suffix,
                             Pattern.CASE_INSENSITIVE);
   }

   private static List<Pattern> CompileRegexFromPatternList(List<String> patternList)
   {
      return CompileRegexFromPatternList(patternList, "", "");
   }

   private static List<Pattern> CompileRegexFromPatternList(List<String> patternList,
                                                            String       prefix,
                                                            String       suffix)
   {
      return patternList.stream().map(pattern ->
      {
         try
         {
            return CompileRegexFromPattern(pattern, prefix, suffix);
         }
         catch (PatternSyntaxException exOuter)
         {
            LOGGER.warn("Invalid pattern syntax in regex: " + pattern + " => trying to rewrite it.");
         }

         try
         {
            // Java's regular-expression engine doesn't support certain types of variable-length
            // look-behinds. Patterns on SmokeDetector's username blacklist are often constructed
            // using look-behinds in order to match the entire name for the purposes of displaying
            // it in the "why" reason. Since we don't care about capturing the exact match, but only
            // positive detection, at least for now, we can do a rote transformation of these
            // look-behinds, replacing the unlimited quantifiers * and + with {0,40} and {1,40},
            // respectively, since usernames on SO/SE have a maximum length of 40 characters.
            // This will have the same effect, ensuring that the exact semantics are preserved,
            // without requiring a regex engine that supports fully variable-length look-behinds.
            // Note that these * and + quantifiers might also be possessive quantifiers (which are
            // heavily used by Charcoal in SmokeDetector as a performance optimization). Although
            // the Java regex engine does support possessive quantifiers, they cannot occur inside
            // look-behinds because they would make the length of the look-behind subject to change,
            // which is not supported. Thus, the "matcher" regexes allow either 1 or 2 occurrences
            // of these quantifiers, in order to ensure that both greedy and possessive quantifiers
            // are matched.
            //
            // This translation is done only after the first attempt to compile the regex fails,
            // and if it still fails to compile after this transformation, no other attempts at
            // recovery are made; the unsupported regex will simply be skipped.
            //
            // See also related discussion in Charcoal HQ, starting here:
            // <https://chat.stackexchange.com/transcript/message/59665065#59665065>
            pattern = REGEX_POSITIVE_LOOKBEHIND_STAR.matcher(pattern).replaceAll("$1{0,40}$2");
            pattern = REGEX_POSITIVE_LOOKBEHIND_PLUS.matcher(pattern).replaceAll("$1{1,40}$2");

            return CompileRegexFromPattern(pattern, prefix, suffix);
         }
         catch (PatternSyntaxException exInner)
         {
            LOGGER.warn("Invalid pattern syntax in regex: " + pattern + " => failed to rewrite it, so skipping.");
         }

         return null;
      })
      .filter(output -> output != null)
      .collect(Collectors.toList());
   }
}

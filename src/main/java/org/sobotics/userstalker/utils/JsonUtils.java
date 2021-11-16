package org.sobotics.userstalker.utils;


import java.io.IOException;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


/**
 * Created by bhargav.h on 10-Sep-16.
 * AKA, TunaLib - All code is courtesy of Lord Tunaki
 */
public class JsonUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);
    public static JsonObject get(String url, String... data) throws IOException {
        Connection.Response response = Jsoup.connect(url).data(data).method(Connection.Method.GET).ignoreContentType(true).ignoreHttpErrors(true).execute();
        String json = response.body();
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching URL " + (url) + ". Body is: " + response.body());
        }
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        return root;
    }
    public static JsonObject post(String url, String... data) throws IOException {
        Connection.Response response = Jsoup.connect(url).data(data).method(Connection.Method.POST).ignoreContentType(true).ignoreHttpErrors(true).execute();
        String json = response.body();
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching URL " + (url) + ". Body is: " + response.body());
        }
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        return root;
    }
    public static void handleBackoff(JsonObject root) {
        if (root.has("backoff")) {
            int backoff = root.get("backoff").getAsInt();
            LOGGER.info("Backing off for " + backoff+ " seconds. Quota left "+root.get("quota_remaining").getAsString());
            try {
                Thread.sleep(1000 * backoff);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public static String escapeHtmlEncoding(String message) {
        return Parser.unescapeEntities(JsonUtils.sanitizeChatMessage(message), false).trim();
    }
    public static String sanitizeChatMessage(String message) {
        return message.replaceAll("(\\[|\\]|_|\\*|`)", "\\\\$1");
    }

}


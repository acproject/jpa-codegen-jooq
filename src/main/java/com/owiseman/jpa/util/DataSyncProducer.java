package com.owiseman.jpa.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;

/**
 * @author acproject@qq.com
 * @date 2025-01-18 19:19
 */
public class DataSyncProducer {
    private static final String DATA_SYNC_TOPIC = "data-sync-topic";
    private static final String DATA_SYNC_GROUP = "data-sync-group";
    private Gson gson;
    private JsonObject rootNode;

    public DataSyncProducer(String json, String DataUrl) {
        gson = new Gson();
        rootNode =JsonParser.parseReader(new JsonReader(new StringReader(json))).getAsJsonObject();
    }
}

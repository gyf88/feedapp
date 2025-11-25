package com.example.feedapp.data.local;

import android.content.Context;

import com.example.feedapp.data.model.FeedCard;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

// 非常简单的 JSON 文件缓存：失败时能从本地读一份
public class FeedLocalDataSource {

    private static final String CACHE_FILE_NAME = "feed_cache.json";

    private final Gson gson = new Gson();
    private final File cacheFile;

    public FeedLocalDataSource(Context context) {
        cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
    }

    public void saveCache(List<FeedCard> cards) {
        if (cards == null) return;
        try (FileWriter writer = new FileWriter(cacheFile)) {
            writer.write(gson.toJson(cards));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<FeedCard> loadCache() {
        if (!cacheFile.exists()) {
            return Collections.emptyList();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            Type type = new TypeToken<List<FeedCard>>() {}.getType();
            return gson.fromJson(reader, type);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}

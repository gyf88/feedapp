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

/**
 * FeedLocalDataSource 负责「本地缓存」：
 *
 * - 对应作业进阶要求中的「本地数据缓存：网络失败时展示缓存」；
 * - 它不直接和 UI 交互，而是由 FeedRepository 调用：
 *      1. 网络成功时：把最新的列表存下来 saveCache(...)
 *      2. 网络失败时：读取本地缓存 loadCache()，尽量给用户一个兜底的数据。
 *
 * 这里采用最简单的实现：
 * - 使用 Gson 把 List<FeedCard> 转成 json 字符串；
 * - 把 json 写到 app 私有目录下的一个文件中；
 * - 读取时再反序列化回来。
 */
public class FeedLocalDataSource {

    /** 缓存文件在本地的路径：例如 /data/data/你的包名/files/feed_cache.json */
    private final File cacheFile;

    /** 用于 json 序列化 / 反序列化的工具类 */
    private final Gson gson = new Gson();

    public FeedLocalDataSource(Context context) {
        // context.getFilesDir() 是 app 私有的 files 目录：
        // - 不需要存储权限；
        // - app 卸载时会一并被删除。
        File dir = context.getFilesDir();
        this.cacheFile = new File(dir, "feed_cache.json");
    }

    /**
     * 把当前列表缓存到本地文件中。
     *
     * 调用时机（通常由 Repository 控制）：
     * - 下拉刷新成功后，用最新的列表覆盖本地缓存；
     * - 或者首次加载网络成功时，写入缓存。
     *
     * 注意这里做的是「整列表覆盖」，不是增量更新，
     */
    public void saveCache(List<FeedCard> cards) {
        if (cards == null) return;
        try (FileWriter writer = new FileWriter(cacheFile)) {
            gson.toJson(cards, writer);
        } catch (Exception e) {
            // 缓存失败不是致命错误，这里简单打印日志即可。
            e.printStackTrace();
        }
    }

    /**
     * 从本地缓存文件中读取上一次保存的列表。
     *
     * 调用时机（通常由 Repository 控制）：
     * - 当网络请求失败时，Repository 会先尝试 loadCache()，
     *   如果返回的列表非空，就用它来填充 UI；
     * - 如果返回空列表，就说明本地也没有可以用的兜底数据。
     */
    public List<FeedCard> loadCache() {
        if (!cacheFile.exists()) {
            // 从未缓存过，返回空列表即可。
            return Collections.emptyList();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            Type type = new TypeToken<List<FeedCard>>() {}.getType();
            return gson.fromJson(reader, type);
        } catch (Exception e) {
            e.printStackTrace();
            // 解析失败时不要让 app 崩掉，直接返回空列表。
            return Collections.emptyList();
        }
    }
}

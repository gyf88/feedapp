package com.example.feedapp;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.feedapp.exposure.ExposureEvent;
import com.example.feedapp.exposure.ExposureLogger;
import com.example.feedapp.ui.debug.LogAdapter;
import com.example.feedapp.ui.feed.FeedFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.widget.Toast;

import com.example.feedapp.data.remote.FeedRemoteDataSource;

/**
 * MainActivity 是整个 App 的入口 Activity（单 Activity 架构的“壳子”）：
 *
 * 功能职责：
 * 1. 在布局中的容器（R.id.container）里挂载首页 Fragment：FeedFragment，
 *    也就是你信息流列表所在的页面。
 * 2. 管理一个“曝光调试悬浮面板”：
 *    - 悬浮面板本身是一个覆盖在页面上的 View（exposureOverlay），里面有一个 RecyclerView；
 *    - RecyclerView 使用 LogAdapter 展示曝光日志（每一条是一个字符串）；
 *    - 右下角的 FloatingActionButton（fabDebugExposure）用来打开/关闭这个面板。
 * 3. 实现 ExposureLogger.Listener 接口：
 *    - 当曝光系统产生新事件时（ExposureTracker 算出 ENTER/HALF/FULL/EXIT），
 *      ExposureLogger 会回调 onNewEvent(...)，这里负责把日志展示在悬浮面板内。
 *
 * 这样设计的好处：
 * - 正常用户使用时，悬浮面板默认是隐藏的，不影响体验；
 * - 调试阶段点一下 FAB，就能在界面上实时看到曝光打点日志，方便验证逻辑。
 */

public class MainActivity extends AppCompatActivity implements ExposureLogger.Listener {
    /** 曝光调试悬浮层的根 View（一般是一个全屏半透明背景 + 内部一个 RecyclerView） */
    private View exposureOverlay;
    /** 用于显示曝光日志列表的 RecyclerView（在悬浮层内部） */
    private RecyclerView rvExposureLog;
    /** 显示日志的 Adapter，每一行是一条字符串 */
    private LogAdapter logAdapter;
    /** 内存中的日志字符串列表（按时间倒序插入，最新的在 index=0） */
    private final List<String> logs = new ArrayList<>();
    // -------------------- Activity 生命周期：onCreate --------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 使用 activity_main.xml 作为当前 Activity 的布局
        setContentView(R.layout.activity_main);

        // 1. 挂载 FeedFragment（只在第一次创建 Activity 时执行一次）
        //
        // savedInstanceState == null 表示不是因为旋转屏幕之类的重建；
        // 若是重建，系统会自动帮你恢复之前的 Fragment 状态，这里就不需要再 replace 一次。
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    // R.id.container 是 activity_main.xml 中的 Fragment 容器（FrameLayout）
                    .replace(R.id.container, new FeedFragment())
                    .commit();
        }

        // 2. 初始化“曝光调试悬浮日志面板”的相关 View 引用
        //
        // exposureOverlay：整个覆盖层（包括背景 + 内部内容），通常默认设置为 GONE
        exposureOverlay = findViewById(R.id.exposureOverlay);
        // rvExposureLog：悬浮层里面的 RecyclerView，用来显示日志列表
        rvExposureLog = findViewById(R.id.rvExposureLog);

        if (rvExposureLog != null) {
            // 2.1 先把 ExposureLogger 中已有的历史日志拉出来，填充到 logs 列表中
            //     （方法名 getAllLogs 由你在 ExposureLogger 中定义）
            logs.addAll(ExposureLogger.getAllLogs()); // 把历史日志加进来
            // 2.2 创建 LogAdapter，数据源就是 logs（按引用传入）
            logAdapter = new LogAdapter(logs);
            // 2.3 设置 RecyclerView 的布局管理器和适配器
            rvExposureLog.setLayoutManager(new LinearLayoutManager(this));
            rvExposureLog.setAdapter(logAdapter);
        }

        // 3. 设置右下角 FloatingActionButton 的点击事件：
        //    - 点击时打开/关闭曝光调试面板；
        //    - 同时在打开时注册曝光监听器，关闭时取消监听。
        FloatingActionButton fab = findViewById(R.id.fabDebugExposure);
        fab.setOnClickListener(v -> toggleExposurePanel());

        fab.setOnLongClickListener(v -> {
            showNetworkDebugDialog();
            return true;   // 消费长按事件
        });
    }
    private void showNetworkDebugDialog() {
        String[] items = {
                "正常模式（不故意失败）",
                "刷新必失败（page=0）",
                "加载更多必失败（page>0）",
                "随机失败（50% 概率）"
        };

        new AlertDialog.Builder(this)
                .setTitle("网络错误调试模式")
                .setItems(items, (dialog, which) -> {
                    FeedRemoteDataSource.FailMode mode;
                    String toastText;
                    switch (which) {
                        case 1:
                            mode = FeedRemoteDataSource.FailMode.REFRESH_ALWAYS_FAIL;
                            toastText = "已切到：刷新必失败";
                            break;
                        case 2:
                            mode = FeedRemoteDataSource.FailMode.LOAD_MORE_ALWAYS_FAIL;
                            toastText = "已切到：加载更多必失败";
                            break;
                        case 3:
                            mode = FeedRemoteDataSource.FailMode.RANDOM_FAIL;
                            toastText = "已切到：随机失败";
                            break;
                        case 0:
                        default:
                            mode = FeedRemoteDataSource.FailMode.NONE;
                            toastText = "已切到：正常模式";
                            break;
                    }
                    FeedRemoteDataSource.setFailMode(mode);
                    Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // -------------------- 悬浮曝光面板的开关逻辑 --------------------

    /**
     * 打开 / 关闭“曝光调试悬浮面板”：
     *
     * - 如果当前是显示状态（VISIBLE），则隐藏它，并从 ExposureLogger 中移除监听；
     * - 如果当前是隐藏状态（GONE/INVISIBLE），则显示它，并注册自己为 ExposureLogger 的监听器。
     *
     * 这样设计的原因：
     * - 避免永远监听曝光事件（Activity 即使在后台也能收到），容易造成内存泄漏；
     * - 只有当你主动打开调试面板时，才开始订阅曝光事件。
     */
    private void toggleExposurePanel() {
        if (exposureOverlay == null) return;

        if (exposureOverlay.getVisibility() == View.VISIBLE) {
            // 当前是“开启”状态 -> 关闭悬浮窗，并取消监听
            exposureOverlay.setVisibility(View.GONE);
            ExposureLogger.removeListener(this);
        } else {
            // 当前是“关闭”状态 -> 打开悬浮窗，并开始监听曝光事件
            exposureOverlay.setVisibility(View.VISIBLE);
            ExposureLogger.addListener(this);
        }
    }

    // -------------------- 实现 ExposureLogger.Listener 接口 --------------------

    /**
     * 当曝光系统（ExposureTracker -> ExposureLogger）产生新事件时，就会回调到这里。
     *
     * @param event     结构化曝光事件（包含 cardId、position、stage、timestamp 等）
     * @param formatted 已经格式化好的日志字符串（例如："[12:01:02.123] pos=3 id=xxx stage=FULL ..."）
     *
     * 这里的逻辑：
     * - 只负责把 formatted 文本插入到 logs 列表的最前面；
     * - 通知 logAdapter 在 index=0 插入一条新 item；
     * - 滚动 RecyclerView 到 position=0，保证最新日志一直在顶部可见。
     */
    @Override
    public void onNewEvent(ExposureEvent event, String formatted) {
        // 如果日志列表或适配器还没有初始化（理论上很少出现），就直接返回
        if (rvExposureLog == null || logAdapter == null) return;
        // onNewEvent 可能在非主线程被调用，这里统一通过 runOnUiThread 回到主线程更新 UI
        runOnUiThread(() -> {
            // 1. 把新的日志文本插入到列表头部（最新的在 index=0）
            logs.add(0, formatted);
            // 2. 通知 RecyclerView 在 position=0 插入了一条新数据
            logAdapter.notifyItemInserted(0);
            // 3. 让列表自动滚动到顶部，显示最新日志
            rvExposureLog.scrollToPosition(0);
        });
    }

    // -------------------- 生命周期：onDestroy --------------------

    /**
     * 在 Activity 销毁时，一定要记得从 ExposureLogger 中移除监听器：
     * - 避免持有已经被销毁的 Activity 引用，产生内存泄漏；
     * - 这也是所有“全局静态单例 + 回调接口”的典型用法：注册时记得对应注销。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        ExposureLogger.removeListener(this);
    }
}

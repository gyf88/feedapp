package com.example.feedapp.ui.debug;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.feedapp.databinding.ActivityExposureDebugBinding;
import com.example.feedapp.exposure.ExposureEvent;
import com.example.feedapp.exposure.ExposureLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 曝光调试页面 Activity：
 *
 * 功能：
 * - 在一个单独的页面中，将所有曝光事件的日志按列表展示出来；
 * - 实时监听新的曝光事件，把最新一条插入列表顶部；
 * - 方便你在滑动主列表的时候，实时观察曝光打点是否正确。
 *
 * 架构关系：
 * - 数据来源：ExposureLogger（内存中维护一份日志列表 + 实时回调）；
 * - UI 展示：RecyclerView + LogAdapter；
 * - 当前类实现 ExposureLogger.Listener 接口，以便接收 onNewEvent 回调。
 */

public class ExposureDebugActivity extends AppCompatActivity implements ExposureLogger.Listener {
    /** ViewBinding 对应 activity_exposure_debug.xml */
    private ActivityExposureDebugBinding binding;

    /**
     * 当前页面展示的日志数据源：
     * - 每一项是一个已经格式化好的字符串（例如 "[12:01:02.123] pos=5 id=xxx stage=FULL ..."）
     * - 数据由 ExposureLogger.getLogs() 初始化，之后 onNewEvent 时不断往前插入。
     */
    private final List<String> logs = new ArrayList<>();
    /** RecyclerView 的适配器：把 logs 列表渲染成一列 TextView */
    private LogAdapter adapter;

    // -------------------- 生命周期：onCreate --------------------
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 使用 ViewBinding 绑定布局
        binding = ActivityExposureDebugBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // 1. 初始化列表数据：先把 ExposureLogger 里已有的日志全部取出来
        //    注意：这里用的是我们在 ExposureLogger 中定义的 getLogs() 方法
        logs.addAll(ExposureLogger.getAllLogs());
        // 2. 创建适配器，传入 logs 引用
        adapter = new LogAdapter(logs);
        // 3. 设置 RecyclerView：
        //    - 使用 LinearLayoutManager 实现简单的垂直列表
        //    - 设置适配器
        binding.rvLog.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLog.setAdapter(adapter);
    }

    // -------------------- 生命周期：onStart / onStop 订阅 & 取消监听器 --------------------

    /**
     * onStart：
     * - Activity 即将对用户可见时，开始监听新的曝光事件；
     * - 调用 ExposureLogger.addListener(this) 注册自己为监听器。
     */
    @Override
    protected void onStart() {
        super.onStart();
        ExposureLogger.addListener(this);
    }

    /**
     * onStop：
     * - Activity 不再可见时，取消监听；
     * - 否则可能导致内存泄漏（Activity 被销毁但 Listener 还在 ExposureLogger 里）。
     */
    @Override
    protected void onStop() {
        super.onStop();
        ExposureLogger.removeListener(this);
    }

    // -------------------- 实现 ExposureLogger.Listener 接口 --------------------

    /**
     * 当曝光系统产生一个新的曝光事件（阶段变化）时，会回调到这里：
     * - event：包含 cardId / position / stage / timestamp / title / cardType 等信息；
     * - formatted：已经格式化好的日志文本（你可以直接展示）。
     *
     * 这里的策略：
     * - 把最新日志插入到列表最前面（index = 0），类似“时间倒序”；
     * - 通知适配器有新 item 插入；
     * - 把 RecyclerView 滚动到 position=0，保证新日志能立即看到。
     */
    @Override
    public void onNewEvent(ExposureEvent event, String formatted) {
        // onNewEvent 可能在任意线程被调用，这里统一切回主线程更新 UI
        runOnUiThread(() -> {
            // 1. 把新的日志字符串插入到列表头部
            logs.add(0, formatted);
            // 2. 通知 Adapter：在 position=0 插入了一条新数据
            adapter.notifyItemInserted(0);
            // 3. 让 RecyclerView 滚动到顶部，显示这条最新的日志
            binding.rvLog.scrollToPosition(0);
        });
    }
}

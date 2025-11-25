package com.example.feedapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.feedapp.ui.feed.FeedFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 这个还是指向 activity_main.xml

        // 第一次创建时，把 FeedFragment 加载到 container 里
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new FeedFragment())
                    .commit();
        }

        // 找到右下角的 FAB，将来用于打开“曝光调试工具”
        FloatingActionButton fab = findViewById(R.id.fabDebugExposure);
        fab.setOnClickListener(v -> {
            // 暂时先简单一点：点击弹个 Toast 或打印日志
            // 以后这里打开调试 Activity
            // Toast.makeText(this, "调试工具开发中~", Toast.LENGTH_SHORT).show();
        });
    }
}

package com.example.wdr_outpost.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.wdr_outpost.R;

public class ProfileFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 加载布局文件
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // 获取 TextView 引用
        TextView helpText = view.findViewById(R.id.help_text);
        TextView settingsText = view.findViewById(R.id.settings_text);
        TextView aboutText = view.findViewById(R.id.about_text);
        TextView ratingText = view.findViewById(R.id.rating_text);

        // 设置点击事件监听器
        helpText.setOnClickListener(v -> showCustomToast());
        settingsText.setOnClickListener(v -> showCustomToast());
        aboutText.setOnClickListener(v -> showCustomToast());
        ratingText.setOnClickListener(v -> showCustomToast());

        return view;
    }

    // 显示自定义时长的 Toast 消息
    private void showCustomToast() {
        Toast toast = Toast.makeText(getContext(), "敬请期待", Toast.LENGTH_SHORT);
        toast.show();

        // 使用 Handler 控制显示时间，明确指定 Looper
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(toast::cancel, 500); // duration 单位为毫秒
    }
}
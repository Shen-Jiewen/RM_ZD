package com.example.wdr_outpost;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

public class DeviceFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 使用原来的 activity_main.xml 布局
        return inflater.inflate(R.layout.activity_main, container, false);
    }
}
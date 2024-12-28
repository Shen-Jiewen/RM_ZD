package com.example.wdr_outpost.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

import com.example.wdr_outpost.R;

public class ProfileFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 创建一个简单的空白页面
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        return view;
    }
}
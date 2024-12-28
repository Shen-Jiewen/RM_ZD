package com.example.wdr_outpost;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Choreographer;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Random;

public class ChartActivity extends AppCompatActivity {

    private final LineChart[] lineCharts = new LineChart[6]; // 六个 LineChart
    private final LineDataSet[] dataSets = new LineDataSet[6]; // 六个数据集
    private final ArrayList[] values = new ArrayList[6]; // 六个数据点列表
    private int dataCount = 0;
    private Choreographer choreographer;
    private Choreographer.FrameCallback frameCallback;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        // 初始化六个 LineChart
        lineCharts[0] = findViewById(R.id.lineChart1);
        lineCharts[1] = findViewById(R.id.lineChart2);
        lineCharts[2] = findViewById(R.id.lineChart3);
        lineCharts[3] = findViewById(R.id.lineChart4);
        lineCharts[4] = findViewById(R.id.lineChart5);
        lineCharts[5] = findViewById(R.id.lineChart6);

        // 初始化数据和数据集
        for (int i = 0; i < 6; i++) {
            values[i] = new ArrayList<>();
            dataSets[i] = new LineDataSet(values[i], "波形 " + (i + 1));
            dataSets[i].setColor(getColorForIndex(i));
            dataSets[i].setLineWidth(2f);
            dataSets[i].setDrawCircles(false); // 禁用圆点显示
            dataSets[i].setDrawValues(false);  // 禁用值显示

            LineData data = new LineData(dataSets[i]);
            lineCharts[i].setData(data);
            customizeChart(lineCharts[i]);
        }

        startDataGeneration();
    }

    private void customizeChart(LineChart chart) {
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.BLACK);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextSize(10f);
        leftAxis.setTextColor(Color.BLACK);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false); // 禁用右侧 Y 轴

        chart.getDescription().setEnabled(false); // 禁用描述
        chart.getLegend().setEnabled(true); // 启用图例
        chart.setDrawMarkers(false); // 禁用高亮显示
    }

    private void startDataGeneration() {
        choreographer = Choreographer.getInstance();
        frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                addDataPoint();
                choreographer.postFrameCallback(this); // 继续下一帧回调
            }
        };
        choreographer.postFrameCallback(frameCallback); // 启动定时任务
    }

    private void addDataPoint() {
        float x = dataCount;

        // 为每个卡片生成数据点
        for (int i = 0; i < 6; i++) {
            float y = generateWave(x, i); // 生成不同波形
            values[i].add(new Entry(x, y));

            // 如果数据点数量超过 200，移除最旧的数据点
            if (values[i].size() > 200) {
                values[i].remove(0);
            }

            // 更新数据集
            dataSets[i].notifyDataSetChanged();
            LineData data = lineCharts[i].getData();
            if (data != null) {
                data.notifyDataChanged();
                lineCharts[i].notifyDataSetChanged();
                lineCharts[i].postInvalidate(); // 异步刷新 UI
            }

            if (dataCount >= 0 && dataCount < values[i].size()) {
                lineCharts[i].moveViewToX(dataCount);
            }
        }

        dataCount++;
        Log.d("ChartActivity", "Data count: " + dataCount);
    }

    private float generateWave(float x, int index) {
        // 为每个卡片生成不同的波形
        switch (index) {
            case 0:
                return (float) Math.sin(x * 0.1) * 10 + 20; // 正弦波
            case 1:
                return (Math.sin(x * 0.1) > 0) ? 10 : -10; // 方波
            case 2:
                return (float) (Math.asin(Math.sin(x * 0.1)) * 10); // 三角波
            case 3:
                return (float) (16 * Math.pow(Math.sin(x * 0.1), 3)); // 爱心正弦波
            case 4:
                return generateEcgWave(x); // 类心电图
            case 5:
                return generateRandomPowerWave(x); // 功率随机波形
            default:
                return 0;
        }
    }

    private float generateEcgWave(float x) {
        // 模拟类心电图的不规则波形
        if (x % 50 < 10) {
            return (float) (Math.sin(x * 0.5) * 15 + 20); // 模拟心跳峰值
        } else {
            return 20; // 模拟基线
        }
    }

    private float generateRandomPowerWave(float x) {
        // 基于随机数和衰减函数的功率随机波形
        return (float) (random.nextFloat() * 10 * Math.exp(-0.02 * x));
    }

    private int getColorForIndex(int index) {
        // 为每个卡片设置不同颜色
        switch (index) {
            case 0:
                return Color.BLUE;
            case 1:
                return Color.RED;
            case 2:
                return Color.GREEN;
            case 3:
                return Color.YELLOW;
            case 4:
                return Color.MAGENTA;
            case 5:
                return Color.CYAN;
            default:
                return Color.BLACK;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (choreographer != null && frameCallback != null) {
            choreographer.removeFrameCallback(frameCallback); // 停止定时任务
        }
    }
}
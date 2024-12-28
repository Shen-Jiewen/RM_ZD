package com.example.wdr_outpost;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import java.util.ArrayList;
import java.util.Random;

public class SupercapActivity extends AppCompatActivity {

    private TextView temperatureValue;
    private TextView batteryValue;
    private TextView inputPowerValue;
    private TextView outputPowerValue;
    private LineChart inputPowerChart;
    private LineChart outputPowerChart;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private float time = 0;

    private static final int DATA_POINTS = 30;

    private MyValueFormatter inputFormatter;
    private MyValueFormatter outputFormatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supercap);

        temperatureValue = findViewById(R.id.supercap_temperature_value);
        batteryValue = findViewById(R.id.supercap_battery_value);
        inputPowerValue = findViewById(R.id.supercap_input_power_value);
        outputPowerValue = findViewById(R.id.supercap_output_power_value);
        inputPowerChart = findViewById(R.id.supercap_input_power_chart);
        outputPowerChart = findViewById(R.id.supercap_output_power_chart);

        inputPowerChart.setHardwareAccelerationEnabled(true);
        outputPowerChart.setHardwareAccelerationEnabled(true);

        inputFormatter = new MyValueFormatter();
        outputFormatter = new MyValueFormatter();

        setupChart(inputPowerChart, inputFormatter);
        setupChart(outputPowerChart, outputFormatter);

        startUpdates();
    }

    private void setupChart(LineChart chart, MyValueFormatter formatter) {
        LineData data = new LineData();
        chart.setData(data);

        chart.getDescription().setEnabled(false);

        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        chart.setDrawGridBackground(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(4, true);
        xAxis.setValueFormatter(formatter);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(false);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        chart.getLegend().setEnabled(false);

        chart.setHighlightPerTapEnabled(false);

        chart.setDragXEnabled(false);
        chart.setScaleXEnabled(false);
    }

    private void startUpdates() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateValues();
                handler.postDelayed(this, 100);
            }
        }, 100);
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void updateValues() {
        int temperature = 20 + random.nextInt(6);
        int battery = 20 + random.nextInt(6);
        temperatureValue.setText(temperature + "°C");
        batteryValue.setText(battery + "%");

        float inputPower = (float) (45 + 5 * Math.sin(time));
        inputPowerValue.setText(String.format("%.1fW", inputPower));

        float triangleWave = getTriangleWave(time);
        float sineWave = (float) Math.sin(time);
        float outputPower = 40 + 5 * (triangleWave + sineWave);
        outputPowerValue.setText(String.format("%.1fW", outputPower));

        inputFormatter.setCurrentTime(time);
        outputFormatter.setCurrentTime(time);

        updateChart(inputPowerChart, inputPower);
        updateChart(outputPowerChart, outputPower);

        time += 0.1F;
    }

    private float getTriangleWave(float time) {
        float period = 2 * (float) Math.PI;
        float phase = time % period;
        if (phase < period / 2) {
            return 2 * phase / (period / 2) - 1;
        } else {
            return 1 - 2 * (phase - period / 2) / (period / 2);
        }
    }

    private void updateChart(LineChart chart, float value) {
        LineData data = chart.getData();
        if (data != null) {
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
            if (set == null) {
                set = new LineDataSet(new ArrayList<>(), "Power");
                set.setColor(0xFF3BB8FF);
                set.setLineWidth(2f);
                set.setDrawCircles(false);
                set.setDrawValues(false);
                data.addDataSet(set);
            }

            data.addEntry(new Entry(time, value), 0);

            if (set.getEntryCount() > DATA_POINTS) {
                data.removeEntry(0, 0);
            }

            XAxis xAxis = chart.getXAxis();
            xAxis.setAxisMinimum(time - 3);
            xAxis.setAxisMaximum(time);

            chart.notifyDataSetChanged();
            chart.invalidate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        inputPowerChart.clear();
        outputPowerChart.clear();
    }

    private static class MyValueFormatter extends ValueFormatter {
        private float currentTime;

        public void setCurrentTime(float currentTime) {
            this.currentTime = currentTime;
        }

        @Override
        public String getFormattedValue(float value) {
            float label = value - (currentTime - 3);
            return String.valueOf(Math.round(label));
        }
    }
}
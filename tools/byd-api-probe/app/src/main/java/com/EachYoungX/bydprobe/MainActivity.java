package com.EachYoungX.bydprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.lang.reflect.Method;

public class MainActivity extends Activity {
    private static final String DEVICE_CLASS =
            "android.hardware.bydauto.statistic.BYDAutoStatisticDevice";
    private static final String STATISTIC_PERMISSION =
            "android.permission.BYDAUTO_STATISTIC_GET";

    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        resultText = findViewById(R.id.result_text);
        Button readButton = findViewById(R.id.read_button);
        readButton.setOnClickListener(v -> readOnce());
        readOnce();
    }

    private void readOnce() {
        StringBuilder result = new StringBuilder();
        result.append("Signature: NORMAL\n");
        result.append("Firmware: 13.1.22.2409213.1\n\n");

        int permission = checkSelfPermission(STATISTIC_PERMISSION);
        result.append("Permission: ")
                .append(permission == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                .append("\n");

        try {
            Class<?> deviceClass = Class.forName(DEVICE_CLASS);
            result.append("Statistic class: FOUND\n");

            Method getInstance = deviceClass.getMethod("getInstance", Context.class);
            Object device = getInstance.invoke(null, this);
            if (device == null) {
                result.append("getInstance: NULL\n");
                resultText.setText(result);
                return;
            }
            result.append("getInstance: OK\n");

            Method getMileage = deviceClass.getMethod("getTotalMileageValue");
            Object rawValue = getMileage.invoke(device);
            result.append("getTotalMileageValue: OK\n")
                    .append("Raw value: ").append(String.valueOf(rawValue)).append("\n")
                    .append("Unit: UNCONFIRMED\n");
        } catch (ClassNotFoundException e) {
            result.append("Statistic class: NOT_FOUND\n");
            result.append("Last error: ").append(e.getClass().getSimpleName()).append("\n");
        } catch (SecurityException e) {
            result.append("Last error: SecurityException\n");
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            result.append("Last error: ").append(cause.getClass().getSimpleName())
                    .append(": ").append(String.valueOf(cause.getMessage())).append("\n");
        }

        resultText.setText(result);
    }
}

package com.example.bashgahnagah;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String WORK_TAG = "message_worker";
    private static final String CHANNEL_ID = "background_notification_channel";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private int selectedSimId = 0;
    private int selectedHour = 0;
    private int selectedMinute = 3; // مقدار پیش‌فرض 3 دقیقه

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. مقداردهی اولیه ویوها
        initViews();

        // 2. بررسی و درخواست مجوزها
        checkAndRequestPermissions();

        // 3. ایجاد کانال نوتیفیکیشن
        createNotificationChannel();
    }

    /**
     * مقداردهی اولیه ویوها و تنظیم لیسنرها
     */
    private void initViews() {
        EditText fetchAddressEditText = findViewById(R.id.fetch);
        EditText postAddressEditText = findViewById(R.id.post);
        Button timePickerButton = findViewById(R.id.duration);
        Spinner simSpinner = findViewById(R.id.sims);
        Button registerJobButton = findViewById(R.id.registerJobButton);

        // 1. تنظیم انتخابگر زمان
        timePickerButton.setOnClickListener(view -> showTimePickerDialog());

        // 2. پر کردن اسپینر سیم‌کارت‌ها
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        setupSimSpinner(simSpinner);

        // 3. ثبت کار پس‌زمینه
        registerJobButton.setOnClickListener(v -> {
            String fetchUrl = fetchAddressEditText.getText().toString();
            String postUrl = postAddressEditText.getText().toString();

            if (validateInputs(fetchUrl, postUrl)) {
                registerBackgroundJob(fetchUrl, postUrl, selectedSimId);
            }
        });
    }

    /**
     * بررسی و درخواست مجوزهای لازم
     */
    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.SEND_SMS
                    },
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    /**
     * تنظیم اسپینر سیم‌کارت‌ها
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private void setupSimSpinner(Spinner simSpinner) {
        SubscriptionManager subscriptionManager = ContextCompat.getSystemService(this, SubscriptionManager.class);
        if (subscriptionManager == null) {
            Log.e(TAG, "SubscriptionManager is not available");
            return;
        }

        List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
        List<String> simNames = new ArrayList<>();

        if (subscriptionInfos != null && !subscriptionInfos.isEmpty()) {
            for (SubscriptionInfo info : subscriptionInfos) {
                CharSequence displayName = info.getDisplayName();
                simNames.add(displayName != null ? displayName.toString() : "SIM " + info.getSubscriptionId());
            }
        } else {
            simNames.add("سیم‌کارت یافت نشد");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                simNames
        );

        simSpinner.setAdapter(adapter);
        simSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (subscriptionInfos != null && position < subscriptionInfos.size()) {
                    selectedSimId = position;
                } else {
                    selectedSimId = 0;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedSimId = 0;
            }
        });
    }

    /**
     * نمایش دیالوگ انتخاب زمان
     */
    private void showTimePickerDialog() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    updateTimeButtonText();
                },
                selectedHour,
                selectedMinute,
                true // 24-hour format
        );

        timePickerDialog.setTitle("انتخاب بازه زمانی");
        timePickerDialog.show();
    }

    /**
     * بروزرسانی متن دکمه زمان
     */
    private void updateTimeButtonText() {
        Button timeButton = findViewById(R.id.duration);
        String timeText = String.format("%02d ساعت و %02d دقیقه", selectedHour, selectedMinute);
        timeButton.setText(timeText);
    }

    /**
     * اعتبارسنجی ورودی‌ها
     */
    private boolean validateInputs(String fetchUrl, String postUrl) {
        if (fetchUrl.isEmpty() || postUrl.isEmpty()) {
            Toast.makeText(this, "لطفا آدرس‌ها را وارد کنید", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (selectedHour == 0 && selectedMinute < 1) {
            Toast.makeText(this, "حداقل بازه زمانی 1 دقیقه است", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    /**
     * ثبت کار پس‌زمینه
     */
    private void registerBackgroundJob(String fetchUrl, String postUrl, int simId) {
        // 1. ایجاد داده‌های ورودی
        Data inputData = new Data.Builder()
                .putString("fetch", fetchUrl)
                .putString("post", postUrl)
                .putInt("cardId", simId)
                .build();

        // 2. محاسبه بازه زمانی بر اساس ساعت و دقیقه
        long repeatInterval = selectedHour * 60 + selectedMinute;
        repeatInterval = Math.max(repeatInterval, 1); // حداقل 1 دقیقه

        // 3. ایجاد درخواست کار تناوبی
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                messageWorker.class,
                repeatInterval,
                TimeUnit.MINUTES
        )
                .setInputData(inputData)
                .build();

        // 4. ثبت کار با سیاست جایگزینی کارهای قبلی
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        WORK_TAG,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest
                );

        Toast.makeText(this,
                "کار پس‌زمینه با موفقیت ثبت شد\nبازه زمانی: هر " +
                        selectedHour + " ساعت و " + selectedMinute + " دقیقه",
                Toast.LENGTH_LONG).show();
    }

    /**
     * ایجاد کانال نوتیفیکیشن
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "اعلان‌های پس‌زمینه",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("اعلان‌های مربوط به ارسال پیامک در پس‌زمینه");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // مجوزها داده شده، اسپینر سیم‌کارت‌ها را پر کن
                Spinner simSpinner = findViewById(R.id.sims);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                setupSimSpinner(simSpinner);
            } else {
                Toast.makeText(this,
                        "برای استفاده از برنامه باید مجوزهای لازم را تایید کنید",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
package com.example.bashgahnagah;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.bashgahnagah.interfaces.Message;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import android.telephony.SmsManager;

public class messageWorker extends Worker {
    // Constants
    private static final String CHANNEL_ID = "background_notification_channel";
    private static final String CHANNEL_NAME = "Background Notifications";
    private static final String TAG = "MessageWorker";
    private static final String AUTH_TOKEN = "Bearer e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final int CONNECTION_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 10000; // 10 seconds
    private static Set<String> sentMessageIds = new HashSet<>();

    private static final long DELAY_BETWEEN_MESSAGES = 2000; // 2 ثانیه تاخیر بین پیام‌ها
    private static final String PREF_SENT_MESSAGES = "sent_messages";

    public messageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @NonNull
    @Override
    public Result doWork() {
        // 1. ابتدا کانال نوتیفیکیشن را ایجاد می‌کنیم
        createNotificationChannel();

        Log.d(TAG, "Background job started. Fetch URL: " + getInputData().getString("fetch") +
                ", Post URL: " + getInputData().getString("post"));

        try {
            // 2. دریافت لیست پیام‌ها از سرور
            List<Message> messages = fetchMessagesFromServer();
            if (messages == null || messages.isEmpty()) {
                Log.d(TAG, "No messages to process");
                return Result.success(); // اگر پیامی نیست، موفق برگردان
            }

            // 3. بررسی مجوزهای لازم
            if (!checkRequiredPermissions()) {
                showNotification("خطای دسترسی", "دسترسی‌های لازم وجود ندارد");
                return Result.failure();
            }

            // 4. دریافت اطلاعات سیم‌کارت
            SubscriptionInfo simInfo = getSimCardInfo();
            if (simInfo == null) {
                showNotification("خطای سیم‌کارت", "سیم‌کارت فعال یافت نشد");
                return Result.failure();
            }

//            // 5. پردازش هر پیام
//            for (Message message : messages) {
//                processSingleMessage(message, simInfo);
//            }
            // 5. پردازش ترتیبی پیام‌ها با تاخیر
            processMessagesSequentially(messages, simInfo);


            Log.d(TAG, "Background job completed successfully");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error in doWork: " + e.getMessage(), e);
            showNotification("خطا", "خطا در پردازش پیام‌ها: " + e.getMessage());
            return Result.failure();
        }
    }

    /**
     * ایجاد کانال نوتیفیکیشن برای اندروید 8 به بالا
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for background message processing");

            NotificationManager manager = getApplicationContext()
                    .getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * دریافت لیست پیام‌ها از سرور
     */
    private List<Message> fetchMessagesFromServer() throws IOException {
        URL url = new URL(Objects.requireNonNull(getInputData().getString("fetch")));
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", AUTH_TOKEN);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Failed to fetch messages. Response code: " + responseCode);
                showNotification("خطای سرور", "خطا در دریافت پیام‌ها. کد: " + responseCode);
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder jsonResponse = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                jsonResponse.append(line);
            }

            Gson gson = new Gson();
            Type listType = new TypeToken<List<Message>>() {}.getType();
            return gson.fromJson(jsonResponse.toString(), listType);

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing reader", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * بررسی مجوزهای لازم
     */
    private boolean checkRequiredPermissions() {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "READ_PHONE_STATE permission not granted");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted");
            return false;
        }

        return true;
    }

    /**
     * دریافت اطلاعات سیم‌کارت
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private SubscriptionInfo getSimCardInfo() {
        SubscriptionManager subscriptionManager = getApplicationContext()
                .getSystemService(SubscriptionManager.class);

        if (subscriptionManager == null) {
            Log.e(TAG, "SubscriptionManager is null");
            return null;
        }

        List<SubscriptionInfo> activeSubs = subscriptionManager.getActiveSubscriptionInfoList();
        if (activeSubs == null || activeSubs.isEmpty()) {
            Log.e(TAG, "No active SIM cards found");
            return null;
        }

        int cardId = getInputData().getInt("cardId", 0); // Default to first SIM
        if (cardId >= 0 && cardId < activeSubs.size()) {
            return activeSubs.get(cardId);
        } else {
            Log.w(TAG, "Invalid cardId, using first SIM card");
            return activeSubs.get(0); // استفاده از سیم‌کارت اول به عنوان پیش‌فرض
        }
    }

    /**
     * پردازش یک پیام واحد (ارسال SMS و بروزرسانی وضعیت)
     */
    private void processSingleMessage(Message message, SubscriptionInfo simInfo) {
        Log.d(TAG, "Processing message ID: " + message.id + ", Phone: " + message.phone);

        if (sentMessageIds.contains(message.id)) {
            Log.d(TAG, "Message ID " + message.id + " already sent. Skipping.");
            return; // پیام قبلاً ارسال شده، پس از ارسال آن جلوگیری می‌کنیم
        }
        // 1. ارسال پیامک
        boolean smsSent = sendSmsMessage(message, simInfo);

        // 2. فقط اگر پیامک ارسال شد، وضعیت را بروزرسانی کن
        if (smsSent) {
            boolean statusUpdated = updateMessageStatus(message);

            // 3. نمایش نوتیفیکیشن
            if (statusUpdated) {
                showNotification("پیام ارسال شد",
                        "پیام"+message.message+" به شماره " + message.phone + " ارسال شد");
            }
            // اضافه کردن شناسه پیام به مجموعه پس از ارسال موفق
            sentMessageIds.add(String.valueOf(message.id));
        }
    }

    /**
     * ارسال پیامک
     */
    private boolean sendSmsMessage(Message message, SubscriptionInfo simInfo) {
        try {
            Log.d(TAG, "Attempting to send SMS to: " + message.phone);

            SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(
                    simInfo.getSubscriptionId());
            smsManager.sendTextMessage(
                    message.phone,
                    null,
                    message.message,
                    null,
                    null);

            Log.d(TAG, "SMS sent successfully to: " + message.phone);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS to " + message.phone + ": " + e.getMessage(), e);
            showNotification("خطای ارسال",
                    "خطا در ارسال پیام به شماره " + message.phone + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * بروزرسانی وضعیت پیام در سرور
     */
    private boolean updateMessageStatus(Message message) {
        HttpURLConnection connection = null;
        OutputStream outputStream = null;

        try {
            URL postUrl = new URL(Objects.requireNonNull(getInputData().getString("post")));
            connection = (HttpURLConnection) postUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Authorization", AUTH_TOKEN);
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            String formData = "id=" + message.id;
            byte[] postData = formData.getBytes(StandardCharsets.UTF_8);

            outputStream = connection.getOutputStream();
            outputStream.write(postData);
            outputStream.flush();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Message status updated successfully for ID: " + message.id);
                return true;
            } else {
                Log.e(TAG, "Failed to update message status. Response code: " + responseCode);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating message status: " + e.getMessage(), e);
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * نمایش نوتیفیکیشن
     */
    private void showNotification(String title, String message) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);

            NotificationManager notificationManager = (NotificationManager)
                    getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                // استفاده از یک ID منحصر به فرد برای هر نوتیفیکیشن
                int notificationId = UUID.randomUUID().hashCode();
                notificationManager.notify(notificationId, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification: " + e.getMessage(), e);
        }
    }


    /**
     * پردازش ترتیبی پیام‌ها با تاخیر بین آنها
     */
    private void processMessagesSequentially(List<Message> messages, SubscriptionInfo simInfo) {
        // بارگذاری پیام‌های ارسال شده از SharedPreferences
        Set<String> sentMessages = loadSentMessages();

        for (Message message : messages) {
            synchronized (this) {
                if (sentMessages.contains(message.id)) {
                    Log.d(TAG, "Message ID " + message.id + " already sent. Skipping.");
                    continue;
                }

                processSingleMessage(message, simInfo);

                // ذخیره پیام ارسال شده
                sentMessages.add(String.valueOf(message.id));
                saveSentMessages(sentMessages);

                // تاخیر بین ارسال پیام‌ها
                try {
                    Thread.sleep(DELAY_BETWEEN_MESSAGES);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Delay between messages interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    /**
     * بارگذاری پیام‌های ارسال شده از SharedPreferences
     */
    private Set<String> loadSentMessages() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(
                PREF_SENT_MESSAGES, Context.MODE_PRIVATE);
        return prefs.getStringSet(PREF_SENT_MESSAGES, new HashSet<>());
    }
    /**
     * ذخیره پیام‌های ارسال شده در SharedPreferences
     */
    private void saveSentMessages(Set<String> sentMessages) {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(
                PREF_SENT_MESSAGES, Context.MODE_PRIVATE);
        prefs.edit()
                .putStringSet(PREF_SENT_MESSAGES, sentMessages)
                .apply();
    }
}
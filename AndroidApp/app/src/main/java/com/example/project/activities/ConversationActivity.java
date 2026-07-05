package com.example.project.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.*;

import com.example.project.R;
import com.example.project.adapters.MessageAdapter;
import com.example.project.api.SignalApiService;
import com.example.project.protocol.SignalProtocolManager;
import com.example.project.storage.MessageDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConversationActivity extends AppCompatActivity {
    private static final String TAG = "ConversationActivity";

    // UI Components
    private RecyclerView recyclerView;
    private EditText etMessage;
    private ImageButton btnSend;
    private TextView tvRecipientName;
    private ProgressBar progressBar;

    // Data
    private String recipientId;
    private String recipientName;
    private String myUserId;
    private String authToken;
    private String conversationId;

    // Adapters & Managers
    private MessageAdapter messageAdapter;
    private SignalProtocolManager signalManager;
    private SignalApiService apiService;
    private MessageDatabase messageDb;

    private ExecutorService executorService;
    private Handler mainHandler;

    // Message polling
    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private boolean isPolling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Get data from intent
        recipientId = getIntent().getStringExtra("recipient_id");
        recipientName = getIntent().getStringExtra("recipient_name");

        if (recipientId == null) {
            Toast.makeText(this, "Error: No recipient", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get user info
        SharedPreferences prefs = getSharedPreferences("signal_prefs", MODE_PRIVATE);
        myUserId = prefs.getString("user_id", "");
        authToken = prefs.getString("auth_token", "");

        conversationId = generateConversationId(myUserId, recipientId);

        // Initialize
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        pollingHandler = new Handler(Looper.getMainLooper());

        signalManager = new SignalProtocolManager(this, myUserId, authToken);
        apiService = new SignalApiService(this, authToken);
        messageDb = new MessageDatabase(this);

        // Setup UI
        setupViews();
        loadMessages();
        startMessagePolling();
    }

    private void setupViews() {
        tvRecipientName = findViewById(R.id.tvRecipientName);
        recyclerView = findViewById(R.id.recyclerMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        progressBar = findViewById(R.id.progressBar);

        tvRecipientName.setText(recipientName);

        // Setup RecyclerView
        messageAdapter = new MessageAdapter(new ArrayList<>(), myUserId);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(messageAdapter);

        // Send button
        btnSend.setOnClickListener(v -> sendMessage());

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void loadMessages() {
        executorService.execute(() -> {
            List<MessageDatabase.Message> messages = messageDb.getMessages(conversationId, 100);
            mainHandler.post(() -> {
                messageAdapter.setMessages(messages);
                scrollToBottom();
            });
        });
    }

    private void sendMessage() {
        String content = etMessage.getText().toString().trim();

        if (TextUtils.isEmpty(content)) {
            return;
        }

        if (content.length() > 10000) {
            Toast.makeText(this, "Message too long", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable input
        etMessage.setEnabled(false);
        btnSend.setEnabled(false);
        progressBar.setVisibility(ProgressBar.VISIBLE);

        String messageContent = content;
        etMessage.setText("");

        executorService.execute(() -> {
            try {
                Log.d(TAG, "Sending message to: " + recipientId);

                // Encrypt and send
                String messageId = signalManager.sendMessage(recipientId, messageContent);

                // Save to local DB
                messageDb.saveMessage(
                        messageId,
                        conversationId,
                        myUserId,
                        recipientId,
                        messageContent,
                        System.currentTimeMillis(),
                        true // isOutgoing
                );

                Log.d(TAG, "Message sent successfully: " + messageId);

                // Refresh UI
                mainHandler.post(() -> {
                    loadMessages();
                    Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to send message", e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Failed to send: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            } finally {
                mainHandler.post(() -> {
                    etMessage.setEnabled(true);
                    btnSend.setEnabled(true);
                    progressBar.setVisibility(ProgressBar.GONE);
                });
            }
        });
    }

    private void startMessagePolling() {
        if (isPolling) return;

        isPolling = true;
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                fetchNewMessages();
                if (isPolling) {
                    pollingHandler.postDelayed(this, 3000); // Poll every 3 seconds
                }
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopMessagePolling() {
        isPolling = false;
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    private void fetchNewMessages() {
        executorService.execute(() -> {
            try {
                List<SignalApiService.IncomingMessage> messages = apiService.fetchMessages();

                for (SignalApiService.IncomingMessage msg : messages) {
                    // Check if from current conversation
                    if (!msg.senderId.equals(recipientId)) {
                        continue;
                    }

                    // Decrypt message
                    String plaintext;
                    if (msg.type == 1) {
                        // PreKey message
                        plaintext = signalManager.processPreKeyMessage(
                                msg.senderId, msg.deviceId, msg.content);
                    } else {
                        // Normal message
                        plaintext = signalManager.decryptMessage(
                                msg.senderId, msg.deviceId, msg.content);
                    }

                    // Save to DB
                    messageDb.saveMessage(
                            msg.messageId,
                            conversationId,
                            msg.senderId,
                            myUserId,
                            plaintext,
                            msg.timestamp,
                            false // isOutgoing
                    );

                    // Acknowledge
                    apiService.acknowledgeMessage(msg.messageId);

                    Log.d(TAG, "Received message: " + plaintext);
                }

                // Refresh UI if we got new messages
                if (!messages.isEmpty()) {
                    mainHandler.post(() -> {
                        loadMessages();
                        playNotificationSound();
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error fetching messages", e);
            }
        });
    }

    private void scrollToBottom() {
        if (messageAdapter.getItemCount() > 0) {
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    private void playNotificationSound() {
        try {
            android.media.RingtoneManager.getRingtone(
                    this,
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            ).play();
        } catch (Exception e) {
            Log.e(TAG, "Failed to play sound", e);
        }
    }

    private String generateConversationId(String userId1, String userId2) {
        // Sort to ensure same conversation ID regardless of order
        if (userId1.compareTo(userId2) < 0) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMessagePolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMessagePolling();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMessagePolling();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (messageDb != null) {
            messageDb.close();
        }
    }
}
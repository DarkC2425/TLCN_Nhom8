package com.example.project.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.project.R;
import com.example.project.crypto.ChainKey;
import com.example.project.crypto.CryptoUtils;
import com.example.project.crypto.RatchetMessenger;
import com.example.project.crypto.RatchetState;
import com.example.project.crypto.X25519Util;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    EditText etMessage;
    Button btnSend, btnClear;
    TextView tvOutput, tvCharCount;
    ScrollView scrollView;
    ImageButton btnSettings;

    RatchetMessenger aliceMessenger;
    RatchetMessenger bobMessenger;

    private static final int MAX_MESSAGE_LENGTH = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnClear = findViewById(R.id.btnClear);
        tvOutput = findViewById(R.id.tvOutput);
        tvCharCount = findViewById(R.id.tvCharCount);
        scrollView = findViewById(R.id.scrollView);
        btnSettings = findViewById(R.id.btnSettings);

        // Setup Double Ratchet protocol
        initializeRatchet();

        // Setup character counter
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();
                tvCharCount.setText(length + " / " + MAX_MESSAGE_LENGTH + " ký tự");

                // Change color if approaching limit
                if (length > MAX_MESSAGE_LENGTH * 0.9) {
                    tvCharCount.setTextColor(getResources().getColor(R.color.error, null));
                } else if (length > MAX_MESSAGE_LENGTH * 0.7) {
                    tvCharCount.setTextColor(getResources().getColor(R.color.warning, null));
                } else {
                    tvCharCount.setTextColor(getResources().getColor(R.color.text_secondary, null));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSend.setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (TextUtils.isEmpty(msg)) {
                Toast.makeText(this, R.string.empty_message, Toast.LENGTH_SHORT).show();
                return;
            }

            if (msg.length() > MAX_MESSAGE_LENGTH) {
                Toast.makeText(this, R.string.message_too_long, Toast.LENGTH_SHORT).show();
                return;
            }

            sendMessage(msg);
        });

        btnClear.setOnClickListener(v -> {
            tvOutput.setText(R.string.waiting_for_messages);
            etMessage.setText("");
            Toast.makeText(this, "Đã xóa", Toast.LENGTH_SHORT).show();
        });

        btnSettings.setOnClickListener(v -> {
            Toast.makeText(this, "Cài đặt", Toast.LENGTH_SHORT).show();
        });
    }

    private void initializeRatchet() {
        try {
            Log.d(TAG, "Initializing Double Ratchet protocol...");

            // Step 1: Derive shared secret (simulating X3DH)
            byte[] sharedSecret = CryptoUtils.randomBytes(32);
            Log.d(TAG, "Shared secret: " + bytesToHex(sharedSecret));

            // Step 2: Derive initial root key and chain keys using HKDF
            byte[] okm = CryptoUtils.hkdf(null, sharedSecret, "Init".getBytes(), 96);
            byte[] rootKey = new byte[32];
            byte[] aliceSendCK = new byte[32];
            byte[] bobSendCK = new byte[32];
            System.arraycopy(okm, 0, rootKey, 0, 32);
            System.arraycopy(okm, 32, aliceSendCK, 0, 32);
            System.arraycopy(okm, 64, bobSendCK, 0, 32);

            Log.d(TAG, "Root key: " + bytesToHex(rootKey));

            // Step 3: Generate ephemeral keypairs
            X25519Util.KeyPair aliceKP = X25519Util.generateKeyPair();
            X25519Util.KeyPair bobKP = X25519Util.generateKeyPair();

            Log.d(TAG, "Alice ephemeral pub: " + bytesToHex(aliceKP.pub));
            Log.d(TAG, "Bob ephemeral pub: " + bytesToHex(bobKP.pub));

            // Step 4: Create ratchet states
            // CRITICAL: Alice's sending chain = Bob's receiving chain (same key)
            //           Bob's sending chain = Alice's receiving chain (same key)

            RatchetState aliceState = new RatchetState(
                    rootKey,
                    aliceKP.priv,
                    aliceKP.pub,
                    bobKP.pub,  // Alice knows Bob's pub
                    new ChainKey(aliceSendCK, 0),  // Alice's sending chain
                    new ChainKey(bobSendCK, 0)     // Alice's receiving chain (= Bob's sending)
            );

            RatchetState bobState = new RatchetState(
                    rootKey,
                    bobKP.priv,
                    bobKP.pub,
                    aliceKP.pub,  // Bob knows Alice's pub
                    new ChainKey(bobSendCK, 0),    // Bob's sending chain
                    new ChainKey(aliceSendCK, 0)   // Bob's receiving chain (= Alice's sending)
            );

            // Create messengers
            aliceMessenger = new RatchetMessenger(aliceState);
            bobMessenger = new RatchetMessenger(bobState);

            // Clean up
            CryptoUtils.clearBytes(sharedSecret);
            CryptoUtils.clearBytes(okm);
            aliceKP.destroy();
            bobKP.destroy();

            appendOutput("✓ Double Ratchet protocol đã được khởi tạo\n");
            appendOutput("  Alice sending → Bob receiving (cùng chain key)\n");
            appendOutput("  Bob sending → Alice receiving (cùng chain key)\n");
            appendOutput("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

            Log.d(TAG, "Initialization complete");

        } catch (Exception e) {
            Log.e(TAG, "Initialization failed", e);
            e.printStackTrace();
            Toast.makeText(this, R.string.initialization_error + ": " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void sendMessage(String msg) {
        try {
            Log.d(TAG, "=== Sending message: " + msg + " ===");

            // Alice gửi message
            byte[] plaintext = msg.getBytes("UTF-8");
            long startTime = System.currentTimeMillis();
            byte[] packet = aliceMessenger.encrypt(plaintext);
            long encryptTime = System.currentTimeMillis() - startTime;

            Log.d(TAG, "Encryption completed in " + encryptTime + "ms");

            appendOutput("📤 Alice gửi: \"" + msg + "\"\n");
            appendOutput("   📦 Kích thước gốc: " + plaintext.length + " bytes\n");
            appendOutput("   🔐 Kích thước mã hóa: " + packet.length + " bytes\n");
            appendOutput("   ⏱️ Thời gian mã hóa: " + encryptTime + "ms\n\n");

            // Bob nhận và giải mã
            Log.d(TAG, "=== Bob receiving message ===");
            startTime = System.currentTimeMillis();
            byte[] decrypted = bobMessenger.decrypt(packet);
            long decryptTime = System.currentTimeMillis() - startTime;
            String received = new String(decrypted, "UTF-8");

            Log.d(TAG, "Decryption completed in " + decryptTime + "ms");

            appendOutput("📥 Bob nhận: \"" + received + "\"\n");
            appendOutput("   ⏱️ Thời gian giải mã: " + decryptTime + "ms\n");

            // Verify
            if (msg.equals(received)) {
                appendOutput("   ✅ Xác thực: Thành công!\n");
                Log.d(TAG, "Message verified successfully");
            } else {
                appendOutput("   ❌ Xác thực: Thất bại!\n");
                Log.e(TAG, "Message verification failed!");
            }

            appendOutput("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

            // Clear input
            etMessage.setText("");

            // Scroll to bottom
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));

            // Clean up
            CryptoUtils.clearBytes(plaintext);
            CryptoUtils.clearBytes(packet);
            CryptoUtils.clearBytes(decrypted);

        } catch (Exception ex) {
            Log.e(TAG, "Message send/receive failed", ex);
            ex.printStackTrace();
            appendOutput("❌ Lỗi: " + ex.getMessage() + "\n\n");
            Toast.makeText(this, R.string.encryption_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void appendOutput(String text) {
        tvOutput.append(text);
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, 8); i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        if (bytes.length > 8) sb.append("...");
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up cryptographic state
        Log.d(TAG, "Cleaning up");
    }
}
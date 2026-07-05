package com.example.project.activities;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.*;
import android.util.Log;

import com.example.project.R;
import com.example.project.activities.LoginActivity;
import com.example.project.api.SignalApiService;
import com.example.project.protocol.SignalProtocolManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";

    EditText etUsername, etPassword, etConfirmPassword, etDisplayName;
    Button btnRegister;
    ProgressBar progressBar;
    TextView tvLogin;

    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.project.R.layout.activity_register);

        // Initialize views
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etDisplayName = findViewById(R.id.etDisplayName);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);
        tvLogin = findViewById(R.id.tvLogin);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        btnRegister.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();
            String displayName = etDisplayName.getText().toString().trim();

            if (!validateInput(username, password, confirmPassword)) {
                return;
            }

            progressBar.setVisibility(ProgressBar.VISIBLE);
            btnRegister.setEnabled(false);

            executorService.execute(() -> register(username, password, displayName));
        });

        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private boolean validateInput(String username, String password, String confirmPassword) {
        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Vui lòng nhập tên đăng nhập");
            etUsername.requestFocus();
            return false;
        }

        if (username.length() < 3 || username.length() > 20) {
            etUsername.setError("Tên đăng nhập phải từ 3-20 ký tự");
            etUsername.requestFocus();
            return false;
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            etUsername.setError("Chỉ chữ, số và gạch dưới");
            etUsername.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Vui lòng nhập mật khẩu");
            etPassword.requestFocus();
            return false;
        }

        if (password.length() < 8) {
            etPassword.setError("Mật khẩu phải có ít nhất 8 ký tự");
            etPassword.requestFocus();
            return false;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Mật khẩu không khớp");
            etConfirmPassword.requestFocus();
            return false;
        }

        return true;
    }

    private void register(String username, String password, String displayName) {
        try {
            Log.d(TAG, "Starting registration for: " + username);

            // 1. Initialize Signal Protocol Manager (temporary, no auth yet)
            SignalProtocolManager signalManager = new SignalProtocolManager(
                    this, "temp", null);

            // 2. Initialize protocol (generates keys)
            signalManager.initialize();

            // 3. Get identity public key
            byte[] identityPublicKey = signalManager.getIdentityPublicKey();

            // 4. Get prekeys
            List<SignalApiService.PreKeyBundle> preKeys = signalManager.getPreKeysForUpload();

            // 5. Register with server
            SignalApiService apiService = new SignalApiService(this, null);
            SignalApiService.RegisterResponse response = apiService.registerAccount(
                    username, password, identityPublicKey, preKeys);

            Log.d(TAG, "Registration successful: " + response.userId);

            // 6. Save credentials locally
            SharedPreferences prefs = getSharedPreferences("signal_prefs", MODE_PRIVATE);
            prefs.edit()
                    .putString("user_id", response.userId)
                    .putString("username", username)
                    .putString("auth_token", response.authToken)
                    .putString("display_name", displayName.isEmpty() ? username : displayName)
                    .apply();

            // 7. Update profile if display name provided
            if (!displayName.isEmpty()) {
                SignalApiService authenticatedApi = new SignalApiService(this, response.authToken);
                authenticatedApi.updateProfile(displayName, "");
            }

            showMessage("Đăng ký thành công!");

            // 8. Navigate to MainActivity
            mainHandler.postDelayed(() -> {
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }, 1000);

        } catch (Exception e) {
            Log.e(TAG, "Registration failed", e);
            showMessage("Đăng ký thất bại: " + e.getMessage());
            enableButton();
        }
    }

    private void enableButton() {
        mainHandler.post(() -> {
            progressBar.setVisibility(ProgressBar.GONE);
            btnRegister.setEnabled(true);
        });
    }

    private void showMessage(String message) {
        mainHandler.post(() ->
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show()
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
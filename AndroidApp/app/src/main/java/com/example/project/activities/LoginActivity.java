package com.example.project.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import android.text.TextUtils;

import com.example.project.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    EditText etUsername, etPassword;
    Button btnLogin;
    ProgressBar progressBar;

    private ExecutorService executorService;
    private Handler mainHandler;

    // Giới hạn số lần thử đăng nhập
    private int loginAttempts = 0;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private long lastFailedAttempt = 0;
    private static final long LOCKOUT_TIME = 300000; // 5 phút

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();

            // Validation
            if (!validateInput(username, password)) {
                return;
            }

            // Kiểm tra lockout
            if (isLockedOut()) {
                long remainingTime = (LOCKOUT_TIME - (System.currentTimeMillis() - lastFailedAttempt)) / 1000;
                showMessage("Tài khoản tạm khóa. Vui lòng thử lại sau " + remainingTime + " giây");
                return;
            }

            progressBar.setVisibility(ProgressBar.VISIBLE);
            btnLogin.setEnabled(false);

            executorService.execute(() -> login(username, password));
        });
    }

    private boolean validateInput(String username, String password) {
        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Vui lòng nhập tên đăng nhập");
            etUsername.requestFocus();
            return false;
        }

        if (username.length() < 3 || username.length() > 50) {
            etUsername.setError("Tên đăng nhập phải từ 3-50 ký tự");
            etUsername.requestFocus();
            return false;
        }

        // Chỉ cho phép chữ cái, số và gạch dưới
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            etUsername.setError("Tên đăng nhập chỉ chứa chữ, số và gạch dưới");
            etUsername.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Vui lòng nhập mật khẩu");
            etPassword.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            etPassword.setError("Mật khẩu phải có ít nhất 6 ký tự");
            etPassword.requestFocus();
            return false;
        }

        return true;
    }

    private boolean isLockedOut() {
        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            long timeSinceLastFail = System.currentTimeMillis() - lastFailedAttempt;
            if (timeSinceLastFail < LOCKOUT_TIME) {
                return true;
            } else {
                // Reset sau khi hết thời gian khóa
                loginAttempts = 0;
            }
        }
        return false;
    }

    private void login(String username, String password) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL("http://10.0.2.2:5001/api/auth/signIn");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);

                // Gửi body JSON
                String jsonInput = "{\"username\":\"" + username + "\", \"password\":\"" + password + "\"}";
                connection.getOutputStream().write(jsonInput.getBytes("UTF-8"));
                connection.getOutputStream().flush();
                connection.getOutputStream().close();

                int status = connection.getResponseCode();
                InputStream stream;

                // Đọc stream đúng chuẩn
                if (status >= 200 && status < 300) {
                    stream = connection.getInputStream();
                } else {
                    stream = connection.getErrorStream();
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder responseStr = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    responseStr.append(line);
                }

                reader.close();

                // Parse JSON server trả về
                JSONObject json = new JSONObject(responseStr.toString());

                if (status == 200) {
                    String message = json.getString("message");
                    String token = json.getString("accessToken");

                    saveToken(token);

                    mainHandler.post(() -> {
                        showMessage(message);
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    });
                } else {
                    // Server trả lỗi
                    String msg = json.has("message") ? json.getString("message") : "Login failed!";
                    mainHandler.post(() -> showMessage("Error " + status + ": " + msg));
                    handleFailedLogin();
                }

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> showMessage("Không thể kết nối server!"));
            } finally {
                mainHandler.post(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnLogin.setEnabled(true);
                });

                if (connection != null) connection.disconnect();
            }
        });
    }




    private void handleFailedLogin() {
        loginAttempts++;
        lastFailedAttempt = System.currentTimeMillis();

        int remainingAttempts = MAX_LOGIN_ATTEMPTS - loginAttempts;
        if (remainingAttempts > 0) {
            showMessage("Sai tên đăng nhập hoặc mật khẩu. Còn " + remainingAttempts + " lần thử");
        } else {
            showMessage("Tài khoản tạm khóa 5 phút do nhập sai quá nhiều lần");
        }
    }

    private void showMessage(String message) {
        mainHandler.post(() ->
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show()
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private void saveToken(String token) {
        getSharedPreferences("Auth", MODE_PRIVATE)
                .edit()
                .putString("access_token", token)
                .apply();
    }

}



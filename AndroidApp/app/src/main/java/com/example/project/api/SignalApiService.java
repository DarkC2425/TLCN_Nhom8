package com.example.project.api;

import android.content.Context;
import android.util.Base64; // FIXED: Use Android Base64
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * FIXED: Changed from java.util.Base64 to android.util.Base64
 * FIXED: Added proper error handling
 * FIXED: Added connection timeout
 */
public class SignalApiService {
    private static final String TAG = "SignalApiService";

    // IMPORTANT: Change this to your server URL
    private static final String BASE_URL = "http://10.0.2.2:3000/api/v1"; // Emulator
    // For real device: private static final String BASE_URL = "http://YOUR_IP:3000/api/v1";

    private static final int TIMEOUT = 15000; // 15 seconds

    private final String authToken;
    private final Context context;

    public SignalApiService(Context context, String authToken) {
        this.context = context;
        this.authToken = authToken;
    }

    // ==================== AUTHENTICATION ====================

    public RegisterResponse registerAccount(String username, String password,
                                            byte[] identityPublicKey,
                                            List<PreKeyBundle> preKeys) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            // FIXED: Use Android Base64
            body.put("identityKey", Base64.encodeToString(identityPublicKey, Base64.NO_WRAP));

            JSONArray preKeysArray = new JSONArray();
            for (PreKeyBundle preKey : preKeys) {
                JSONObject pk = new JSONObject();
                pk.put("keyId", preKey.preKeyId);
                // FIXED: Use Android Base64
                pk.put("publicKey", Base64.encodeToString(preKey.publicKey, Base64.NO_WRAP));
                preKeysArray.put(pk);
            }
            body.put("preKeys", preKeysArray);

            JSONObject response = post("/accounts/register", body, false);
            return new RegisterResponse(
                    response.getString("userId"),
                    response.getString("authToken")
            );
        } catch (Exception e) {
            throw new IOException("Registration failed: " + e.getMessage(), e);
        }
    }

    public LoginResponse login(String username, String password) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);

            JSONObject response = post("/accounts/login", body, false);
            return new LoginResponse(
                    response.getString("userId"),
                    response.getString("authToken"),
                    response.getLong("timestamp")
            );
        } catch (Exception e) {
            throw new IOException("Login failed: " + e.getMessage(), e);
        }
    }

    // ==================== PREKEY SERVICE ====================

    public void uploadPreKeys(List<PreKeyBundle> preKeys,
                              PreKeyBundle signedPreKey) throws IOException {
        JSONObject body = new JSONObject();
        try {
            JSONArray preKeysArray = new JSONArray();
            for (PreKeyBundle pk : preKeys) {
                JSONObject preKey = new JSONObject();
                preKey.put("keyId", pk.preKeyId);
                preKey.put("publicKey", Base64.encodeToString(pk.publicKey, Base64.NO_WRAP));
                preKeysArray.put(preKey);
            }
            body.put("preKeys", preKeysArray);

            JSONObject signedPK = new JSONObject();
            signedPK.put("keyId", signedPreKey.preKeyId);
            signedPK.put("publicKey", Base64.encodeToString(signedPreKey.publicKey, Base64.NO_WRAP));
            signedPK.put("signature", Base64.encodeToString(signedPreKey.signature, Base64.NO_WRAP));
            body.put("signedPreKey", signedPK);

            post("/keys/prekeys", body, true);
        } catch (Exception e) {
            throw new IOException("Upload prekeys failed: " + e.getMessage(), e);
        }
    }

    public PreKeyBundleResponse fetchPreKeyBundle(String recipientId) throws IOException {
        try {
            JSONObject response = get("/keys/prekeys/" + recipientId);

            return new PreKeyBundleResponse(
                    response.getString("recipientId"),
                    Base64.decode(response.getString("identityKey"), Base64.NO_WRAP),
                    response.getInt("deviceId"),
                    response.getInt("registrationId"),
                    Base64.decode(response.getJSONObject("signedPreKey").getString("publicKey"), Base64.NO_WRAP),
                    response.getJSONObject("signedPreKey").getInt("keyId"),
                    Base64.decode(response.getJSONObject("signedPreKey").getString("signature"), Base64.NO_WRAP),
                    response.has("preKey") ? Base64.decode(response.getJSONObject("preKey").getString("publicKey"), Base64.NO_WRAP) : null,
                    response.has("preKey") ? response.getJSONObject("preKey").getInt("keyId") : 0
            );
        } catch (Exception e) {
            throw new IOException("Fetch prekey bundle failed: " + e.getMessage(), e);
        }
    }

    // ==================== MESSAGE SERVICE ====================

    public SendMessageResponse sendMessage(String recipientId,
                                           int deviceId,
                                           byte[] encryptedMessage,
                                           int messageType) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("recipientId", recipientId);
            body.put("deviceId", deviceId);
            body.put("content", Base64.encodeToString(encryptedMessage, Base64.NO_WRAP));
            body.put("type", messageType);
            body.put("timestamp", System.currentTimeMillis());

            JSONObject response = post("/messages/send", body, true);
            return new SendMessageResponse(
                    response.getBoolean("success"),
                    response.getString("messageId"),
                    response.getLong("timestamp")
            );
        } catch (Exception e) {
            throw new IOException("Send message failed: " + e.getMessage(), e);
        }
    }

    public List<IncomingMessage> fetchMessages() throws IOException {
        try {
            JSONObject response = get("/messages/fetch");
            JSONArray messagesArray = response.getJSONArray("messages");

            List<IncomingMessage> messages = new ArrayList<>();
            for (int i = 0; i < messagesArray.length(); i++) {
                JSONObject msg = messagesArray.getJSONObject(i);
                messages.add(new IncomingMessage(
                        msg.getString("messageId"),
                        msg.getString("senderId"),
                        msg.getInt("deviceId"),
                        Base64.decode(msg.getString("content"), Base64.NO_WRAP),
                        msg.getInt("type"),
                        msg.getLong("timestamp")
                ));
            }
            return messages;
        } catch (Exception e) {
            throw new IOException("Fetch messages failed: " + e.getMessage(), e);
        }
    }

    public void acknowledgeMessage(String messageId) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("messageId", messageId);
            post("/messages/ack", body, true);
        } catch (Exception e) {
            throw new IOException("Acknowledge failed: " + e.getMessage(), e);
        }
    }

    // ==================== PROFILE SERVICE ====================

    public UserProfile fetchProfile(String userId) throws IOException {
        try {
            JSONObject response = get("/users/profile/" + userId);
            return new UserProfile(
                    response.getString("userId"),
                    response.getString("username"),
                    response.optString("displayName", ""),
                    response.optString("avatarUrl", ""),
                    Base64.decode(response.getString("identityKey"), Base64.NO_WRAP),
                    response.getInt("registrationId")
            );
        } catch (Exception e) {
            throw new IOException("Fetch profile failed: " + e.getMessage(), e);
        }
    }

    public void updateProfile(String displayName, String avatarUrl) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("displayName", displayName);
            body.put("avatarUrl", avatarUrl);
            post("/users/profile/update", body, true);
        } catch (Exception e) {
            throw new IOException("Update profile failed: " + e.getMessage(), e);
        }
    }

    // ADDED: Search users
    public List<UserProfile> searchUsers(String query) throws IOException {
        try {
            JSONObject response = get("/users/search?query=" + query);
            JSONArray usersArray = response.getJSONArray("users");

            List<UserProfile> users = new ArrayList<>();
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                users.add(new UserProfile(
                        user.getString("userId"),
                        user.getString("username"),
                        user.optString("displayName", ""),
                        user.optString("avatarUrl", ""),
                        null, // No identity key in search results
                        0
                ));
            }
            return users;
        } catch (Exception e) {
            throw new IOException("Search users failed: " + e.getMessage(), e);
        }
    }

    // ==================== HTTP HELPERS ====================

    private JSONObject get(String endpoint) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);

            if (authToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            return readResponse(conn);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private JSONObject post(String endpoint, JSONObject body, boolean needsAuth) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setDoOutput(true);

            if (needsAuth && authToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            return readResponse(conn);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private JSONObject readResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        InputStream is = responseCode < 400 ? conn.getInputStream() : conn.getErrorStream();

        if (is == null) {
            throw new IOException("No response from server");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        if (responseCode >= 400) {
            throw new IOException("HTTP " + responseCode + ": " + response.toString());
        }

        try {
            return new JSONObject(response.toString());
        } catch (Exception e) {
            throw new IOException("Invalid JSON response: " + response.toString(), e);
        }
    }

    // ==================== DATA CLASSES ====================

    public static class PreKeyBundle {
        public int preKeyId;
        public byte[] publicKey;
        public byte[] signature;

        public PreKeyBundle(int preKeyId, byte[] publicKey) {
            this.preKeyId = preKeyId;
            this.publicKey = publicKey;
        }

        public PreKeyBundle(int preKeyId, byte[] publicKey, byte[] signature) {
            this.preKeyId = preKeyId;
            this.publicKey = publicKey;
            this.signature = signature;
        }
    }

    public static class RegisterResponse {
        public String userId;
        public String authToken;

        public RegisterResponse(String userId, String authToken) {
            this.userId = userId;
            this.authToken = authToken;
        }
    }

    public static class LoginResponse {
        public String userId;
        public String authToken;
        public long timestamp;

        public LoginResponse(String userId, String authToken, long timestamp) {
            this.userId = userId;
            this.authToken = authToken;
            this.timestamp = timestamp;
        }
    }

    public static class PreKeyBundleResponse {
        public String recipientId;
        public byte[] identityKey;
        public int deviceId;
        public int registrationId;
        public byte[] signedPreKeyPublic;
        public int signedPreKeyId;
        public byte[] signedPreKeySignature;
        public byte[] preKeyPublic;
        public int preKeyId;

        public PreKeyBundleResponse(String recipientId, byte[] identityKey,
                                    int deviceId, int registrationId,
                                    byte[] signedPreKeyPublic, int signedPreKeyId,
                                    byte[] signedPreKeySignature,
                                    byte[] preKeyPublic, int preKeyId) {
            this.recipientId = recipientId;
            this.identityKey = identityKey;
            this.deviceId = deviceId;
            this.registrationId = registrationId;
            this.signedPreKeyPublic = signedPreKeyPublic;
            this.signedPreKeyId = signedPreKeyId;
            this.signedPreKeySignature = signedPreKeySignature;
            this.preKeyPublic = preKeyPublic;
            this.preKeyId = preKeyId;
        }
    }

    public static class SendMessageResponse {
        public boolean success;
        public String messageId;
        public long timestamp;

        public SendMessageResponse(boolean success, String messageId, long timestamp) {
            this.success = success;
            this.messageId = messageId;
            this.timestamp = timestamp;
        }
    }

    public static class IncomingMessage {
        public String messageId;
        public String senderId;
        public int deviceId;
        public byte[] content;
        public int type;
        public long timestamp;

        public IncomingMessage(String messageId, String senderId, int deviceId,
                               byte[] content, int type, long timestamp) {
            this.messageId = messageId;
            this.senderId = senderId;
            this.deviceId = deviceId;
            this.content = content;
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    public static class UserProfile {
        public String userId;
        public String username;
        public String displayName;
        public String avatarUrl;
        public byte[] identityKey;
        public int registrationId;

        public UserProfile(String userId, String username, String displayName,
                           String avatarUrl, byte[] identityKey, int registrationId) {
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
            this.identityKey = identityKey;
            this.registrationId = registrationId;
        }
    }

    public interface MessageListener {
        void onMessageReceived(IncomingMessage message);
        void onError(Exception e);
    }
}
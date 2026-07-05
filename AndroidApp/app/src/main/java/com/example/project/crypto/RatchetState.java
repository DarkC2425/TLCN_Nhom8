package com.example.project.crypto;

import android.util.Base64;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

/**
 * Updated RatchetState with Serialization support
 */
public class RatchetState {
    public byte[] rootKey; // 32 bytes
    public byte[] ourPriv; // 32
    private byte[] ourPub;  // 32
    private byte[] theirPub; // 32 or null

    public ChainKey sendingChain;
    public ChainKey receivingChain;

    // Skipped keys: key = base64(theirPub)+":"+msgNum
    private final Map<String, MessageKey> skippedMessageKeys = new HashMap<>();
    private static final int MAX_SKIPPED_KEYS = 1000;

    public RatchetState(byte[] rootKey,
                        byte[] ourPriv, byte[] ourPub,
                        byte[] theirPub,
                        ChainKey sendingChain, ChainKey receivingChain) {
        this.rootKey = copyArray(rootKey);
        this.ourPriv = copyArray(ourPriv);
        this.ourPub = copyArray(ourPub);
        this.theirPub = theirPub == null ? null : copyArray(theirPub);
        this.sendingChain = sendingChain;
        this.receivingChain = receivingChain;
    }

    private byte[] copyArray(byte[] array) {
        if (array == null) return null;
        return Arrays.copyOf(array, array.length);
    }

    // ==================== SERIALIZATION ====================

    /**
     * Serialize RatchetState to byte array for storage
     */
    public byte[] serialize() {
        try {
            JSONObject json = new JSONObject();

            // Root key
            json.put("rootKey", Base64.encodeToString(rootKey, Base64.NO_WRAP));

            // Our keys
            json.put("ourPriv", Base64.encodeToString(ourPriv, Base64.NO_WRAP));
            json.put("ourPub", Base64.encodeToString(ourPub, Base64.NO_WRAP));

            // Their pub
            if (theirPub != null) {
                json.put("theirPub", Base64.encodeToString(theirPub, Base64.NO_WRAP));
            }

            // Sending chain
            JSONObject sendingChainJson = new JSONObject();
            sendingChainJson.put("chainKey", Base64.encodeToString(sendingChain.getCK(), Base64.NO_WRAP));
            sendingChainJson.put("index", sendingChain.getIndex());
            json.put("sendingChain", sendingChainJson);

            // Receiving chain
            JSONObject receivingChainJson = new JSONObject();
            receivingChainJson.put("chainKey", Base64.encodeToString(receivingChain.getCK(), Base64.NO_WRAP));
            receivingChainJson.put("index", receivingChain.getIndex());
            json.put("receivingChain", receivingChainJson);

            // Skipped message keys
            JSONObject skippedKeysJson = new JSONObject();
            for (Map.Entry<String, MessageKey> entry : skippedMessageKeys.entrySet()) {
                JSONObject mkJson = new JSONObject();
                mkJson.put("key", Base64.encodeToString(entry.getValue().getKey(), Base64.NO_WRAP));
                mkJson.put("index", entry.getValue().getIndex());
                skippedKeysJson.put(entry.getKey(), mkJson);
            }
            json.put("skippedKeys", skippedKeysJson);

            return json.toString().getBytes("UTF-8");

        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    /**
     * Deserialize RatchetState from byte array
     */
    public static RatchetState deserialize(byte[] data) {
        try {
            JSONObject json = new JSONObject(new String(data, "UTF-8"));

            // Root key
            byte[] rootKey = Base64.decode(json.getString("rootKey"), Base64.NO_WRAP);

            // Our keys
            byte[] ourPriv = Base64.decode(json.getString("ourPriv"), Base64.NO_WRAP);
            byte[] ourPub = Base64.decode(json.getString("ourPub"), Base64.NO_WRAP);

            // Their pub
            byte[] theirPub = null;
            if (json.has("theirPub")) {
                theirPub = Base64.decode(json.getString("theirPub"), Base64.NO_WRAP);
            }

            // Sending chain
            JSONObject sendingChainJson = json.getJSONObject("sendingChain");
            byte[] sendingCK = Base64.decode(sendingChainJson.getString("chainKey"), Base64.NO_WRAP);
            int sendingIndex = sendingChainJson.getInt("index");
            ChainKey sendingChain = new ChainKey(sendingCK, sendingIndex);

            // Receiving chain
            JSONObject receivingChainJson = json.getJSONObject("receivingChain");
            byte[] receivingCK = Base64.decode(receivingChainJson.getString("chainKey"), Base64.NO_WRAP);
            int receivingIndex = receivingChainJson.getInt("index");
            ChainKey receivingChain = new ChainKey(receivingCK, receivingIndex);

            // Create state
            RatchetState state = new RatchetState(
                    rootKey, ourPriv, ourPub, theirPub,
                    sendingChain, receivingChain
            );

            // Restore skipped keys
            if (json.has("skippedKeys")) {
                JSONObject skippedKeysJson = json.getJSONObject("skippedKeys");
                java.util.Iterator<String> keys = skippedKeysJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject mkJson = skippedKeysJson.getJSONObject(key);
                    byte[] mkKey = Base64.decode(mkJson.getString("key"), Base64.NO_WRAP);
                    int mkIndex = mkJson.getInt("index");
                    state.skippedMessageKeys.put(key, new MessageKey(mkKey, mkIndex));
                }
            }

            return state;

        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    // ==================== DOUBLE RATCHET OPERATIONS ====================

    public void doDHratchet(byte[] theirNewPub) {
        try {
            // 1) DH between ourPriv and theirNewPub -> shared
            byte[] dh = X25519Util.dh(ourPriv, theirNewPub);

            // 2) KDF: newRoot, newReceivingCK = HKDF(rootKey || dh)
            byte[] input = new byte[rootKey.length + dh.length];
            System.arraycopy(rootKey, 0, input, 0, rootKey.length);
            System.arraycopy(dh, 0, input, rootKey.length, dh.length);

            byte[] rk_ck = CryptoUtils.hkdf(null, input, "RatchetStep".getBytes(), CryptoUtils.KEY_LEN * 2);
            byte[] newRoot = Arrays.copyOfRange(rk_ck, 0, CryptoUtils.KEY_LEN);
            byte[] newRecvCK = Arrays.copyOfRange(rk_ck, CryptoUtils.KEY_LEN, CryptoUtils.KEY_LEN * 2);

            // Clean up
            clearArray(dh);
            clearArray(input);
            clearArray(rk_ck);

            // Update
            clearArray(this.rootKey);
            this.rootKey = newRoot;
            this.receivingChain = new ChainKey(newRecvCK, 0);

            // Rotate our ephemeral keypair
            X25519Util.KeyPair kp = X25519Util.generateKeyPair();
            clearArray(this.ourPriv);
            this.ourPriv = kp.priv;
            clearArray(this.ourPub);
            this.ourPub = kp.pub;

            // 3) DH between our new ephemeral and theirNewPub
            byte[] dh2 = X25519Util.dh(this.ourPriv, theirNewPub);
            byte[] rk_ck2 = CryptoUtils.hkdf(null, dh2, "RatchetStep".getBytes(), CryptoUtils.KEY_LEN * 2);
            byte[] newRoot2 = Arrays.copyOfRange(rk_ck2, 0, CryptoUtils.KEY_LEN);
            byte[] newSendCK = Arrays.copyOfRange(rk_ck2, CryptoUtils.KEY_LEN, CryptoUtils.KEY_LEN * 2);

            // Clean up
            clearArray(dh2);
            clearArray(rk_ck2);
            clearArray(newRecvCK);

            // Update
            clearArray(this.rootKey);
            this.rootKey = newRoot2;
            this.sendingChain = new ChainKey(newSendCK, 0);

            // Update their pub
            if (this.theirPub != null) {
                clearArray(this.theirPub);
            }
            this.theirPub = copyArray(theirNewPub);

        } catch (Exception ex) {
            throw new RuntimeException("DH ratchet failed", ex);
        }
    }

    public MessageKey nextSendingMessageKey() {
        if (sendingChain == null) {
            throw new IllegalStateException("Sending chain not initialized");
        }
        return sendingChain.deriveMessageKey();
    }

    public MessageKey nextReceivingMessageKey() {
        if (receivingChain == null) {
            throw new IllegalStateException("Receiving chain not initialized");
        }
        return receivingChain.deriveMessageKey();
    }

    public void saveSkippedMessageKey(byte[] theirPub, int msgNum, MessageKey mk) {
        if (skippedMessageKeys.size() >= MAX_SKIPPED_KEYS) {
            String oldestKey = skippedMessageKeys.keySet().iterator().next();
            skippedMessageKeys.remove(oldestKey);
        }

        String k = keyFor(theirPub, msgNum);
        skippedMessageKeys.put(k, mk);
    }

    public MessageKey retrieveSkippedMessageKey(byte[] theirPub, int msgNum) {
        String k = keyFor(theirPub, msgNum);
        return skippedMessageKeys.remove(k);
    }

    private String keyFor(byte[] theirPubBytes, int num) {
        String b64 = Base64.encodeToString(theirPubBytes, Base64.NO_WRAP);
        return b64 + ":" + num;
    }

    private void clearArray(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
        }
    }

    public void destroy() {
        clearArray(rootKey);
        clearArray(ourPriv);
        clearArray(ourPub);
        clearArray(theirPub);

        for (MessageKey mk : skippedMessageKeys.values()) {
            mk.destroy();
        }
        skippedMessageKeys.clear();

        if (sendingChain != null) {
            sendingChain.destroy();
        }
        if (receivingChain != null) {
            receivingChain.destroy();
        }
    }

    // Getters
    public byte[] getOurPub() {
        return copyArray(ourPub);
    }

    public byte[] getTheirPub() {
        return theirPub == null ? null : copyArray(theirPub);
    }

    public static RatchetState initFromSharedSecret(byte[] sharedSecret) {
        if (sharedSecret == null || sharedSecret.length != CryptoUtils.KEY_LEN) {
            throw new IllegalArgumentException("Invalid shared secret");
        }

        byte[] okm = CryptoUtils.hkdf(null, sharedSecret, "Init".getBytes(), CryptoUtils.KEY_LEN * 3);
        byte[] root = new byte[CryptoUtils.KEY_LEN];
        byte[] sendCK = new byte[CryptoUtils.KEY_LEN];
        byte[] recvCK = new byte[CryptoUtils.KEY_LEN];
        System.arraycopy(okm, 0, root, 0, CryptoUtils.KEY_LEN);
        System.arraycopy(okm, CryptoUtils.KEY_LEN, sendCK, 0, CryptoUtils.KEY_LEN);
        System.arraycopy(okm, CryptoUtils.KEY_LEN*2, recvCK, 0, CryptoUtils.KEY_LEN);

        Arrays.fill(okm, (byte) 0);

        X25519Util.KeyPair kp = X25519Util.generateKeyPair();
        return new RatchetState(root, kp.priv, kp.pub, null,
                new ChainKey(sendCK, 0), new ChainKey(recvCK, 0));
    }
}
package com.example.project.protocol;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.project.crypto.RatchetMessenger;
import com.example.project.api.SignalApiService;
import com.example.project.storage.SignalProtocolStore;
import com.example.project.crypto.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main Signal Protocol Manager
 * Handles all protocol operations
 */
public class SignalProtocolManager {
    private static final String TAG = "SignalProtocolManager";

    private final Context context;
    private final SignalProtocolStore store;
    private final SignalApiService apiService;
    private final String userId;

    public SignalProtocolManager(Context context, String userId, String authToken) {
        this.context = context;
        this.userId = userId;
        this.store = new SignalProtocolStore(context);
        this.apiService = new SignalApiService(context, authToken);
    }

    /**
     * Initialize Signal Protocol
     * Generate identity, prekeys, signed prekeys
     */
    public void initialize() throws Exception {
        Log.d(TAG, "Initializing Signal Protocol");

        // 1. Check if already initialized
        if (store.getIdentityKeyPair() != null) {
            Log.d(TAG, "Already initialized");
            return;
        }

        // 2. Generate registration ID (random 14-bit number)
        int registrationId = CryptoUtils.randomInt() & 0x3FFF;

        // 3. Generate identity key pair
        X25519Util.KeyPair identityKey = X25519Util.generateKeyPair();
        store.saveIdentity(registrationId, identityKey.pub, identityKey.priv);

        // 4. Generate 100 prekeys
        List<SignalApiService.PreKeyBundle> preKeys = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            X25519Util.KeyPair preKey = X25519Util.generateKeyPair();
            store.savePreKey(i, preKey.pub, preKey.priv);
            preKeys.add(new SignalApiService.PreKeyBundle(i, preKey.pub));
        }

        // 5. Generate signed prekey
        X25519Util.KeyPair signedPreKey = X25519Util.generateKeyPair();
        byte[] signature = signPreKey(signedPreKey.pub, identityKey.priv);
        store.saveSignedPreKey(1, signedPreKey.pub, signedPreKey.priv, signature);

        // 6. Upload to server
        SignalApiService.PreKeyBundle signedPK = new SignalApiService.PreKeyBundle(
                1, signedPreKey.pub, signature);
        apiService.uploadPreKeys(preKeys, signedPK);

        Log.d(TAG, "Signal Protocol initialized successfully");
    }
    public byte[] getIdentityPublicKey() throws Exception{
        return store.getIdentityKeyPair().publicKey;
    }
    public List<SignalApiService.PreKeyBundle> getPreKeysForUpload() throws Exception{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            SignalApiService.PreKeyBundle a = new SignalApiService.PreKeyBundle(store.getPreKeyIds().getFirst(), store.getIdentityKeyPair().publicKey);
        }
        return null;
    }
    /**
     * Send encrypted message to recipient
     */
    public String sendMessage(String recipientId, String plaintext) throws Exception {
        // 1. Check if we have a session
        if (!store.containsSession(recipientId, 1)) {
            // No session, need to do X3DH key exchange
            performX3DHKeyExchange(recipientId);
        }

        // 2. Load session
        byte[] sessionData = store.loadSession(recipientId, 1);
        RatchetState state = RatchetState.deserialize(sessionData);

        // 3. Encrypt message
        RatchetMessenger messenger = new RatchetMessenger(state);
        byte[] ciphertext = messenger.encrypt(plaintext.getBytes("UTF-8"));

        // 4. Save updated session
        store.saveSession(recipientId, 1, state.serialize());

        // 5. Send to server
        SignalApiService.SendMessageResponse response = apiService.sendMessage(
                recipientId, 1, ciphertext, 3); // Type 3 = normal message

        return response.messageId;
    }

    /**
     * Decrypt received message
     */
    public String decryptMessage(String senderId, int deviceId, byte[] ciphertext) throws Exception {
        // 1. Load session
        if (!store.containsSession(senderId, deviceId)) {
            throw new Exception("No session for sender: " + senderId);
        }

        byte[] sessionData = store.loadSession(senderId, deviceId);
        RatchetState state = RatchetState.deserialize(sessionData);

        // 2. Decrypt
        RatchetMessenger messenger = new RatchetMessenger(state);
        byte[] plaintext = messenger.decrypt(ciphertext);

        // 3. Save updated session
        store.saveSession(senderId, deviceId, state.serialize());

        return new String(plaintext, "UTF-8");
    }

    /**
     * Perform X3DH Key Exchange
     */
    private void performX3DHKeyExchange(String recipientId) throws Exception {
        Log.d(TAG, "Performing X3DH with " + recipientId);

        // 1. Fetch recipient's prekey bundle from server
        SignalApiService.PreKeyBundleResponse bundle = apiService.fetchPreKeyBundle(recipientId);

        // 2. Verify identity key (check against trust store)
        byte[] savedIdentity = store.getRecipientIdentity(recipientId);
        if (savedIdentity != null && !Arrays.equals(savedIdentity, bundle.identityKey)) {
            throw new Exception("Identity key mismatch! Possible MITM attack!");
        }

        // 3. Save recipient's identity key
        if (savedIdentity == null) {
            store.saveRecipientIdentity(recipientId, bundle.identityKey);
        }

        // 4. Perform X3DH calculation
        X3DHKeyExchange x3dh = new X3DHKeyExchange();
        byte[] sharedSecret = x3dh.calculateInitiatorSecret(
                store.getIdentityKeyPair(),
                bundle.identityKey,
                bundle.signedPreKeyPublic,
                bundle.preKeyPublic
        );

        // 5. Initialize Double Ratchet session
        RatchetState state = RatchetState.initFromSharedSecret(sharedSecret);

        // Update with recipient's signed prekey
        state = new RatchetState(
                state.rootKey,
                state.ourPriv,
                state.getOurPub(),
                bundle.signedPreKeyPublic, // Use signed prekey as their pub
                state.sendingChain,
                state.receivingChain
        );

        // 6. Save session
        store.saveSession(recipientId, bundle.deviceId, state.serialize());

        Log.d(TAG, "X3DH completed, session established");
    }

    /**
     * Process prekey message (initial message with X3DH)
     */
    public String processPreKeyMessage(String senderId, int deviceId,
                                       byte[] prekeyMessage) throws Exception {
        Log.d(TAG, "Processing prekey message from " + senderId);

        // Parse prekey message format:
        // [1 byte version][32 bytes sender identity][32 bytes ephemeral][4 bytes prekey id][encrypted message]

        ByteBuffer buffer = ByteBuffer.wrap(prekeyMessage);
        byte version = buffer.get();

        byte[] senderIdentity = new byte[32];
        buffer.get(senderIdentity);

        byte[] senderEphemeral = new byte[32];
        buffer.get(senderEphemeral);

        int preKeyId = buffer.getInt();

        byte[] encryptedMessage = new byte[buffer.remaining()];
        buffer.get(encryptedMessage);

        // Verify sender's identity
        byte[] savedIdentity = store.getRecipientIdentity(senderId);
        if (savedIdentity != null && !Arrays.equals(savedIdentity, senderIdentity)) {
            throw new Exception("Identity key mismatch!");
        }

        if (savedIdentity == null) {
            store.saveRecipientIdentity(senderId, senderIdentity);
        }

        // Load our prekey
        SignalProtocolStore.PreKeyRecord preKey = store.loadPreKey(preKeyId);
        if (preKey == null) {
            throw new Exception("PreKey not found: " + preKeyId);
        }

        // Perform X3DH as responder
        X3DHKeyExchange x3dh = new X3DHKeyExchange();
        byte[] sharedSecret = x3dh.calculateResponderSecret(
                store.getIdentityKeyPair(),
                senderIdentity,
                senderEphemeral,
                preKey.privateKey
        );

        // Initialize session
        RatchetState state = RatchetState.initFromSharedSecret(sharedSecret);
        state = new RatchetState(
                state.rootKey,
                state.ourPriv,
                state.getOurPub(),
                senderEphemeral,
                state.sendingChain,
                state.receivingChain
        );

        // Save session
        store.saveSession(senderId, deviceId, state.serialize());

        // Delete used prekey
        store.removePreKey(preKeyId);

        // Decrypt message
        RatchetMessenger messenger = new RatchetMessenger(state);
        byte[] plaintext = messenger.decrypt(encryptedMessage);

        // Save updated session
        store.saveSession(senderId, deviceId, state.serialize());

        return new String(plaintext, "UTF-8");
    }

    /**
     * Sign prekey with identity key
     */
    private byte[] signPreKey(byte[] preKeyPublic, byte[] identityPrivate) throws Exception {
        // Simple signature: HMAC-SHA256(identityPrivate, preKeyPublic)
        return CryptoUtils.hkdf(identityPrivate, preKeyPublic, "SignedPreKey".getBytes(), 64);
    }
}
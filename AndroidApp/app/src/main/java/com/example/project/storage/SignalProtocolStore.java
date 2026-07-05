package com.example.project.storage;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.os.Build;
import android.util.Log;

import java.util.*;

/**
 * Signal Protocol Local Storage
 * Stores all cryptographic state locally using SQLite
 */
public class SignalProtocolStore extends SQLiteOpenHelper {
    private static final String TAG = "SignalProtocolStore";
    private static final String DATABASE_NAME = "signal_protocol.db";
    private static final int DATABASE_VERSION = 1;

    // ==================== TABLES ====================

    // Identity Keys
    private static final String TABLE_IDENTITY = "identity_keys";
    private static final String TABLE_IDENTITY_KEYS = "recipient_identity_keys";

    // PreKeys
    private static final String TABLE_PREKEYS = "prekeys";
    private static final String TABLE_SIGNED_PREKEYS = "signed_prekeys";

    // Sessions (Double Ratchet State)
    private static final String TABLE_SESSIONS = "sessions";

    // Message Keys (for out-of-order messages)
    private static final String TABLE_MESSAGE_KEYS = "message_keys";

    // Sender Keys (for group messages - future)
    private static final String TABLE_SENDER_KEYS = "sender_keys";

    public SignalProtocolStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Identity table - Our own identity
        db.execSQL("CREATE TABLE " + TABLE_IDENTITY + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "registration_id INTEGER NOT NULL, "
                + "identity_public_key BLOB NOT NULL, "
                + "identity_private_key BLOB NOT NULL, "
                + "created_at INTEGER NOT NULL)");

        // Recipient identity keys - Trust store
        db.execSQL("CREATE TABLE " + TABLE_IDENTITY_KEYS + " ("
                + "recipient_id TEXT PRIMARY KEY, "
                + "identity_key BLOB NOT NULL, "
                + "verified INTEGER DEFAULT 0, " // 0=unverified, 1=verified
                + "first_use INTEGER NOT NULL, "
                + "timestamp INTEGER NOT NULL)");

        // PreKeys table
        db.execSQL("CREATE TABLE " + TABLE_PREKEYS + " ("
                + "prekey_id INTEGER PRIMARY KEY, "
                + "public_key BLOB NOT NULL, "
                + "private_key BLOB NOT NULL, "
                + "created_at INTEGER NOT NULL)");

        // Signed PreKeys table
        db.execSQL("CREATE TABLE " + TABLE_SIGNED_PREKEYS + " ("
                + "prekey_id INTEGER PRIMARY KEY, "
                + "public_key BLOB NOT NULL, "
                + "private_key BLOB NOT NULL, "
                + "signature BLOB NOT NULL, "
                + "created_at INTEGER NOT NULL)");

        // Sessions table - Double Ratchet State
        db.execSQL("CREATE TABLE " + TABLE_SESSIONS + " ("
                + "recipient_id TEXT NOT NULL, "
                + "device_id INTEGER NOT NULL, "
                + "session_data BLOB NOT NULL, " // Serialized RatchetState
                + "timestamp INTEGER NOT NULL, "
                + "PRIMARY KEY (recipient_id, device_id))");

        // Message keys for out-of-order messages
        db.execSQL("CREATE TABLE " + TABLE_MESSAGE_KEYS + " ("
                + "recipient_id TEXT NOT NULL, "
                + "device_id INTEGER NOT NULL, "
                + "chain_key BLOB NOT NULL, "
                + "counter INTEGER NOT NULL, "
                + "message_key BLOB NOT NULL, "
                + "timestamp INTEGER NOT NULL, "
                + "PRIMARY KEY (recipient_id, device_id, chain_key, counter))");

        // Sender keys for group messages
        db.execSQL("CREATE TABLE " + TABLE_SENDER_KEYS + " ("
                + "group_id TEXT NOT NULL, "
                + "sender_id TEXT NOT NULL, "
                + "device_id INTEGER NOT NULL, "
                + "sender_key_data BLOB NOT NULL, "
                + "timestamp INTEGER NOT NULL, "
                + "PRIMARY KEY (group_id, sender_id, device_id))");

        Log.d(TAG, "Signal Protocol database created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop all tables
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_IDENTITY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_IDENTITY_KEYS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PREKEYS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SIGNED_PREKEYS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGE_KEYS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SENDER_KEYS);
        onCreate(db);
    }

    // ==================== IDENTITY KEY STORE ====================

    /**
     * Get local registration ID
     */
    public int getLocalRegistrationId() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_IDENTITY, new String[]{"registration_id"},
                null, null, null, null, "id DESC", "1");

        int regId = 0;
        if (cursor.moveToFirst()) {
            regId = cursor.getInt(0);
        }
        cursor.close();
        return regId;
    }

    /**
     * Save local identity key pair
     */
    public void saveIdentity(int registrationId, byte[] publicKey, byte[] privateKey) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("registration_id", registrationId);
        values.put("identity_public_key", publicKey);
        values.put("identity_private_key", privateKey);
        values.put("created_at", System.currentTimeMillis());
        db.insert(TABLE_IDENTITY, null, values);
    }

    /**
     * Get local identity key pair
     */
    public IdentityKeyPair getIdentityKeyPair() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_IDENTITY,
                new String[]{"identity_public_key", "identity_private_key"},
                null, null, null, null, "id DESC", "1");

        IdentityKeyPair keyPair = null;
        if (cursor.moveToFirst()) {
            byte[] publicKey = cursor.getBlob(0);
            byte[] privateKey = cursor.getBlob(1);
            keyPair = new IdentityKeyPair(publicKey, privateKey);
        }
        cursor.close();
        return keyPair;
    }

    /**
     * Save recipient's identity key
     */
    public void saveRecipientIdentity(String recipientId, byte[] identityKey) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("recipient_id", recipientId);
        values.put("identity_key", identityKey);
        values.put("first_use", System.currentTimeMillis());
        values.put("timestamp", System.currentTimeMillis());

        db.insertWithOnConflict(TABLE_IDENTITY_KEYS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Get recipient's identity key
     */
    public byte[] getRecipientIdentity(String recipientId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_IDENTITY_KEYS,
                new String[]{"identity_key"},
                "recipient_id = ?", new String[]{recipientId},
                null, null, null);

        byte[] identityKey = null;
        if (cursor.moveToFirst()) {
            identityKey = cursor.getBlob(0);
        }
        cursor.close();
        return identityKey;
    }

    /**
     * Mark identity as verified (after QR code scan or safety number check)
     */
    public void markIdentityVerified(String recipientId, boolean verified) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("verified", verified ? 1 : 0);
        db.update(TABLE_IDENTITY_KEYS, values,
                "recipient_id = ?", new String[]{recipientId});
    }

    // ==================== PREKEY STORE ====================

    /**
     * Save prekey
     */
    public void savePreKey(int preKeyId, byte[] publicKey, byte[] privateKey) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("prekey_id", preKeyId);
        values.put("public_key", publicKey);
        values.put("private_key", privateKey);
        values.put("created_at", System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_PREKEYS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Load prekey
     */
    public PreKeyRecord loadPreKey(int preKeyId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PREKEYS,
                new String[]{"public_key", "private_key"},
                "prekey_id = ?", new String[]{String.valueOf(preKeyId)},
                null, null, null);

        PreKeyRecord record = null;
        if (cursor.moveToFirst()) {
            byte[] publicKey = cursor.getBlob(0);
            byte[] privateKey = cursor.getBlob(1);
            record = new PreKeyRecord(preKeyId, publicKey, privateKey);
        }
        cursor.close();
        return record;
    }

    /**
     * Remove used prekey
     */
    public void removePreKey(int preKeyId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_PREKEYS, "prekey_id = ?",
                new String[]{String.valueOf(preKeyId)});
    }

    /**
     * Get all prekey IDs
     */
    public List<Integer> getPreKeyIds() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PREKEYS,
                new String[]{"prekey_id"},
                null, null, null, null, null);

        List<Integer> ids = new ArrayList<>();
        while (cursor.moveToNext()) {
            ids.add(cursor.getInt(0));
        }
        cursor.close();
        return ids;
    }

    // ==================== SIGNED PREKEY STORE ====================

    /**
     * Save signed prekey
     */
    public void saveSignedPreKey(int signedPreKeyId, byte[] publicKey,
                                 byte[] privateKey, byte[] signature) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("prekey_id", signedPreKeyId);
        values.put("public_key", publicKey);
        values.put("private_key", privateKey);
        values.put("signature", signature);
        values.put("created_at", System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_SIGNED_PREKEYS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Load signed prekey
     */
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SIGNED_PREKEYS,
                new String[]{"public_key", "private_key", "signature"},
                "prekey_id = ?", new String[]{String.valueOf(signedPreKeyId)},
                null, null, null);

        SignedPreKeyRecord record = null;
        if (cursor.moveToFirst()) {
            byte[] publicKey = cursor.getBlob(0);
            byte[] privateKey = cursor.getBlob(1);
            byte[] signature = cursor.getBlob(2);
            record = new SignedPreKeyRecord(signedPreKeyId, publicKey, privateKey, signature);
        }
        cursor.close();
        return record;
    }

    /**
     * Get all signed prekey IDs
     */
    public List<Integer> getSignedPreKeyIds() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SIGNED_PREKEYS,
                new String[]{"prekey_id"},
                null, null, null, null, null);

        List<Integer> ids = new ArrayList<>();
        while (cursor.moveToNext()) {
            ids.add(cursor.getInt(0));
        }
        cursor.close();
        return ids;
    }

    // ==================== SESSION STORE ====================

    /**
     * Save session (Double Ratchet state)
     */
    public void saveSession(String recipientId, int deviceId, byte[] sessionData) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("recipient_id", recipientId);
        values.put("device_id", deviceId);
        values.put("session_data", sessionData);
        values.put("timestamp", System.currentTimeMillis());

        db.insertWithOnConflict(TABLE_SESSIONS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Load session
     */
    public byte[] loadSession(String recipientId, int deviceId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SESSIONS,
                new String[]{"session_data"},
                "recipient_id = ? AND device_id = ?",
                new String[]{recipientId, String.valueOf(deviceId)},
                null, null, null);

        byte[] sessionData = null;
        if (cursor.moveToFirst()) {
            sessionData = cursor.getBlob(0);
        }
        cursor.close();
        return sessionData;
    }

    /**
     * Check if session exists
     */
    public boolean containsSession(String recipientId, int deviceId) {
        return loadSession(recipientId, deviceId) != null;
    }

    /**
     * Delete session
     */
    public void deleteSession(String recipientId, int deviceId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SESSIONS,
                "recipient_id = ? AND device_id = ?",
                new String[]{recipientId, String.valueOf(deviceId)});
    }

    /**
     * Delete all sessions for recipient
     */
    public void deleteAllSessions(String recipientId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SESSIONS,
                "recipient_id = ?",
                new String[]{recipientId});
    }

    /**
     * Get device IDs for recipient
     */
    public List<Integer> getDeviceIds(String recipientId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SESSIONS,
                new String[]{"device_id"},
                "recipient_id = ?", new String[]{recipientId},
                null, null, null);

        List<Integer> deviceIds = new ArrayList<>();
        while (cursor.moveToNext()) {
            deviceIds.add(cursor.getInt(0));
        }
        cursor.close();
        return deviceIds;
    }

    // ==================== MESSAGE KEY STORE ====================

    /**
     * Store message key for out-of-order messages
     */
    public void storeMessageKey(String recipientId, int deviceId,
                                byte[] chainKey, int counter, byte[] messageKey) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("recipient_id", recipientId);
        values.put("device_id", deviceId);
        values.put("chain_key", chainKey);
        values.put("counter", counter);
        values.put("message_key", messageKey);
        values.put("timestamp", System.currentTimeMillis());

        db.insertWithOnConflict(TABLE_MESSAGE_KEYS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Load and remove message key
     */
    public byte[] loadMessageKey(String recipientId, int deviceId,
                                 byte[] chainKey, int counter) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cursor = db.query(TABLE_MESSAGE_KEYS,
                    new String[]{"message_key"},
                    "recipient_id = ? AND device_id = ? AND chain_key = ? AND counter = ?",
                    new String[]{recipientId, String.valueOf(deviceId),
                            Base64.getEncoder().encodeToString(chainKey),
                            String.valueOf(counter)},
                    null, null, null);
        }

        byte[] messageKey = null;
        if (cursor.moveToFirst()) {
            messageKey = cursor.getBlob(0);
            // Remove after loading
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                db.delete(TABLE_MESSAGE_KEYS,
                        "recipient_id = ? AND device_id = ? AND chain_key = ? AND counter = ?",
                        new String[]{recipientId, String.valueOf(deviceId),
                                Base64.getEncoder().encodeToString(chainKey),
                                String.valueOf(counter)});
            }
        }
        cursor.close();
        return messageKey;
    }

    // ==================== DATA CLASSES ====================

    public static class IdentityKeyPair {
        public byte[] publicKey;
        public byte[] privateKey;

        public IdentityKeyPair(byte[] publicKey, byte[] privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

    public static class PreKeyRecord {
        public int id;
        public byte[] publicKey;
        public byte[] privateKey;

        public PreKeyRecord(int id, byte[] publicKey, byte[] privateKey) {
            this.id = id;
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

    public static class SignedPreKeyRecord {
        public int id;
        public byte[] publicKey;
        public byte[] privateKey;
        public byte[] signature;

        public SignedPreKeyRecord(int id, byte[] publicKey, byte[] privateKey, byte[] signature) {
            this.id = id;
            this.publicKey = publicKey;
            this.privateKey = privateKey;
            this.signature = signature;
        }
    }
}
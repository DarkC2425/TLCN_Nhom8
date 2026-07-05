package com.example.project.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.*;

/**
 * Message History Database
 * Stores all sent/received messages
 */
public class MessageDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "messages.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String TABLE_MESSAGES = "messages";

    public MessageDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Conversations table
        db.execSQL("CREATE TABLE " + TABLE_CONVERSATIONS + " ("
                + "conversation_id TEXT PRIMARY KEY, "
                + "recipient_id TEXT NOT NULL, "
                + "recipient_name TEXT, "
                + "last_message TEXT, "
                + "last_message_timestamp INTEGER, "
                + "unread_count INTEGER DEFAULT 0)");

        // Messages table
        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + "message_id TEXT PRIMARY KEY, "
                + "conversation_id TEXT NOT NULL, "
                + "sender_id TEXT NOT NULL, "
                + "recipient_id TEXT NOT NULL, "
                + "content TEXT NOT NULL, "
                + "timestamp INTEGER NOT NULL, "
                + "is_outgoing INTEGER NOT NULL, "
                + "is_read INTEGER DEFAULT 0, "
                + "delivery_status INTEGER DEFAULT 0, " // 0=pending, 1=sent, 2=delivered, 3=read
                + "FOREIGN KEY(conversation_id) REFERENCES " + TABLE_CONVERSATIONS + "(conversation_id))");

        db.execSQL("CREATE INDEX idx_messages_conversation ON " + TABLE_MESSAGES + "(conversation_id)");
        db.execSQL("CREATE INDEX idx_messages_timestamp ON " + TABLE_MESSAGES + "(timestamp)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONVERSATIONS);
        onCreate(db);
    }

    /**
     * Save message
     */
    public void saveMessage(String messageId, String conversationId,
                            String senderId, String recipientId,
                            String content, long timestamp, boolean isOutgoing) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("message_id", messageId);
        values.put("conversation_id", conversationId);
        values.put("sender_id", senderId);
        values.put("recipient_id", recipientId);
        values.put("content", content);
        values.put("timestamp", timestamp);
        values.put("is_outgoing", isOutgoing ? 1 : 0);

        db.insertWithOnConflict(TABLE_MESSAGES, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);

        // Update conversation
        updateConversation(conversationId, recipientId, content, timestamp);
    }

    /**
     * Get messages for conversation
     */
    public List<Message> getMessages(String conversationId, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MESSAGES,
                null,
                "conversation_id = ?",
                new String[]{conversationId},
                null, null,
                "timestamp DESC",
                String.valueOf(limit));

        List<Message> messages = new ArrayList<>();
        while (cursor.moveToNext()) {
            messages.add(new Message(
                    cursor.getString(cursor.getColumnIndexOrThrow("message_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("sender_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("is_outgoing")) == 1
            ));
        }
        cursor.close();

        Collections.reverse(messages);
        return messages;
    }

    private void updateConversation(String conversationId, String recipientId,
                                    String lastMessage, long timestamp) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("conversation_id", conversationId);
        values.put("recipient_id", recipientId);
        values.put("last_message", lastMessage);
        values.put("last_message_timestamp", timestamp);

        db.insertWithOnConflict(TABLE_CONVERSATIONS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static class Message {
        public String messageId;
        public String senderId;
        public String content;
        public long timestamp;
        public boolean isOutgoing;

        public Message(String messageId, String senderId, String content,
                       long timestamp, boolean isOutgoing) {
            this.messageId = messageId;
            this.senderId = senderId;
            this.content = content;
            this.timestamp = timestamp;
            this.isOutgoing = isOutgoing;
        }
    }
}
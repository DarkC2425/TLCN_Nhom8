package com.example.project.models;

public class Conversation {
    public String conversationId;
    public String recipientId;
    public String recipientName;
    public String lastMessage;
    public long lastMessageTimestamp;
    public int unreadCount;

    public Conversation(String conversationId, String recipientId,
                        String recipientName, String lastMessage,
                        long lastMessageTimestamp, int unreadCount) {
        this.conversationId = conversationId;
        this.recipientId = recipientId;
        this.recipientName = recipientName;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.unreadCount = unreadCount;
    }
}
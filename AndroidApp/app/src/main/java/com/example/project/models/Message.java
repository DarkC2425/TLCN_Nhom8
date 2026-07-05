package com.example.project.models;

public class Message {
    public String messageId;
    public String conversationId;
    public String senderId;
    public String recipientId;
    public String content;
    public long timestamp;
    public boolean isOutgoing;
    public boolean isRead;
    public int deliveryStatus; // 0=pending, 1=sent, 2=delivered, 3=read

    public Message(String messageId, String conversationId, String senderId,
                   String recipientId, String content, long timestamp, boolean isOutgoing) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.content = content;
        this.timestamp = timestamp;
        this.isOutgoing = isOutgoing;
        this.isRead = false;
        this.deliveryStatus = 0;
    }
}
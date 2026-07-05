package com.example.project.models;

public class User {
    public String userId;
    public String username;
    public String displayName;
    public String avatarUrl;
    public long lastSeen;

    public User(String userId, String username, String displayName, String avatarUrl) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getDisplayName() {
        return displayName != null && !displayName.isEmpty() ? displayName : username;
    }
}
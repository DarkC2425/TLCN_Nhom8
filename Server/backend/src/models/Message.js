const mongoose = require("mongoose");

const MessageSchema = new mongoose.Schema({
  messageId: { type: String, unique: true },
  senderId: String,
  senderDeviceId: { type: Number, required: true }, // NEW: Track which device sent it
  recipientId: String,
  recipientDeviceId: { type: Number, default: 1 }, // NEW: Track target device
  // Content must be base64-encoded encrypted data (validated)
  content: {
    type: String,
    required: true,
    validate: {
      validator: function (v) {
        // Check if valid base64
        return /^[A-Za-z0-9+/]*={0,2}$/.test(v) && v.length % 4 === 0;
      },
      message: "Content must be valid base64-encoded",
    },
  },
  type: { type: Number, default: 1 }, // 1: text, 2: media, etc
  delivered: { type: Boolean, default: false },
  deliveredAt: Date,
  read: { type: Boolean, default: false }, // NEW: Read status
  readAt: Date, // NEW: Read timestamp
  timestamp: { type: Date, default: Date.now },
  createdAt: { type: Date, default: Date.now, index: true },
});

// Index for efficient queries
MessageSchema.index({ recipientId: 1, delivered: 1, createdAt: -1 });
MessageSchema.index({ senderId: 1, recipientId: 1, senderDeviceId: 1 });

module.exports = mongoose.model("Message", MessageSchema);

const mongoose = require("mongoose");

const SessionStateSchema = new mongoose.Schema({
  sessionId: { type: String, required: true, unique: true }, // userId_recipientId_deviceId
  userId: String,
  recipientId: String,
  recipientDeviceId: Number,

  // Double Ratchet state
  rootKey: String,
  chainKeySender: String,
  chainKeyReceiver: String,
  senderMessageNumber: { type: Number, default: 0 },
  receiverMessageNumber: { type: Number, default: 0 },

  // DH Ratchet
  dhRatchetPairPublic: String,
  dhRatchetPairPrivate: String,
  dhRatchetPublicRemote: String,

  // Skip lists for out-of-order messages
  skippedMessageKeys: [
    {
      chainKey: String,
      messageNumber: Number,
      createdAt: { type: Date, default: Date.now, expires: 604800 }, // 7 days TTL
    },
  ],

  // Session metadata
  initiated: { type: Boolean, default: false },
  createdAt: { type: Date, default: Date.now },
  updatedAt: { type: Date, default: Date.now },
});

module.exports = mongoose.model("SessionState", SessionStateSchema);

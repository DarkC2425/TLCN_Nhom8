const mongoose = require("mongoose");

const UserSchema = new mongoose.Schema({
  userId: { type: String, required: true, unique: true },
  username: { type: String, required: true, unique: true },
  passwordHash: { type: String, required: true },
  identityKey: { type: String, required: true },
  registrationId: { type: Number, required: true },
  displayName: String,
  avatarUrl: String,
  devices: [
    {
      deviceId: Number,
      deviceName: String,
      registeredAt: Date,
      lastSeen: Date,
      verified: { type: Boolean, default: false }, // NEW: Device verification status
      deviceToken: String, // NEW: Device-specific token
      // Device-scoped identity material (required for correct X3DH with multi-device / device tokens)
      identityKey: String,
      registrationId: Number,
    },
  ],
  createdAt: { type: Date, default: Date.now },
  lastActive: { type: Date, default: Date.now },
});

module.exports = mongoose.model("User", UserSchema);

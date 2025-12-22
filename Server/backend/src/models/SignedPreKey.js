const mongoose = require("mongoose");

const SignedPreKeySchema = new mongoose.Schema({
  userId: String,
  deviceId: Number,
  keyId: Number,
  publicKey: String,
  signature: String,
  createdAt: { type: Date, default: Date.now },
});

// Only one signed prekey per device.
SignedPreKeySchema.index({ userId: 1, deviceId: 1 }, { unique: true });

module.exports = mongoose.model("SignedPreKey", SignedPreKeySchema);

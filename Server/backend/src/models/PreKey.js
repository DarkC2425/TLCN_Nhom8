const mongoose = require("mongoose");

const PreKeySchema = new mongoose.Schema({
  userId: String,
  deviceId: Number,
  keyId: Number,
  publicKey: String,
  createdAt: { type: Date, default: Date.now },
});

// Prevent duplicates for the same keyId on the same device.
PreKeySchema.index({ userId: 1, deviceId: 1, keyId: 1 }, { unique: true });

module.exports = mongoose.model("PreKey", PreKeySchema);

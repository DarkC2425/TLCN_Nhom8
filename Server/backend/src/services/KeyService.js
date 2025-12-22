// services/KeyService.js
const PreKey = require("../models/PreKey");
const SignedPreKey = require("../models/SignedPreKey");

class KeyService {
  static async replacePreKeys(userId, preKeys = [], deviceId = 1) {
    if (!preKeys || preKeys.length === 0) return;

    // Critical: avoid leaving old keys around. Otherwise we can hand out stale public keys that
    // don't match the current device private keys, causing X3DH mismatch and AEAD failures.
    await PreKey.deleteMany({ userId, deviceId });

    const docs = preKeys.map((pk) => ({
      userId,
      deviceId,
      keyId: pk.keyId,
      publicKey: pk.publicKey,
    }));

    return PreKey.insertMany(docs, { ordered: true });
  }
  static async insertPreKeys(userId, preKeys = [], deviceId = 1) {
    if (!preKeys || preKeys.length === 0) return;
    const docs = preKeys.map((pk) => ({
      userId,
      deviceId,
      keyId: pk.keyId,
      publicKey: pk.publicKey,
    }));
    // insertMany with ordered:false to ignore duplicates
    return PreKey.insertMany(docs, { ordered: false }).catch((err) => {
      if (err.code !== 11000) throw err;
    });
  }

  static async upsertSignedPreKey(userId, signedPreKey, deviceId = 1) {
    return SignedPreKey.findOneAndUpdate(
      { userId, deviceId },
      {
        userId,
        deviceId,
        keyId: signedPreKey.keyId,
        publicKey: signedPreKey.publicKey,
        signature: signedPreKey.signature,
      },
      { upsert: true, new: true }
    );
  }

  static async fetchSignedPreKey(userId, deviceId = 1) {
    return SignedPreKey.findOne({ userId, deviceId });
  }

  static async takeOneTimePreKey(userId, deviceId = 1) {
    // findOneAndDelete to consume one-time prekey
    return PreKey.findOneAndDelete({ userId, deviceId }).sort({ createdAt: 1 });
  }

  static async getKeyStatus(userId, deviceId = 1) {
    // NEW: Check key counts
    const preKeyCount = await PreKey.countDocuments({ userId, deviceId });
    const signedPreKey = await SignedPreKey.findOne({ userId, deviceId });

    return {
      preKeyCount,
      signedPreKeyExists: !!signedPreKey,
    };
  }
}

module.exports = KeyService;

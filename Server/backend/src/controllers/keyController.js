// controllers/KeyController.js
const KeyService = require("../services/KeyService");
const User = require("../models/User");
const AuditLogger = require("../services/AuditLogger");

// Helper: normalize a publicKey/signature which may be a base64 string or an array of numbers
function normalizeKeyField(field) {
  if (!field && field !== 0) return null;
  // If already a string, trim whitespace
  if (typeof field === "string") return field.trim();
  // If it's an array of numbers, convert to base64
  if (Array.isArray(field)) {
    try {
      const buf = Buffer.from(field);
      return buf.toString("base64");
    } catch (e) {
      return null;
    }
  }
  // If it's an object with data buffer (in some clients), attempt conversion
  if (typeof field === "object" && field !== null) {
    if (field.data && Array.isArray(field.data)) {
      try {
        return Buffer.from(field.data).toString("base64");
      } catch (e) {
        return null;
      }
    }
    // If it's Buffer-like
    if (field.type === "Buffer" && Array.isArray(field.data)) {
      try {
        return Buffer.from(field.data).toString("base64");
      } catch (e) {
        return null;
      }
    }
  }
  return null;
}

class KeyController {
  static async uploadPreKeys(req, res) {
    try {
      const { preKeys, signedPreKey, identityKey, registrationId } = req.body;
      const userId = req.userId;
      const deviceId = req.deviceId || 1;

      // Update per-device identity (device-scoped IK is required for correct X3DH).
      if (identityKey) {
        try {
          const user = await User.findOne({ userId });
          if (user && Array.isArray(user.devices)) {
            const d = user.devices.find((x) => x.deviceId === deviceId);
            if (d) {
              d.identityKey =
                typeof identityKey === "string"
                  ? identityKey.trim()
                  : identityKey;
              if (registrationId != null) d.registrationId = registrationId;
              d.lastSeen = new Date();
              await user.save();
            }
          }
        } catch (e) {
          // Ignore identity update failures; key upload still succeeds.
        }
      }

      // Defensive normalization: accept base64 strings or arrays/buffer-like inputs
      if (preKeys && Array.isArray(preKeys)) {
        const normalized = preKeys.map((pk) => ({
          keyId: pk.keyId,
          publicKey: normalizeKeyField(pk.publicKey),
        }));
        await KeyService.replacePreKeys(userId, normalized, deviceId);
      }

      if (signedPreKey) {
        const normalizedSPK = {
          keyId: signedPreKey.keyId,
          publicKey: normalizeKeyField(signedPreKey.publicKey),
          signature: normalizeKeyField(signedPreKey.signature),
        };
        await KeyService.upsertSignedPreKey(userId, normalizedSPK, deviceId);
      }

      res.json({ success: true });
      AuditLogger.logKeyOperation(
        userId,
        "upload_prekeys",
        deviceId,
        preKeys ? preKeys.length : 0
      );
    } catch (err) {
      console.error("Upload prekeys error:", err);
      res.status(500).json({ error: "Failed to upload prekeys" });
    }
  }

  static async fetchPreKeyBundle(req, res) {
    try {
      const recipientId = req.params.recipientId;
      const requestedDeviceIdRaw = req.query.deviceId;
      const requestedDeviceId = requestedDeviceIdRaw
        ? parseInt(requestedDeviceIdRaw, 10)
        : null;

      const recipient = await User.findOne({ userId: recipientId });
      if (!recipient) return res.status(404).json({ error: "User not found" });

      // Choose a target device:
      // - explicit ?deviceId=...
      // - else latest verified device
      // - else primary device 1
      let targetDeviceId = 1;
      if (Number.isFinite(requestedDeviceId) && requestedDeviceId > 0) {
        targetDeviceId = requestedDeviceId;
      } else if (Array.isArray(recipient.devices) && recipient.devices.length) {
        const verified = recipient.devices
          .filter((d) => d && d.verified)
          .sort(
            (a, b) =>
              (b.lastSeen || b.registeredAt || 0) -
              (a.lastSeen || a.registeredAt || 0)
          );
        if (verified.length) targetDeviceId = verified[0].deviceId;
      }

      const signedPreKey = await KeyService.fetchSignedPreKey(
        recipientId,
        targetDeviceId
      );
      if (!signedPreKey)
        return res.status(404).json({ error: "No signed prekey available" });

      const preKey = await KeyService.takeOneTimePreKey(
        recipientId,
        targetDeviceId
      );

      // Prefer device-scoped identity key if present (multi-device).
      let identityKey = recipient.identityKey;
      let registrationId = recipient.registrationId;
      try {
        if (Array.isArray(recipient.devices)) {
          const d = recipient.devices.find(
            (x) => x.deviceId === targetDeviceId
          );
          if (d && d.identityKey) identityKey = d.identityKey;
          if (d && d.registrationId != null) registrationId = d.registrationId;
        }
      } catch (e) {}

      const response = {
        recipientId,
        identityKey,
        deviceId: targetDeviceId,
        registrationId,
        signedPreKey: {
          keyId: signedPreKey.keyId,
          publicKey: signedPreKey.publicKey,
          signature: signedPreKey.signature,
        },
      };

      if (preKey) {
        response.preKey = { keyId: preKey.keyId, publicKey: preKey.publicKey };
      }

      res.json(response);
      AuditLogger.logKeyOperation(
        req.userId || "system",
        "fetch_bundle",
        targetDeviceId,
        preKey ? 1 : 0,
        { recipientId }
      );
    } catch (err) {
      console.error("Fetch prekey bundle error:", err);
      res.status(500).json({ error: "Failed to fetch prekey bundle" });
    }
  }

  static async getKeyStatus(req, res) {
    // NEW: Check prekey count
    try {
      const userId = req.userId;
      const deviceId = req.deviceId || 1;
      const status = await KeyService.getKeyStatus(userId, deviceId);

      res.json({
        userId,
        deviceId,
        preKeyCount: status.preKeyCount,
        signedPreKeyExists: status.signedPreKeyExists,
        needsRefill: status.preKeyCount < 20, // Threshold
      });
    } catch (err) {
      console.error("Get key status error:", err);
      res.status(500).json({ error: "Failed to get key status" });
    }
  }
}

module.exports = KeyController;

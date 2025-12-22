// controllers/DeviceController.js
const DeviceService = require("../services/DeviceService");
const jwt = require("jsonwebtoken");
const { jwtSecret } = require("../config/jwt");
const AuditLogger = require("../services/AuditLogger");

class DeviceController {
  static async register(req, res) {
    try {
      const { deviceName } = req.body;
      const result = await DeviceService.registerDevice(req.userId, deviceName);
      AuditLogger.logDevice(req.userId, "register", result.deviceId, {
        deviceName,
      });
      res.json(result);
    } catch (err) {
      console.error("Device registration error:", err);
      res.status(500).json({ error: "Failed to register device" });
    }
  }

  static async verifyDevice(req, res) {
    try {
      const { deviceToken } = req.body;
      if (!deviceToken)
        return res.status(400).json({ error: "Missing deviceToken" });

      const decoded = jwt.verify(deviceToken, jwtSecret);
      const result = await DeviceService.verifyDevice(
        decoded.userId,
        decoded.deviceId
      );
      res.json(result);
      AuditLogger.logDevice(decoded.userId, "verify", decoded.deviceId, {});
    } catch (err) {
      console.error("Device verification error:", err);
      res.status(401).json({ error: "Invalid or expired device token" });
    }
  }

  static async list(req, res) {
    try {
      const devices = await DeviceService.listDevices(req.userId);
      res.json({
        devices: devices.map((d) => ({
          deviceId: d.deviceId,
          deviceName: d.deviceName,
          verified: d.verified,
          registeredAt: d.registeredAt ? d.registeredAt.getTime() : null,
          lastSeen: d.lastSeen ? d.lastSeen.getTime() : null,
        })),
      });
    } catch (err) {
      console.error("List devices error:", err);
      res.status(500).json({ error: "Failed to list devices" });
    }
  }

  static async revokeDevice(req, res) {
    try {
      const { deviceId } = req.body;
      if (!deviceId) return res.status(400).json({ error: "Missing deviceId" });

      await DeviceService.revokeDevice(req.userId, deviceId);
      res.json({ success: true, message: "Device revoked" });
    } catch (err) {
      console.error("Revoke device error:", err);
      res.status(500).json({ error: "Failed to revoke device" });
    }
  }
}

module.exports = DeviceController;

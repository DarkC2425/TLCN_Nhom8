// services/DeviceService.js
const jwt = require("jsonwebtoken");
const User = require("../models/User");
const { jwtSecret, expiresIn } = require("../config/jwt");

class DeviceService {
  static async registerDevice(userId, deviceName) {
    const user = await User.findOne({ userId });
    if (!user) throw new Error("User not found");

    const deviceId =
      user.devices && user.devices.length
        ? Math.max(...user.devices.map((d) => d.deviceId)) + 1
        : 1;

    const deviceToken = jwt.sign({ userId, deviceId }, jwtSecret, {
      expiresIn,
    });

    user.devices.push({
      deviceId,
      deviceName: deviceName || `device-${deviceId}`,
      registeredAt: new Date(),
      lastSeen: new Date(),
      verified: false,
      deviceToken,
    });

    await user.save();
    return { deviceId, deviceToken, verified: false };
  }

  static async verifyDevice(userId, deviceId) {
    const user = await User.findOne({ userId });
    if (!user) throw new Error("User not found");

    const device = user.devices.find((d) => d.deviceId === deviceId);
    if (!device) throw new Error("Device not found");

    device.verified = true;
    device.lastSeen = new Date();
    await user.save();

    return { success: true, deviceId, verified: true };
  }

  static async listDevices(userId) {
    const user = await User.findOne({ userId }).select("devices");
    return user && user.devices ? user.devices : [];
  }

  static async revokeDevice(userId, deviceId) {
    const user = await User.findOne({ userId });
    if (!user) throw new Error("User not found");

    user.devices = user.devices.filter((d) => d.deviceId !== deviceId);
    await user.save();
  }

  static async getDeviceByToken(token) {
    const decoded = jwt.verify(token, jwtSecret);
    return { userId: decoded.userId, deviceId: decoded.deviceId };
  }
}

module.exports = DeviceService;

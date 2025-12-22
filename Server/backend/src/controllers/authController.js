const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const UserService = require("../services/UserService");
const KeyService = require("../services/KeyService");
const { jwtSecret, expiresIn } = require("../config/jwt");
const AuditLogger = require("../services/AuditLogger");

class AuthController {
  static async register(req, res) {
    try {
      const { username, password, identityKey, preKeys } = req.body;

      if (!username || !password || !identityKey)
        return res.status(400).json({ error: "Missing fields" });

      const existing = await UserService.findByUsername(username);
      if (existing) return res.status(409).json({ error: "Username exists" });

      const passwordHash = await bcrypt.hash(password, 10);
      const userId = `user_${Date.now()}_${Math.random()
        .toString(36)
        .substr(2, 9)}`;
      const registrationId = Math.floor(Math.random() * 16384);

      const deviceId = 1;
      const deviceToken = jwt.sign({ userId, deviceId }, jwtSecret, {
        expiresIn,
      });

      await UserService.createUser({
        userId,
        username,
        passwordHash,
        identityKey,
        registrationId,
        devices: [
          {
            deviceId,
            deviceName: "Primary Device",
            registeredAt: new Date(),
            lastSeen: new Date(),
            verified: true,
            deviceToken,
          },
        ],
      });

      // If client provided preKeys or signedPreKey during registration, save them
      if (preKeys && Array.isArray(preKeys) && preKeys.length > 0) {
        await KeyService.insertPreKeys(userId, preKeys, 1);
      }

      if (req.body.signedPreKey) {
        await KeyService.upsertSignedPreKey(userId, req.body.signedPreKey, 1);
      }

      const token = jwt.sign({ userId, username }, jwtSecret, { expiresIn });

      res.json({
        userId,
        authToken: token,
        registrationId,
        deviceId,
        deviceToken,
      });
      AuditLogger.logAuth(userId, "register", true, { username });
    } catch (err) {
      console.error(err);
      res.status(500).json({ error: "Registration failed" });
      AuditLogger.logAuth(null, "register", false, { error: err.message });
    }
  }

  static async login(req, res) {
    try {
      const { username, password } = req.body;

      const user = await UserService.findByUsername(username);
      if (!user) return res.status(401).json({ error: "Invalid credentials" });

      const match = await bcrypt.compare(password, user.passwordHash);
      if (!match) return res.status(401).json({ error: "Invalid credentials" });

      const token = jwt.sign(
        { userId: user.userId, username: user.username },
        jwtSecret,
        { expiresIn }
      );

      const deviceId = 1;
      const deviceToken = jwt.sign(
        { userId: user.userId, deviceId },
        jwtSecret,
        {
          expiresIn,
        }
      );

      try {
        const d = Array.isArray(user.devices)
          ? user.devices.find((x) => x.deviceId === deviceId)
          : null;
        if (d) {
          d.deviceToken = deviceToken;
          d.verified = true;
          d.lastSeen = new Date();
          await user.save();
        }
      } catch (e) {}

      res.json({
        userId: user.userId,
        authToken: token,
        deviceId,
        deviceToken,
      });
      AuditLogger.logAuth(user.userId, "login", true, { username });
    } catch (err) {
      res.status(500).json({ error: "Login failed" });
      AuditLogger.logAuth(null, "login", false, { error: err.message });
    }
  }
}

module.exports = AuthController;

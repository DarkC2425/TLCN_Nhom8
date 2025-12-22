const jwt = require("jsonwebtoken");
const User = require("../models/User");
const { jwtSecret } = require("../config/jwt");

module.exports = async (req, res, next) => {
  try {
    const header = req.headers.authorization;
    if (!header || !header.startsWith("Bearer "))
      return res.status(401).json({ error: "No token provided" });

    const token = header.substring(7);
    const decoded = jwt.verify(token, jwtSecret);

    req.userId = decoded.userId;
    req.username = decoded.username;
    req.deviceId = decoded.deviceId; // NEW: Extract device ID if present

    await User.findOneAndUpdate(
      { userId: decoded.userId },
      { lastActive: new Date() }
    );

    next();
  } catch (err) {
    return res.status(401).json({ error: "Invalid token" });
  }
};

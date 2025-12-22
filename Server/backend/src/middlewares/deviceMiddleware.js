// middlewares/deviceMiddleware.js
const jwt = require("jsonwebtoken");
const { jwtSecret } = require("../config/jwt");

/**
 * Device authentication middleware
 * Verifies device token from Authorization header or X-Device-Token
 * Sets req.deviceId for device-specific operations
 */
module.exports = async (req, res, next) => {
  try {
    // First, try to get deviceToken from custom header
    let deviceToken = req.headers["x-device-token"];

    // Fallback: Extract from Authorization header if it's device-specific
    if (
      !deviceToken &&
      req.headers.authorization &&
      req.headers.authorization.startsWith("Device ")
    ) {
      deviceToken = req.headers.authorization.substring(7);
    }

    if (deviceToken) {
      try {
        const decoded = jwt.verify(deviceToken, jwtSecret);
        req.deviceId = decoded.deviceId;
        req.userId = decoded.userId;
        req.isDeviceVerified = true;
        next();
      } catch (err) {
        return res
          .status(401)
          .json({ error: "Invalid device token", details: err.message });
      }
    } else {
      // No device token, proceed but mark as unverified
      req.isDeviceVerified = false;
      next();
    }
  } catch (err) {
    return res
      .status(401)
      .json({ error: "Device verification failed", details: err.message });
  }
};

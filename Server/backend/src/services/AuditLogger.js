// services/AuditLogger.js
const fs = require("fs");
const path = require("path");

/**
 * Audit Logger Service
 * Logs security-sensitive operations for compliance and debugging
 */
class AuditLogger {
  constructor() {
    this.logDir = path.join(__dirname, "../logs");
    this.ensureLogDir();
  }

  ensureLogDir() {
    if (!fs.existsSync(this.logDir)) {
      fs.mkdirSync(this.logDir, { recursive: true });
    }
  }

  /**
   * Log authentication events
   */
  logAuth(userId, action, success, details = {}) {
    this.log("auth", {
      userId,
      action, // 'register', 'login', 'logout'
      success,
      details,
      timestamp: new Date().toISOString(),
    });
  }

  /**
   * Log device operations
   */
  logDevice(userId, action, deviceId, details = {}) {
    this.log("device", {
      userId,
      deviceId,
      action, // 'register', 'verify', 'revoke'
      details,
      timestamp: new Date().toISOString(),
    });
  }

  /**
   * Log key operations
   */
  logKeyOperation(userId, action, deviceId, count = 0, details = {}) {
    this.log("keys", {
      userId,
      deviceId,
      action, // 'upload_prekeys', 'upload_signed_prekey', 'fetch_bundle'
      count,
      details,
      timestamp: new Date().toISOString(),
    });
  }

  /**
   * Log message operations
   */
  logMessage(action, senderId, recipientId, messageId, deviceInfo = {}) {
    this.log("messages", {
      action, // 'send', 'fetch', 'ack', 'read'
      senderId,
      recipientId,
      messageId,
      senderDeviceId: deviceInfo.senderDeviceId,
      timestamp: new Date().toISOString(),
    });
  }

  /**
   * Log security violations or suspicious activities
   */
  logSecurityEvent(userId, severity, event, details = {}) {
    this.log("security", {
      userId,
      severity, // 'low', 'medium', 'high', 'critical'
      event, // 'unverified_device_access', 'invalid_content', etc
      details,
      timestamp: new Date().toISOString(),
    });
  }

  /**
   * Generic log method
   */
  log(category, data) {
    const logFile = path.join(
      this.logDir,
      `${category}-${this.getDateString()}.log`
    );
    const logEntry = JSON.stringify(data) + "\n";

    fs.appendFile(logFile, logEntry, (err) => {
      if (err) {
        console.error("Failed to write audit log:", err);
      }
    });
  }

  getDateString() {
    const now = new Date();
    return now.toISOString().split("T")[0]; // YYYY-MM-DD
  }
}

module.exports = new AuditLogger();

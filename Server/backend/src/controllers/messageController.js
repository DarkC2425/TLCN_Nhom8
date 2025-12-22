// controllers/MessageController.js
const MessageService = require("../services/MessageService");
const User = require("../models/User");
const AuditLogger = require("../services/AuditLogger");

class MessageController {
  static async send(req, res) {
    try {
      const { recipientId, recipientDeviceId, content, type } = req.body;

      if (!recipientId || !content || !type) {
        return res.status(400).json({
          error: "Missing required fields: recipientId, content, type",
        });
      }

      // NEW: Validate senderDeviceId from auth
      if (!req.deviceId) {
        return res.status(401).json({ error: "Device not verified" });
      }

      // NEW: Validate content is base64
      if (!/^[A-Za-z0-9+/]*={0,2}$/.test(content) || content.length % 4 !== 0) {
        return res
          .status(400)
          .json({ error: "Content must be valid base64-encoded" });
      }

      const recipient = await User.findOne({ userId: recipientId });
      if (!recipient)
        return res.status(404).json({ error: "Recipient not found" });

      const messageId = `msg_${Date.now()}_${Math.random()
        .toString(36)
        .substr(2, 9)}`;

      const msg = await MessageService.createMessage({
        messageId,
        senderId: req.userId,
        senderDeviceId: req.deviceId, // NEW: Track sender device
        recipientId,
        recipientDeviceId: recipientDeviceId || 1,
        content,
        type,
        timestamp: new Date(),
      });

      res.json({
        success: true,
        messageId: msg.messageId,
        timestamp: msg.timestamp.getTime(),
        senderDeviceId: msg.senderDeviceId,
      });
      AuditLogger.logMessage("send", req.userId, recipientId, msg.messageId, {
        senderDeviceId: req.deviceId,
      });
    } catch (err) {
      console.error("Send message error:", err);
      res.status(500).json({ error: "Failed to send message" });
    }
  }

  static async fetch(req, res) {
    try {
      const { limit = 100, cursor } = req.query;
      const messages = await MessageService.getPendingMessages(
        req.userId,
        parseInt(limit),
        cursor
      );

      const formatted = messages.map((m) => ({
        messageId: m.messageId,
        senderId: m.senderId,
        senderDeviceId: m.senderDeviceId,
        recipientDeviceId: m.recipientDeviceId,
        content: m.content,
        type: m.type,
        timestamp: m.timestamp.getTime(),
      }));

      res.json({ messages: formatted });
    } catch (err) {
      console.error("Fetch messages error:", err);
      res.status(500).json({ error: "Failed to fetch messages" });
    }
  }

  static async ack(req, res) {
    try {
      const { messageId } = req.body;
      if (!messageId)
        return res.status(400).json({ error: "Missing messageId" });

      await MessageService.ackMessage(messageId, req.userId);
      res.json({ success: true });
      AuditLogger.logMessage("ack", req.userId, req.userId, messageId, {
        senderDeviceId: req.deviceId,
      });
    } catch (err) {
      console.error("Ack message error:", err);
      res.status(500).json({ error: "Failed to acknowledge message" });
    }
  }

  static async markAsRead(req, res) {
    // NEW: Mark messages as read
    try {
      const { messageId } = req.body;
      if (!messageId)
        return res.status(400).json({ error: "Missing messageId" });

      const result = await MessageService.markAsRead(messageId, req.userId);
      res.json({ success: true, readAt: result.readAt.getTime() });
      AuditLogger.logMessage("read", req.userId, req.userId, messageId, {
        senderDeviceId: req.deviceId,
      });
    } catch (err) {
      console.error("Mark as read error:", err);
      res.status(500).json({ error: "Failed to mark as read" });
    }
  }
}

module.exports = MessageController;

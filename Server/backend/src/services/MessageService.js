// services/MessageService.js
const Message = require("../models/Message");

class MessageService {
  static async createMessage(data) {
    const msg = new Message(data);
    return msg.save();
  }

  static async getPendingMessages(userId, limit = 100, cursor = null) {
    const query = { recipientId: userId, delivered: false };
    if (cursor) {
      query.createdAt = { $lt: new Date(parseInt(cursor)) };
    }
    return Message.find(query).sort({ timestamp: 1 }).limit(limit);
  }

  static async ackMessage(messageId, userId) {
    return Message.findOneAndUpdate(
      { messageId, recipientId: userId },
      { delivered: true, deliveredAt: new Date() },
      { new: true }
    );
  }

  static async markAsRead(messageId, userId) {
    // NEW: Mark message as read
    return Message.findOneAndUpdate(
      { messageId, recipientId: userId },
      { read: true, readAt: new Date() },
      { new: true }
    );
  }

  static async getConversation(userId, otherUserId, limit = 100) {
    // NEW: Get conversation history
    return Message.find({
      $or: [
        { senderId: userId, recipientId: otherUserId },
        { senderId: otherUserId, recipientId: userId },
      ],
    })
      .sort({ timestamp: -1 })
      .limit(limit);
  }
}

module.exports = MessageService;

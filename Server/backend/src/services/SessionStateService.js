// services/SessionStateService.js
const SessionState = require("../models/SessionState");

class SessionStateService {
  static generateSessionId(userId, recipientId, deviceId) {
    return `${userId}_${recipientId}_${deviceId}`;
  }

  static async getOrCreateSession(userId, recipientId, recipientDeviceId) {
    const sessionId = this.generateSessionId(
      userId,
      recipientId,
      recipientDeviceId
    );

    let session = await SessionState.findOne({ sessionId });
    if (!session) {
      session = new SessionState({
        sessionId,
        userId,
        recipientId,
        recipientDeviceId,
        initiated: false,
      });
      await session.save();
    }
    return session;
  }

  static async initializeSession(
    userId,
    recipientId,
    recipientDeviceId,
    rootKey,
    chainKeySender,
    dhRatchetPairPublic,
    dhRatchetPairPrivate,
    dhRatchetPublicRemote
  ) {
    const sessionId = this.generateSessionId(
      userId,
      recipientId,
      recipientDeviceId
    );

    return SessionState.findOneAndUpdate(
      { sessionId },
      {
        rootKey,
        chainKeySender,
        chainKeyReceiver: chainKeySender,
        senderMessageNumber: 0,
        receiverMessageNumber: 0,
        dhRatchetPairPublic,
        dhRatchetPairPrivate,
        dhRatchetPublicRemote,
        initiated: true,
        updatedAt: new Date(),
      },
      { upsert: true, new: true }
    );
  }

  static async incrementSenderMessageNumber(sessionId) {
    return SessionState.findOneAndUpdate(
      { sessionId },
      {
        $inc: { senderMessageNumber: 1 },
        updatedAt: new Date(),
      },
      { new: true }
    );
  }

  static async incrementReceiverMessageNumber(sessionId) {
    return SessionState.findOneAndUpdate(
      { sessionId },
      {
        $inc: { receiverMessageNumber: 1 },
        updatedAt: new Date(),
      },
      { new: true }
    );
  }

  static async updateChainKeys(sessionId, chainKeySender, chainKeyReceiver) {
    return SessionState.findOneAndUpdate(
      { sessionId },
      {
        chainKeySender,
        chainKeyReceiver,
        updatedAt: new Date(),
      },
      { new: true }
    );
  }

  static async addSkippedMessageKey(sessionId, chainKey, messageNumber) {
    return SessionState.findOneAndUpdate(
      { sessionId },
      {
        $push: { skippedMessageKeys: { chainKey, messageNumber } },
        updatedAt: new Date(),
      },
      { new: true }
    );
  }

  static async rotateRatchet(
    sessionId,
    newRootKey,
    newChainKeySender,
    newDhPublic,
    newDhPrivate,
    remoteDhPublic
  ) {
    return SessionState.findOneAndUpdate(
      { sessionId },
      {
        rootKey: newRootKey,
        chainKeySender: newChainKeySender,
        chainKeyReceiver: newChainKeySender,
        senderMessageNumber: 0,
        receiverMessageNumber: 0,
        dhRatchetPairPublic: newDhPublic,
        dhRatchetPairPrivate: newDhPrivate,
        dhRatchetPublicRemote: remoteDhPublic,
        updatedAt: new Date(),
      },
      { new: true }
    );
  }

  static async getSession(sessionId) {
    return SessionState.findOne({ sessionId });
  }

  static async deleteSession(sessionId) {
    return SessionState.deleteOne({ sessionId });
  }
}

module.exports = SessionStateService;

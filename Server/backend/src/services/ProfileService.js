// services/ProfileService.js
const User = require("../models/User");

class ProfileService {
  static async getProfile(userId) {
    return User.findOne({ userId }).select(
      "userId username displayName avatarUrl identityKey registrationId"
    );
  }

  static async updateProfile(userId, { displayName, avatarUrl }) {
    return User.findOneAndUpdate(
      { userId },
      { displayName, avatarUrl },
      { new: true }
    );
  }
}

module.exports = ProfileService;

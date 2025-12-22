// services/UserService.js
const User = require("../models/User");

class UserService {
  static async findByUsername(username) {
    return User.findOne({ username });
  }

  static async findByUserId(userId) {
    return User.findOne({ userId });
  }

  static async createUser(data) {
    const user = new User(data);
    return user.save();
  }

  static async updateUser(userId, update) {
    return User.findOneAndUpdate({ userId }, update, { new: true });
  }

  static async searchUsers(query, excludeUserId, limit = 20) {
    return User.find({
      username: { $regex: query, $options: "i" },
      userId: { $ne: excludeUserId }
    })
      .select("userId username displayName avatarUrl")
      .limit(limit);
  }
}

module.exports = UserService;

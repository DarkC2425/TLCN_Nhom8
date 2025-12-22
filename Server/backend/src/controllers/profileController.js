// controllers/ProfileController.js
const ProfileService = require("../services/ProfileService");
const UserService = require("../services/UserService");

class ProfileController {
  static async getProfile(req, res) {
    try {
      const userId = req.params.userId;
      const user = await ProfileService.getProfile(userId);
      if (!user) return res.status(404).json({ error: "User not found" });

      res.json({
        userId: user.userId,
        username: user.username,
        displayName: user.displayName || user.username,
        avatarUrl: user.avatarUrl || "",
        identityKey: user.identityKey,
        registrationId: user.registrationId,
      });
    } catch (err) {
      console.error("Fetch profile error:", err);
      res.status(500).json({ error: "Failed to fetch profile" });
    }
  }

  static async updateProfile(req, res) {
    try {
      const { displayName, avatarUrl } = req.body;
      await ProfileService.updateProfile(req.userId, {
        displayName,
        avatarUrl,
      });
      res.json({ success: true });
    } catch (err) {
      console.error("Update profile error:", err);
      res.status(500).json({ error: "Failed to update profile" });
    }
  }

  static async search(req, res) {
    try {
      const { query = "" } = req.query;
      const currentUserId = req.userId; // From auth middleware
      const limit = parseInt(req.query.limit) || 20;

      // Search users, exclude current user
      const UserService = require("../services/UserService"); 
      const users = await UserService.searchUsers(query, currentUserId, limit)

      res.json({
        success: true,
        users: users.map((user) => ({
          userId: user.userId,
          username: user.username,
          displayName: user.displayName || user.username,
          avatarUrl: user.avatarUrl || null,
        })),
      });
    } catch (error) {
      console.error("Search users error:", error);
      res.status(500).json({
        success: false,
        message: "Failed to search users",
        error: error.message,
      });
    }
  }
}

module.exports = ProfileController;

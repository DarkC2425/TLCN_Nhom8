// routes/profile.routes.js
const router = require("express").Router();
const authenticate = require("../middlewares/authMiddleware");
const ProfileController = require("../controllers/profileController");

router.get("/profile/:userId", authenticate, ProfileController.getProfile);
router.post("/profile/update", authenticate, ProfileController.updateProfile);
router.get("/search", authenticate, ProfileController.search);

module.exports = router;

// routes/messages.routes.js
const router = require("express").Router();
const authenticate = require("../middlewares/authMiddleware");
const deviceAuth = require("../middlewares/deviceMiddleware");
const MessageController = require("../controllers/messageController");

router.post("/send", authenticate, deviceAuth, MessageController.send);
router.get("/fetch", authenticate, MessageController.fetch);
router.post("/ack", authenticate, deviceAuth, MessageController.ack);
router.post("/read", authenticate, deviceAuth, MessageController.markAsRead); // NEW: Mark as read

module.exports = router;

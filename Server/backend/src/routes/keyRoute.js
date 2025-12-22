// routes/keys.routes.js
const router = require("express").Router();
const authenticate = require("../middlewares/authMiddleware");
const deviceAuth = require("../middlewares/deviceMiddleware");
const KeyController = require("../controllers/keyController");

router.post("/prekeys", authenticate, deviceAuth, KeyController.uploadPreKeys);
router.get(
  "/prekeys/:recipientId",
  authenticate,
  KeyController.fetchPreKeyBundle
);
router.get("/status", authenticate, deviceAuth, KeyController.getKeyStatus); // NEW: Check prekey status

module.exports = router;

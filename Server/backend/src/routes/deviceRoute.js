// routes/devices.routes.js
const router = require("express").Router();
const authenticate = require("../middlewares/authMiddleware");
const DeviceController = require("../controllers/deviceController");

router.post("/register", authenticate, DeviceController.register);
router.post("/verify", DeviceController.verifyDevice); // NEW: Device verification
router.get("/list", authenticate, DeviceController.list);
router.post("/revoke", authenticate, DeviceController.revokeDevice); // NEW: Device revocation

module.exports = router;

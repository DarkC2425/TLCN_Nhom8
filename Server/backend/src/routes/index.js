const router = require("express").Router();

router.use("/accounts", require("./authRoute"));
router.use("/keys", require("./keyRoute"));
router.use("/messages", require("./messageRoute"));
router.use("/users", require("./profileRoute"));
router.use("/devices", require("./deviceRoute"));
router.use("/certificates", require("./certificateRoute"));

module.exports = router;

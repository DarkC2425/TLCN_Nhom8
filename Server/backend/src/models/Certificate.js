const mongoose = require("mongoose");

const CertificateSchema = new mongoose.Schema({
    userId: { type: String, unique: true },
    certificate: String,
    expiresAt: Date,
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model("Certificate", CertificateSchema);

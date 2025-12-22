// services/CertificateService.js
const Certificate = require("../models/Certificate");

class CertificateService {
  static async getOrCreateCertificate(userId) {
    let cert = await Certificate.findOne({ userId });
    const now = new Date();

    if (!cert || cert.expiresAt < now) {
      const certificate = Buffer.from(
        JSON.stringify({ userId, timestamp: Date.now() })
      ).toString("base64");

      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 30);

      cert = await Certificate.findOneAndUpdate(
        { userId },
        { userId, certificate, expiresAt },
        { upsert: true, new: true }
      );
    }

    return cert;
  }
}

module.exports = CertificateService;

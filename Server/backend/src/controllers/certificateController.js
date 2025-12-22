// controllers/CertificateController.js
const CertificateService = require("../services/CertificateService");

class CertificateController {
  static async getSealedSenderCert(req, res) {
    try {
      const cert = await CertificateService.getOrCreateCertificate(req.userId);
      res.json({
        certificate: cert.certificate,
        expiresAt: cert.expiresAt.getTime()
      });
    } catch (err) {
      console.error("Certificate error:", err);
      res.status(500).json({ error: "Failed to get certificate" });
    }
  }
}

module.exports = CertificateController;

require("dotenv").config();
const express = require("express");
const http = require("http");
const https = require("https");
const fs = require("fs");
const helmet = require("helmet");
const cors = require("cors");

const connectDB = require("./config/database");
const limiter = require("./config/rateLimit");
const routes = require("./routes");

const app = express();

// Middlewares
app.use(helmet());
app.use(cors());
app.use(express.json({ limit: "10mb" }));
app.use("/api", limiter);

// DB
connectDB();

// Routes
app.use("/api/v1", routes);

// Health check
app.get("/health", (req, res) => res.json({ status: "ok", time: Date.now() }));

const PORT = process.env.PORT || 3000;

const SSL_ENABLED =
  String(process.env.SSL_ENABLED || "false").toLowerCase() === "true";

if (SSL_ENABLED) {
  const SSL_PORT = process.env.SSL_PORT || 3443;
  const SSL_KEY_PATH = process.env.SSL_KEY_PATH;
  const SSL_CERT_PATH = process.env.SSL_CERT_PATH;

  if (!SSL_KEY_PATH || !SSL_CERT_PATH) {
    throw new Error(
      "SSL is enabled (SSL_ENABLED=true) but SSL_KEY_PATH or SSL_CERT_PATH is missing in env"
    );
  }

  const key = fs.readFileSync(SSL_KEY_PATH);
  const cert = fs.readFileSync(SSL_CERT_PATH);

  https.createServer({ key, cert }, app).listen(SSL_PORT, () => {
    console.log(`🔐 HTTPS server running on port ${SSL_PORT}`);
  });

  // Optional: also expose HTTP (e.g. for local tools). Set HTTP_PORT to enable.
  const HTTP_PORT = process.env.HTTP_PORT;
  if (HTTP_PORT) {
    http.createServer(app).listen(HTTP_PORT, () => {
      console.log(`🚀 HTTP server running on port ${HTTP_PORT}`);
    });
  }
} else {
  http.createServer(app).listen(PORT, () => {
    console.log(`🚀 HTTP server running on port ${PORT}`);
  });
}

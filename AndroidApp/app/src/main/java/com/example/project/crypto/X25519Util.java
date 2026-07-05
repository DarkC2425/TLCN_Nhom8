package com.example.project.crypto;

import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import java.security.SecureRandom;
import java.util.Arrays;

public class X25519Util {

    // Sử dụng SecureRandom được seed đúng cách
    private static final SecureRandom secureRandom = new SecureRandom();

    // Generate keypair: returns {priv(32), pub(32)}
    public static KeyPair generateKeyPair() {
        byte[] priv = new byte[32];
        secureRandom.nextBytes(priv);

        X25519PrivateKeyParameters privParam = new X25519PrivateKeyParameters(priv, 0);
        byte[] pub = new byte[32];
        privParam.generatePublicKey().encode(pub, 0);

        return new KeyPair(priv, pub);
    }

    // compute shared secret: ourPriv (32) + theirPub (32) -> 32 bytes
    public static byte[] dh(byte[] ourPriv, byte[] theirPub) {
        if (ourPriv == null || ourPriv.length != 32) {
            throw new IllegalArgumentException("Invalid private key length");
        }
        if (theirPub == null || theirPub.length != 32) {
            throw new IllegalArgumentException("Invalid public key length");
        }

        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(ourPriv, 0);
        X25519PublicKeyParameters pub = new X25519PublicKeyParameters(theirPub, 0);
        X25519Agreement agr = new X25519Agreement();
        agr.init(priv);
        byte[] shared = new byte[32];
        agr.calculateAgreement(pub, shared, 0);

        return shared;
    }

    public static class KeyPair {
        public final byte[] priv; // 32 bytes
        public final byte[] pub;  // 32 bytes

        public KeyPair(byte[] priv, byte[] pub) {
            this.priv = priv;
            this.pub = pub;
        }

        // Xóa private key khỏi memory khi không còn sử dụng
        public void destroy() {
            if (priv != null) {
                Arrays.fill(priv, (byte) 0);
            }
            // Không cần xóa public key vì nó không nhạy cảm
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                destroy();
            } finally {
                super.finalize();
            }
        }
    }
}
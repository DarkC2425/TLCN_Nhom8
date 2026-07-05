package com.example.project.crypto;

import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.HKDFParameters;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * FIXED: Added randomInt() method
 * FIXED: Added clearBytes() helper
 * FIXED: Improved error handling
 */
public class CryptoUtils {
    public static final int KEY_LEN = 32; // 256-bit
    public static final int IV_LEN = 12;  // 96-bit for GCM
    public static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag

    private static final SecureRandom RNG;

    static {
        RNG = new SecureRandom();
        RNG.nextBytes(new byte[32]); // Force seeding
    }

    /**
     * HKDF Key Derivation Function
     */
    public static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int outLen) {
        if (ikm == null || ikm.length == 0) {
            throw new IllegalArgumentException("Input key material cannot be null or empty");
        }
        if (outLen <= 0 || outLen > 255 * 32) {
            throw new IllegalArgumentException("Invalid output length");
        }

        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(ikm, salt, info));
        byte[] okm = new byte[outLen];
        hkdf.generateBytes(okm, 0, outLen);
        return okm;
    }

    /**
     * Generate cryptographically secure random bytes
     */
    public static byte[] randomBytes(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    /**
     * ADDED: Generate random integer
     */
    public static int randomInt() {
        return RNG.nextInt();
    }

    /**
     * ADDED: Generate random positive integer within range
     */
    public static int randomInt(int bound) {
        return RNG.nextInt(bound);
    }

    /**
     * AES-GCM Encryption
     */
    public static byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) throws Exception {
        if (key == null || key.length != KEY_LEN) {
            throw new IllegalArgumentException("Invalid key length");
        }
        if (iv == null || iv.length != IV_LEN) {
            throw new IllegalArgumentException("Invalid IV length");
        }
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec ks = new SecretKeySpec(key, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, ks, spec);

            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new Exception("AES-GCM encryption failed", e);
        }
    }

    /**
     * AES-GCM Decryption
     */
    public static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertext) throws Exception {
        if (key == null || key.length != KEY_LEN) {
            throw new IllegalArgumentException("Invalid key length");
        }
        if (iv == null || iv.length != IV_LEN) {
            throw new IllegalArgumentException("Invalid IV length");
        }
        if (ciphertext == null || ciphertext.length < (GCM_TAG_LENGTH / 8)) {
            throw new IllegalArgumentException("Invalid ciphertext");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec ks = new SecretKeySpec(key, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, ks, spec);

            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            return cipher.doFinal(ciphertext);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new Exception("Authentication failed - message has been tampered with", e);
        } catch (Exception e) {
            throw new Exception("AES-GCM decryption failed", e);
        }
    }

    /**
     * Constant time comparison to prevent timing attacks
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * ADDED: Securely clear sensitive data from memory
     */
    public static void clearBytes(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
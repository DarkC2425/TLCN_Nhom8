package com.example.project.protocol;

import com.example.project.crypto.X25519Util;
import com.example.project.crypto.CryptoUtils;
import com.example.project.storage.SignalProtocolStore.IdentityKeyPair;

/**
 * X3DH (Extended Triple Diffie-Hellman) Key Agreement
 * Used to establish initial shared secret
 */
public class X3DHKeyExchange {

    /**
     * Initiator (Alice) side of X3DH
     *
     * DH1 = DH(IKa, SPKb)
     * DH2 = DH(EKa, IKb)
     * DH3 = DH(EKa, SPKb)
     * DH4 = DH(EKa, OPKb)  [if one-time prekey available]
     *
     * SK = KDF(DH1 || DH2 || DH3 || DH4)
     */
    public byte[] calculateInitiatorSecret(
            IdentityKeyPair ourIdentity,      // IKa
            byte[] theirIdentityPublic,       // IKb
            byte[] theirSignedPreKeyPublic,   // SPKb
            byte[] theirOneTimePreKeyPublic   // OPKb (can be null)
    ) throws Exception {

        // Generate ephemeral key for this session
        X25519Util.KeyPair ephemeralKey = X25519Util.generateKeyPair(); // EKa

        // DH1 = DH(IKa, SPKb)
        byte[] dh1 = X25519Util.dh(ourIdentity.privateKey, theirSignedPreKeyPublic);

        // DH2 = DH(EKa, IKb)
        byte[] dh2 = X25519Util.dh(ephemeralKey.priv, theirIdentityPublic);

        // DH3 = DH(EKa, SPKb)
        byte[] dh3 = X25519Util.dh(ephemeralKey.priv, theirSignedPreKeyPublic);

        // Combine all DH outputs
        byte[] dhOutput;
        if (theirOneTimePreKeyPublic != null) {
            // DH4 = DH(EKa, OPKb)
            byte[] dh4 = X25519Util.dh(ephemeralKey.priv, theirOneTimePreKeyPublic);
            dhOutput = concatenate(dh1, dh2, dh3, dh4);
        } else {
            dhOutput = concatenate(dh1, dh2, dh3);
        }

        // Derive shared secret
        byte[] sharedSecret = CryptoUtils.hkdf(
                null,
                dhOutput,
                "X3DH".getBytes(),
                32
        );

        // Clean up
        CryptoUtils.clearBytes(dh1);
        CryptoUtils.clearBytes(dh2);
        CryptoUtils.clearBytes(dh3);
        CryptoUtils.clearBytes(dhOutput);
        ephemeralKey.destroy();

        return sharedSecret;
    }

    /**
     * Responder (Bob) side of X3DH
     */
    public byte[] calculateResponderSecret(
            IdentityKeyPair ourIdentity,        // IKb
            byte[] theirIdentityPublic,         // IKa
            byte[] theirEphemeralPublic,        // EKa
            byte[] ourOneTimePreKeyPrivate      // OPKb private (can be null)
    ) throws Exception {

        // Load our signed prekey
        // Assume we have it in the identity
        byte[] ourSignedPreKeyPrivate = ourIdentity.privateKey; // Simplified

        // DH1 = DH(SPKb, IKa)
        byte[] dh1 = X25519Util.dh(ourSignedPreKeyPrivate, theirIdentityPublic);

        // DH2 = DH(IKb, EKa)
        byte[] dh2 = X25519Util.dh(ourIdentity.privateKey, theirEphemeralPublic);

        // DH3 = DH(SPKb, EKa)
        byte[] dh3 = X25519Util.dh(ourSignedPreKeyPrivate, theirEphemeralPublic);

        // Combine
        byte[] dhOutput;
        if (ourOneTimePreKeyPrivate != null) {
            // DH4 = DH(OPKb, EKa)
            byte[] dh4 = X25519Util.dh(ourOneTimePreKeyPrivate, theirEphemeralPublic);
            dhOutput = concatenate(dh1, dh2, dh3, dh4);
        } else {
            dhOutput = concatenate(dh1, dh2, dh3);
        }

        // Derive shared secret
        byte[] sharedSecret = CryptoUtils.hkdf(
                null,
                dhOutput,
                "X3DH".getBytes(),
                32
        );

        // Clean up
        CryptoUtils.clearBytes(dh1);
        CryptoUtils.clearBytes(dh2);
        CryptoUtils.clearBytes(dh3);
        CryptoUtils.clearBytes(dhOutput);

        return sharedSecret;
    }

    private byte[] concatenate(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] arr : arrays) {
            totalLength += arr.length;
        }

        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }

        return result;
    }
}
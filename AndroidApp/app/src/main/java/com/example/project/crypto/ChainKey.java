package com.example.project.crypto;

import java.util.Arrays;

public class ChainKey {
    private byte[] ck; // 32 bytes
    private int index;

    public ChainKey(byte[] ck, int index) {
        if (ck == null || ck.length != CryptoUtils.KEY_LEN) {
            throw new IllegalArgumentException("Invalid chain key length");
        }
        this.ck = Arrays.copyOf(ck, ck.length);
        this.index = index;
    }

    // Derive message key and advance chain key
    public MessageKey deriveMessageKey() {
        byte[] info = "RatchetMessageKeys".getBytes();
        byte[] okm = CryptoUtils.hkdf(null, ck, info, CryptoUtils.KEY_LEN * 2);

        byte[] mk = new byte[CryptoUtils.KEY_LEN];
        byte[] ckPrime = new byte[CryptoUtils.KEY_LEN];
        System.arraycopy(okm, 0, mk, 0, CryptoUtils.KEY_LEN);
        System.arraycopy(okm, CryptoUtils.KEY_LEN, ckPrime, 0, CryptoUtils.KEY_LEN);

        Arrays.fill(okm, (byte) 0);

        MessageKey mkObj = new MessageKey(mk, index);

        Arrays.fill(this.ck, (byte) 0);
        this.ck = ckPrime;
        this.index += 1;

        return mkObj;
    }

    public int getIndex() {
        return index;
    }

    // Get chain key for serialization
    public byte[] getCK() {
        return Arrays.copyOf(ck, ck.length);
    }

    public void destroy() {
        if (ck != null) {
            Arrays.fill(ck, (byte) 0);
        }
    }
}
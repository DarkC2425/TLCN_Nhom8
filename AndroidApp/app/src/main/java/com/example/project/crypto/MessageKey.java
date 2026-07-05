package com.example.project.crypto;

import java.util.Arrays;

public class MessageKey {
    private byte[] key; // 32 bytes
    private final int index;
    private byte[] cachedIv; // Cache IV để tránh tính toán lại

    public MessageKey(byte[] key, int index) {
        if (key == null || key.length != CryptoUtils.KEY_LEN) {
            throw new IllegalArgumentException("Invalid message key length");
        }
        this.key = Arrays.copyOf(key, key.length);
        this.index = index;
    }

    public byte[] getKey() {
        return Arrays.copyOf(key, key.length);
    }

    public int getIndex() {
        return index;
    }

    public byte[] getIv() {
        if (cachedIv == null) {
            cachedIv = CryptoUtils.hkdf(null, key, "IV".getBytes(), CryptoUtils.IV_LEN);
        }
        return Arrays.copyOf(cachedIv, cachedIv.length);
    }

    // Xóa dữ liệu nhạy cảm khỏi memory
    public void destroy() {
        if (key != null) {
            Arrays.fill(key, (byte) 0);
            key = null;
        }
        if (cachedIv != null) {
            Arrays.fill(cachedIv, (byte) 0);
            cachedIv = null;
        }
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
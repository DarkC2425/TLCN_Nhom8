package com.example.project.crypto;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class RatchetMessenger {
    private static final String TAG = "RatchetMessenger";
    private static final byte VERSION = 0x01;
    private static final int HEADER_LENGTH = 1 + 32 + 4 + 4; // version + pub + pn + msgNum
    private static final int MAX_SKIP = 100; // Giới hạn số message có thể skip

    private final RatchetState state;

    public RatchetMessenger(RatchetState state) {
        if (state == null) {
            throw new IllegalArgumentException("RatchetState cannot be null");
        }
        this.state = state;
    }

    // Header format: [1][32 senderPub][4 PN][4 msgNum]
    private byte[] packHeader(byte[] senderPub, int pn, int msgNum) {
        if (senderPub == null || senderPub.length != 32) {
            throw new IllegalArgumentException("Invalid sender public key");
        }

        ByteBuffer bb = ByteBuffer.allocate(HEADER_LENGTH);
        bb.put(VERSION);
        bb.put(senderPub);
        bb.putInt(pn);
        bb.putInt(msgNum);
        return bb.array();
    }

    private Header unpackHeader(byte[] header) {
        if (header == null || header.length != HEADER_LENGTH) {
            throw new IllegalArgumentException("Invalid header length");
        }

        ByteBuffer bb = ByteBuffer.wrap(header);
        byte v = bb.get();
        if (v != VERSION) {
            throw new IllegalArgumentException("Unsupported protocol version: " + v);
        }

        byte[] senderPub = new byte[32];
        bb.get(senderPub);
        int pn = bb.getInt();
        int msgNum = bb.getInt();

        // Validate message number
        if (msgNum < 0) {
            throw new IllegalArgumentException("Invalid message number");
        }

        return new Header(senderPub, pn, msgNum);
    }

    public byte[] encrypt(byte[] plaintext) throws Exception {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }

        try {
            // Get current chain index BEFORE deriving the key
            int currentChainIndex = state.sendingChain != null ? state.sendingChain.getIndex() : 0;

            // Get message key
            MessageKey mk = state.nextSendingMessageKey();
            byte[] iv = mk.getIv();
            int pn = 0; // Previous chain length (simplified)

            // Use current chain index for this message
            byte[] header = packHeader(state.getOurPub(), pn, currentChainIndex);

            Log.d(TAG, "Encrypting message #" + currentChainIndex);
            Log.d(TAG, "Sender pub: " + bytesToHex(state.getOurPub()));

            byte[] cipher = CryptoUtils.aesGcmEncrypt(mk.getKey(), iv, header, plaintext);

            // Clean up message key
            mk.destroy();

            // packet = header + cipher
            byte[] out = new byte[header.length + cipher.length];
            System.arraycopy(header, 0, out, 0, header.length);
            System.arraycopy(cipher, 0, out, header.length, cipher.length);

            return out;
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            throw new Exception("Encryption failed: " + e.getMessage(), e);
        }
    }

    public byte[] decrypt(byte[] packet) throws Exception {
        if (packet == null || packet.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("Invalid packet");
        }

        byte[] header = null;
        byte[] cipher = null;

        try {
            // Split header (fixed size) + cipher
            header = Arrays.copyOfRange(packet, 0, HEADER_LENGTH);
            cipher = Arrays.copyOfRange(packet, HEADER_LENGTH, packet.length);

            Header h = unpackHeader(header);

            Log.d(TAG, "Decrypting message #" + h.msgNum);
            Log.d(TAG, "Sender pub from header: " + bytesToHex(h.senderPub));
            Log.d(TAG, "Current their pub: " + bytesToHex(state.getTheirPub()));

            // Check if we need to do DH ratchet
            boolean needRatchet = false;
            if (state.getTheirPub() == null) {
                needRatchet = true;
                Log.d(TAG, "First message - need DH ratchet");
            } else if (!Arrays.equals(h.senderPub, state.getTheirPub())) {
                needRatchet = true;
                Log.d(TAG, "Sender pub changed - need DH ratchet");
            }

            if (needRatchet) {
                state.doDHratchet(h.senderPub);
            }

            // First check skipped keys
            MessageKey skipped = state.retrieveSkippedMessageKey(h.senderPub, h.msgNum);
            MessageKey mk = null;

            if (skipped != null) {
                Log.d(TAG, "Using skipped message key");
                mk = skipped;
            } else {
                // Get current receiving chain index
                int currentIndex = state.receivingChain != null ? state.receivingChain.getIndex() : 0;

                Log.d(TAG, "Current receiving chain index: " + currentIndex);
                Log.d(TAG, "Target message number: " + h.msgNum);

                // If message number matches current index, derive the key
                if (currentIndex == h.msgNum) {
                    mk = state.nextReceivingMessageKey();
                    Log.d(TAG, "Direct key derivation");
                } else if (currentIndex < h.msgNum) {
                    // Need to skip some messages
                    int skipCount = h.msgNum - currentIndex;

                    if (skipCount > MAX_SKIP) {
                        throw new Exception("Too many skipped messages: " + skipCount);
                    }

                    Log.d(TAG, "Skipping " + skipCount + " messages");

                    // Derive and store skipped keys
                    for (int i = 0; i < skipCount; i++) {
                        MessageKey derived = state.nextReceivingMessageKey();
                        state.saveSkippedMessageKey(h.senderPub, derived.getIndex(), derived);
                        Log.d(TAG, "Stored skipped key #" + derived.getIndex());
                    }

                    // Now derive the actual message key
                    mk = state.nextReceivingMessageKey();
                } else {
                    // Current index is already past the message number
                    // This might be a replay attack or out-of-order message
                    throw new Exception("Message number " + h.msgNum + " is too old (current: " + currentIndex + ")");
                }
            }

            if (mk == null) {
                throw new Exception("Failed to derive message key");
            }

            Log.d(TAG, "Using message key index: " + mk.getIndex());

            byte[] iv = mk.getIv();
            byte[] plaintext = CryptoUtils.aesGcmDecrypt(mk.getKey(), iv, header, cipher);

            // Clean up
            mk.destroy();

            Log.d(TAG, "Decryption successful");

            return plaintext;

        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            throw new Exception("Decryption failed: " + e.getMessage(), e);
        }
    }

    // Helper method to convert bytes to hex for debugging
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, 8); i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        if (bytes.length > 8) sb.append("...");
        return sb.toString();
    }

    private static class Header {
        final byte[] senderPub;
        final int pn;
        final int msgNum;

        Header(byte[] senderPub, int pn, int msgNum) {
            this.senderPub = senderPub;
            this.pn = pn;
            this.msgNum = msgNum;
        }
    }
}
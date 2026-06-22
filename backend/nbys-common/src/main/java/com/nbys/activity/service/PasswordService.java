package com.nbys.activity.service;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Service
public class PasswordService {
    public boolean matches(String raw, String encoded) {
        if (raw == null || encoded == null) return false;
        if (encoded.startsWith("scrypt:")) return matchesWerkzeugScrypt(raw, encoded);
        return false;
    }

    public String encodeDefaultPassword() {
        return encode("nb123456");
    }

    public String encode(String raw) {
        return encodeWerkzeugScrypt(raw);
    }

    private boolean matchesWerkzeugScrypt(String raw, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            String[] method = parts[0].split(":");
            int n = Integer.parseInt(method[1]);
            int r = Integer.parseInt(method[2]);
            int p = Integer.parseInt(method[3]);
            byte[] actual = SCrypt.generate(raw.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8), n, r, p, 64);
            byte[] expected = hex(parts[2]);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) {
            return false;
        }
    }

    private String encodeWerkzeugScrypt(String raw) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = toHex(saltBytes).substring(0, 16);
        byte[] hash = SCrypt.generate(raw.getBytes(StandardCharsets.UTF_8), salt.getBytes(StandardCharsets.UTF_8), 32768, 8, 1, 64);
        return "scrypt:32768:8:1$" + salt + "$" + toHex(hash);
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}

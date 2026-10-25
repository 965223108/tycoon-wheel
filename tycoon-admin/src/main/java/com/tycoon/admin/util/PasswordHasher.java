package com.tycoon.admin.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 与其它服务一致的口令哈希：PBKDF2-HMAC-SHA256 / 20 万次 / 独立随机盐。
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 200_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String newSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String hash(String rawPassword, String saltBase64) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    rawPassword.toCharArray(),
                    Base64.getDecoder().decode(saltBase64),
                    ITERATIONS,
                    KEY_BITS);
            byte[] encoded = SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (Exception e) {
            throw new IllegalStateException("口令哈希计算失败", e);
        }
    }

    public static boolean verify(String rawPassword, String saltBase64, String expectedBase64) {
        byte[] actual = hash(rawPassword, saltBase64).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedBase64.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }
}

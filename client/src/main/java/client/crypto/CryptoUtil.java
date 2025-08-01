package client.crypto;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public class CryptoUtil {
    private static final String RSA_ALGO = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int KEY_SIZE = 2048;

    /** Generate an RSA key pair. */
    public static KeyPair generateRSAKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(KEY_SIZE);
        return kpg.generateKeyPair();
    }

    /** Encode a PublicKey to Base64. */
    public static String encodePublicKey(PublicKey pk) {
        return Base64.getEncoder().encodeToString(pk.getEncoded());
    }

    /** Decode a Base64 string to PublicKey with robust error handling. */
    public static PublicKey decodePublicKey(String b64) throws GeneralSecurityException {
        if (b64 == null || b64.trim().isEmpty()) {
            throw new IllegalArgumentException("Public key string is null or empty");
        }
        try {
            // First try standard Base64 decoding
            byte[] data = Base64.getDecoder().decode(b64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (IllegalArgumentException e) {
            System.out.println("Standard Base64 failed, trying URL-safe Base64");
            try {
                // If standard Base64 fails, try URL-safe Base64
                byte[] data = Base64.getUrlDecoder().decode(b64);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            } catch (IllegalArgumentException e2) {
                System.out.println("URL-safe Base64 failed, trying to clean the string");
                // If both fail, try cleaning the string first
                String cleaned = cleanBase64String(b64);
                System.out.println("Original: " + b64.substring(0, Math.min(50, b64.length())));
                System.out.println("Cleaned:  " + cleaned.substring(0, Math.min(50, cleaned.length())));

                byte[] data = Base64.getDecoder().decode(cleaned);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            }
        }
    }

    /** Clean and normalize a Base64 string. */
    private static String cleanBase64String(String base64String) {
        if (base64String == null || base64String.trim().isEmpty()) {
            throw new IllegalArgumentException("Base64 string is null or empty");
        }
        // Remove any whitespace and newlines
        String cleaned = base64String.replaceAll("\\s+", "");
        // Convert URL-safe characters to standard Base64
        cleaned = cleaned.replace('-', '+').replace('_', '/');
        // Add padding if necessary
        int padding = 4 - (cleaned.length() % 4);
        if (padding != 4) {
            cleaned += "=".repeat(padding);
        }
        return cleaned;
    }

    /** Encode a PrivateKey to Base64 (PKCS8). */
    public static String encodePrivateKey(PrivateKey pk) {
        return Base64.getEncoder().encodeToString(pk.getEncoded());
    }

    /** Decode a Base64 string to PrivateKey with robust error handling. */
    public static PrivateKey decodePrivateKey(String b64) throws GeneralSecurityException {
        if (b64 == null || b64.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key string is null or empty");
        }
        try {
            // First try standard Base64 decoding
            byte[] data = Base64.getDecoder().decode(b64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(data);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (IllegalArgumentException e) {
            System.out.println("Standard Base64 failed, trying URL-safe Base64: " + e.getMessage());
            try {
                // If standard Base64 fails, try URL-safe Base64
                byte[] data = Base64.getUrlDecoder().decode(b64);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(data);
                return KeyFactory.getInstance("RSA").generatePrivate(spec);
            } catch (IllegalArgumentException e2) {
                System.out.println("URL-safe Base64 failed, trying to clean the string: " + e2.getMessage());
                // If both fail, try cleaning the string first
                String cleaned = cleanBase64String(b64);
                System.out.println("Original: " + b64.substring(0, Math.min(50, b64.length())));
                System.out.println("Cleaned:  " + cleaned.substring(0, Math.min(50, cleaned.length())));

                byte[] data = Base64.getDecoder().decode(cleaned);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(data);
                return KeyFactory.getInstance("RSA").generatePrivate(spec);
            }
        }
    }

    /** Encrypt plaintext (UTF-8) with RSA public key. Returns Base64 ciphertext. */
    public static String encryptWithPublicKey(String plain, PublicKey pub) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, pub);
        byte[] ct = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ct);
    }

    /** Decrypt Base64 ciphertext with RSA private key. Returns UTF-8 plaintext. */
    public static String decryptWithPrivateKey(String b64cipher, PrivateKey priv) throws GeneralSecurityException {
        if (b64cipher == null || b64cipher.trim().isEmpty()) {
            throw new IllegalArgumentException("Ciphertext is null or empty");
        }
        try {
            // First try standard Base64 decoding
            byte[] ct = Base64.getDecoder().decode(b64cipher);
            Cipher cipher = Cipher.getInstance(RSA_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, priv);
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            System.out.println("Standard Base64 failed, trying URL-safe Base64: " + e.getMessage());
            try {
                // If standard Base64 fails, try URL-safe Base64
                byte[] ct = Base64.getUrlDecoder().decode(b64cipher);
                Cipher cipher = Cipher.getInstance(RSA_ALGO);
                cipher.init(Cipher.DECRYPT_MODE, priv);
                byte[] pt = cipher.doFinal(ct);
                return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e2) {
                System.out.println("URL-safe Base64 failed, trying to clean the string: " + e2.getMessage());
                // If both fail, try cleaning the string first
                String cleaned = cleanBase64String(b64cipher);
                byte[] ct = Base64.getDecoder().decode(cleaned);
                Cipher cipher = Cipher.getInstance(RSA_ALGO);
                cipher.init(Cipher.DECRYPT_MODE, priv);
                byte[] pt = cipher.doFinal(ct);
                return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }
}
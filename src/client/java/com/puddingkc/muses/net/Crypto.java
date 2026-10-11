package com.puddingkc.muses.net;

import com.puddingkc.muses.MusesPlayer;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class Crypto {
    public static final int NONCE_LEN = 32;

    private static final String PUBLIC_KEY_B64 =
            "MCowBQYDK2VwAyEAlIi4xTLTDJFJN0Mc1uWod6Fj43RZjYY7+eDxXFGzv7Y=";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final PublicKey publicKey = loadKey();

    private Crypto() {
    }

    private static PublicKey loadKey() {
        try {
            byte[] der = Base64.getDecoder().decode(PUBLIC_KEY_B64);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static byte[] newNonce() {
        byte[] nonce = new byte[NONCE_LEN];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    public static boolean verify(byte[] nonce, byte[] signature) {
        if (nonce == null || signature == null || nonce.length != NONCE_LEN) return false;
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(nonce);
            return verifier.verify(signature);
        } catch (Exception e) {
            MusesPlayer.LOGGER.warn("signature check failed: {}", e.toString());
            return false;
        }
    }
}

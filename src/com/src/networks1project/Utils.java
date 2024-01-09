package com.src.networks1project;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {

    private Utils() {
    }

    public static byte[] hashByteBuffer(byte[] thisBuffer) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.reset();
        return digest.digest(thisBuffer);
    }
}

package com.hitstdio.fhir.server.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class AESUtil {
    //private final static String IvAES = "";
    private final static String KeyAES = System.getenv("FHIRSERVER_OrgId_PASSWORD");

    public static String Decrypt(String text) {
        try {
            byte[] tobyte = Base64.getDecoder().decode(text);
            //byte[] iv = IvAES.getBytes(StandardCharsets.UTF_8);
            byte[] key = KeyAES.getBytes(StandardCharsets.UTF_8);
            //AlgorithmParameterSpec mAlgorithmParameterSpec = new IvParameterSpec(iv);
            SecretKeySpec mSecretKeySpec = new SecretKeySpec(key, "AES");
            Cipher mCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            mCipher.init(Cipher.DECRYPT_MODE, mSecretKeySpec);

            String byte2string = new String(mCipher.doFinal(tobyte), StandardCharsets.UTF_8);
            return byte2string;
        } catch (Exception ex) {
            return "";
        }
    }
}

package com.qy.citytechupgrade.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;

/**
 * sm4 加密解密 CBC、ECB模式
 */
@SuppressWarnings("restriction")
public class SM4Utils {
    private static String UTF_8 = "UTF-8";
    private static final boolean hexString = false;
    public SM4Utils() { }

    // ECB模式加密
    public static String encryptData_ECB(String plainText,String secretKey) throws Exception {
        Sm4Context ctx = new Sm4Context();
        ctx.isPadding = true;
        ctx.mode = Sm4.SM4_ENCRYPT;

        byte[] keyBytes;
        keyBytes = secretKey.getBytes("UTF-8");
        Sm4 sm4 = new Sm4();
        sm4.sm4_setkey_enc(ctx, keyBytes);
        byte[] encrypted = sm4.sm4_crypt_ecb(ctx, plainText.getBytes(UTF_8));
        String cipherText = new String(Base64.getEncoder().encode(encrypted),"UTF-8");
        if (cipherText != null && cipherText.trim().length() > 0) {
            Pattern p = Pattern.compile("\\s*|\t|\r|\n");
            Matcher m = p.matcher(cipherText);
            cipherText = m.replaceAll("");
        }
        return cipherText;
    }
    // ECB模式解密
    public static String decryptData_ECB(String cipherText,String secretKey) throws Exception {
        Sm4Context ctx = new Sm4Context();
        ctx.isPadding = true;
        ctx.mode = Sm4.SM4_DECRYPT;

        byte[] keyBytes;
        keyBytes = secretKey.getBytes("UTF-8");
        Sm4 sm4 = new Sm4();
        sm4.sm4_setkey_dec(ctx, keyBytes);
        byte[] decrypted = sm4.sm4_crypt_ecb(ctx, Base64.getDecoder().decode(cipherText.getBytes("utf-8")));
        return new String(decrypted, UTF_8);
    }
    // CBC模式加密
    public static String encryptData_CBC(String plainText,String secretKey,String iv) throws Exception {
        Sm4Context ctx = new Sm4Context();
        ctx.isPadding = true;
        ctx.mode = Sm4.SM4_ENCRYPT;

        byte[] keyBytes;
        byte[] ivBytes;

        keyBytes = secretKey.getBytes("UTF-8");
        ivBytes = iv.getBytes("UTF-8");

        Sm4 sm4 = new Sm4();
        sm4.sm4_setkey_enc(ctx, keyBytes);
        byte[] encrypted = sm4.sm4_crypt_cbc(ctx, ivBytes, plainText.getBytes(UTF_8));
        String cipherText = new String(Base64.getEncoder().encode(encrypted),"UTF-8");
        if (cipherText != null && cipherText.trim().length() > 0) {
            Pattern p = Pattern.compile("\\s*|\t|\r|\n");
            Matcher m = p.matcher(cipherText);
            cipherText = m.replaceAll("");
        }
        return cipherText;
    }
    // CBC模式解密
    public static String decryptData_CBC(String cipherText,String secretKey,String iv) throws Exception {
        Sm4Context ctx = new Sm4Context();
        ctx.isPadding = true;
        ctx.mode = Sm4.SM4_DECRYPT;

        byte[] keyBytes;
        byte[] ivBytes;
        if (hexString) {
            keyBytes = SM4HexUtil.hexStringToBytes(secretKey);
            ivBytes = SM4HexUtil.hexStringToBytes(iv);
        } else {
            keyBytes = secretKey.getBytes("UTF-8");
            ivBytes = iv.getBytes("UTF-8");
        }

        Sm4 sm4 = new Sm4();
        sm4.sm4_setkey_dec(ctx, keyBytes);
        byte[] decrypted = sm4.sm4_crypt_cbc(ctx, ivBytes, Base64.getDecoder().decode(cipherText.getBytes("utf-8")));
        return new String(decrypted, UTF_8);
    }

    public static void main(String[] args) throws Exception {
        String plainText = "developer";
        String secretKey = "1234567812345678";
        String iv = secretKey;

        String cipherText = SM4Utils.encryptData_ECB(plainText,secretKey);
        System.out.println("ECB模式加密密文: " + cipherText);

        plainText = SM4Utils.decryptData_ECB(cipherText,secretKey);
        System.out.println("ECB模式解密明文: " + plainText);

        cipherText = SM4Utils.encryptData_CBC(plainText,secretKey,iv);
        System.out.println("CBC模式加密密文: " + cipherText);

        plainText = SM4Utils.decryptData_CBC(cipherText,secretKey,iv);
        System.out.println("CBC模式解密明文: " + plainText);
    }

}

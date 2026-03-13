package com.qy.citytechupgrade.common.util;

import com.qy.citytechupgrade.common.exception.BizException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Key;

@Component
public class CryptoUtils {

    public String md5Lower32(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("MD5计算失败");
        }
    }

    public String desEncrypt(String text, String keyText) {
        try {
            Key key = getDesKey(keyText);
            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return byteArr2HexStr(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException("DES加密失败");
        }
    }

    public String desDecrypt(String cipherText, String keyText) {
        try {
            Key key = getDesKey(keyText);
            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = cipher.doFinal(hexStr2ByteArr(cipherText));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("DES解密失败");
        }
    }

    private Key getDesKey(String keyText) {
        byte[] source = keyText == null ? new byte[0] : keyText.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[8];
        for (int i = 0; i < source.length && i < key.length; i++) {
            key[i] = source[i];
        }
        return new SecretKeySpec(key, "DES");
    }

    private String byteArr2HexStr(byte[] arrB) {
        StringBuilder sb = new StringBuilder(arrB.length * 2);
        for (byte b : arrB) {
            int value = b;
            while (value < 0) {
                value += 256;
            }
            if (value < 16) {
                sb.append('0');
            }
            sb.append(Integer.toString(value, 16));
        }
        return sb.toString();
    }

    private byte[] hexStr2ByteArr(String strIn) {
        String hex = strIn == null ? "" : strIn.trim();
        byte[] bytes = hex.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bytes.length / 2];
        for (int i = 0; i < bytes.length; i += 2) {
            String strTmp = new String(bytes, i, 2, StandardCharsets.UTF_8);
            out[i / 2] = (byte) Integer.parseInt(strTmp, 16);
        }
        return out;
    }
}

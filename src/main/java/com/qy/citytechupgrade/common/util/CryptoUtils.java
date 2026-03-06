package com.qy.citytechupgrade.common.util;

import com.qy.citytechupgrade.common.exception.BizException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

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
            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(normalizeDesKey(keyText), "DES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return toHex(encrypted);
        } catch (Exception e) {
            throw new BizException("DES加密失败");
        }
    }

    public String desDecrypt(String base64Text, String keyText) {
        try {
            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(normalizeDesKey(keyText), "DES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = decodeCipherText(base64Text);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("DES解密失败");
        }
    }

    private byte[] normalizeDesKey(String keyText) {
        byte[] source = keyText == null ? new byte[0] : keyText.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[8];
        for (int i = 0; i < 8; i++) {
            key[i] = i < source.length ? source[i] : 0;
        }
        return key;
    }

    private byte[] decodeCipherText(String cipherText) {
        String normalized = cipherText == null ? "" : cipherText.trim();
        if (isHex(normalized)) {
            return fromHex(normalized);
        }
        return Base64.getDecoder().decode(normalized);
    }

    private boolean isHex(String value) {
        if (value == null || value.isEmpty() || (value.length() % 2 != 0)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}

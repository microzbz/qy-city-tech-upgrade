package com.qy.citytechupgrade.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qy.citytechupgrade.common.util.CryptoUtils;
import com.qy.citytechupgrade.config.AppProperties;
import com.qy.citytechupgrade.utils.SM4Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/mock/qf/api")
@RequiredArgsConstructor
public class MockQfController {
    private final AppProperties appProperties;
    private final CryptoUtils cryptoUtils;
    private final ObjectMapper objectMapper;

    private final Map<String, LocalDateTime> tokenStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> msgSendStore = new ConcurrentHashMap<>();

    @PostMapping("/token")
    public Map<String, Object> token(@RequestParam(required = false) String time,
                                     @RequestParam(required = false) String policyName,
                                     @RequestParam(required = false) String loginSign) {
        String expectedPolicyName = appProperties.getExternal().getQf().getPolicyName();
        String policyPwd = appProperties.getExternal().getQf().getPolicyPwd();

        if (!StringUtils.hasText(time) || !StringUtils.hasText(policyName) || !StringUtils.hasText(loginSign)) {
            return fail("缺少必要参数");
        }
        if (!policyName.equals(expectedPolicyName)) {
            return fail("policyName无效");
        }
        String expectedSign = cryptoUtils.md5Lower32(time + policyName + policyPwd);
        if (!expectedSign.equalsIgnoreCase(loginSign)) {
            return fail("loginSign校验失败");
        }

        String token = "MOCK_TOKEN_" + UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(token, LocalDateTime.now().plusHours(2));
        return success(token);
    }

    @PostMapping("/access")
    public Map<String, Object> access(@RequestParam(required = false) String token,
                                      @RequestParam(required = false) String apiName,
                                      @RequestParam(required = false) String data) {
        if (!isTokenValid(token)) {
            return fail("token无效或已过期");
        }
        if (!StringUtils.hasText(data)) {
            return fail("data不能为空");
        }

        String policyPwd = appProperties.getExternal().getQf().getPolicyPwd();
        final String rawData;
        try {
            rawData = cryptoUtils.desDecrypt(data, policyPwd);
        } catch (Exception e) {
            return fail("data解密失败");
        }

        try {
            if ("getUserInfoByToken".equals(apiName)) {
                String tokenCode = parseParam(rawData, "token");
                if (!StringUtils.hasText(tokenCode)) {
                    tokenCode = parseParam(rawData, "tokenCode");
                }
                if (!StringUtils.hasText(tokenCode)) {
                    return fail("token缺失");
                }
                if (tokenCode.length() < 16) {
                    return fail("token长度不足16");
                }
                String sm4Key = tokenCode.substring(0, 16);
                String userJson = objectMapper.writeValueAsString(buildMockUserInfo(tokenCode));
                String sm4Cipher = SM4Utils.encryptData_ECB(userJson, sm4Key);
                String desCipher = cryptoUtils.desEncrypt(sm4Cipher, policyPwd);
                return success(desCipher);
            }
            if ("msgCentreSend".equals(apiName)) {
                return success(cryptoUtils.desEncrypt(objectMapper.writeValueAsString(buildMockMsgSendResult(rawData)), policyPwd));
            }
            if ("msgCentreSendResult".equals(apiName)) {
                return success(cryptoUtils.desEncrypt(objectMapper.writeValueAsString(buildMockMsgSendQueryResult(rawData)), policyPwd));
            }
            if ("sendSystemMsg".equals(apiName)) {
                return success(cryptoUtils.desEncrypt(objectMapper.writeValueAsString(buildMockSystemMsgResult(rawData)), policyPwd));
            }
            return fail("mock不支持接口: " + apiName);
        } catch (Exception e) {
            return fail("mock生成返回数据失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildMockUserInfo(String tokenCode) {
        String suffix = shortHash(tokenCode).substring(0, 8).toUpperCase();
        Map<String, Object> user = new HashMap<>();
        user.put("id", "mock_user_" + suffix);
        user.put("userName", "本地联调企业" + suffix.substring(0, 4));
        user.put("cn", "本地联调企业" + suffix.substring(0, 4));
        user.put("usertype", "2");
        user.put("idcardtype", "49");
        user.put("idcardnumber", "91441900" + suffix + "0000");
        user.put("legalPerson", "张三");
        user.put("linkPersonName", "李四");
        user.put("linkPersonCode", "441900199001011234");
        user.put("linkPersonType", "10");
        user.put("telPhone", "13800000000");
        user.put("mail", "mock@example.com");
        user.put("isReal", "1");
        return user;
    }

    private Map<String, Object> buildMockMsgSendResult(String rawData) {
        String templateId = parseParam(rawData, "templateId");
        String receiveParamType = parseParam(rawData, "receiveParamType");
        String receiveParam = parseParam(rawData, "receiveParam");
        String dataJson = parseParam(rawData, "dataJson");
        String messageId = UUID.randomUUID().toString();

        Map<String, Object> logData = new HashMap<>();
        logData.put("id", messageId);
        logData.put("templateId", templateId);
        logData.put("receiveParamType", receiveParamType);
        logData.put("receiveParam", receiveParam);
        logData.put("state", "1");
        logData.put("dataJson", dataJson);
        logData.put("sendTime", System.currentTimeMillis());
        msgSendStore.put(messageId, logData);

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 1);
        resp.put("msg", "操作成功");
        resp.put("message", "操作成功");
        resp.put("data", List.of(messageId));
        return resp;
    }

    private Map<String, Object> buildMockMsgSendQueryResult(String rawData) {
        String id = parseParam(rawData, "id");
        Map<String, Object> saved = msgSendStore.get(id);
        if (saved == null) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 0);
            resp.put("msg", "消息不存在");
            resp.put("data", null);
            return resp;
        }

        Map<String, Object> msgSendLog = new HashMap<>(saved);
        msgSendLog.put("createTime", System.currentTimeMillis());
        msgSendLog.put("templateName", "审批结果通知");
        msgSendLog.put("sendType", "1");
        msgSendLog.put("templateTitle", "审批结果通知");
        msgSendLog.put("readState", "0");

        Map<String, Object> detail = new HashMap<>();
        detail.put("sendData", saved.get("dataJson"));
        detail.put("sendResult", "{\"errcode\":0,\"errmsg\":\"ok\"}");
        detail.put("sendState", "1");
        detail.put("sendType", "1");
        detail.put("sendTime", LocalDateTime.now().toString());

        Map<String, Object> data = new HashMap<>();
        data.put("msgSendLog", msgSendLog);
        data.put("msgSendLogDetail", new ArrayList<>(List.of(detail)));

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 1);
        resp.put("msg", "操作成功");
        resp.put("message", "操作成功");
        resp.put("data", data);
        return resp;
    }

    private Map<String, Object> buildMockSystemMsgResult(String rawData) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", UUID.randomUUID().toString());
        data.put("msgTitle", parseParam(rawData, "msgTitle"));
        data.put("bizType", parseParam(rawData, "bizType"));
        data.put("bizNo", parseParam(rawData, "bizNo"));
        data.put("associationList", parseParam(rawData, "associationList"));

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 1);
        resp.put("msg", "操作成功");
        resp.put("data", data);
        return resp;
    }

    private boolean isTokenValid(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        LocalDateTime expireAt = tokenStore.get(token);
        if (expireAt == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(expireAt)) {
            tokenStore.remove(token);
            return false;
        }
        return true;
    }

    private String parseParam(String queryString, String key) {
        if (!StringUtils.hasText(queryString)) {
            return null;
        }
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String k = pair.substring(0, idx);
            String actualKey = k.startsWith("encode_") ? k.substring("encode_".length()) : k;
            if (!key.equals(actualKey)) {
                continue;
            }
            String val = pair.substring(idx + 1);
            if (!StringUtils.hasText(val)) {
                return null;
            }
            if (k.startsWith("encode_")) {
                return URLDecoder.decode(val, StandardCharsets.UTF_8);
            }
            return val;
        }
        return null;
    }

    private String shortHash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
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
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private Map<String, Object> success(String data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 1);
        resp.put("msg", "OK");
        resp.put("data", data);
        return resp;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("msg", msg);
        resp.put("data", "");
        return resp;
    }
}

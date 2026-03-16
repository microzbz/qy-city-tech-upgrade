package com.qy.citytechupgrade.enterprise;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.util.CryptoUtils;
import com.qy.citytechupgrade.config.AppProperties;
import com.qy.citytechupgrade.utils.SM4Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QfClientService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CryptoUtils cryptoUtils;
    private final AppProperties appProperties;

    private String cachedToken;
    private LocalDateTime tokenExpireAt;

    public EnterpriseProfile fetchEnterpriseProfile(String creditCode) {
        if (!appProperties.getExternal().getQf().isEnabled()) {
            throw new BizException("外部企业接口未启用");
        }
        String baseUrl = require(appProperties.getExternal().getQf().getBaseUrl(), "app.external.qf.base-url 未配置");
        String policyName = require(appProperties.getExternal().getQf().getPolicyName(), "app.external.qf.policy-name 未配置");
        String policyPwd = require(appProperties.getExternal().getQf().getPolicyPwd(), "app.external.qf.policy-pwd 未配置");
        String apiName = require(appProperties.getExternal().getQf().getEnterpriseApiName(), "app.external.qf.enterprise-api-name 未配置");

        String token = getToken(baseUrl, policyName, policyPwd);
        String raw = callAccess(baseUrl, token, apiName, "creditCode=" + creditCode, policyPwd);

        Map<String, Object> map = parseFirstObject(raw);
        EnterpriseProfile profile = new EnterpriseProfile();
        profile.setCreditCode(getString(map, "creditCode", "unifiedSocialCreditCode", "socialCreditCode", "uscc", creditCode));
        profile.setEnterpriseName(getString(map, "enterpriseName", "companyName", "name", "未知企业"));
        profile.setAddress(getString(map, "address", "companyAddress", ""));
        profile.setIndustryCode(getString(map, "industryCode", "industrycode", "industry_code", null));
        profile.setIndustryName(getString(map, "industryName", "industryname", "industry_name", null));
        profile.setLegalPerson(getString(map, "legalPerson", "legalRepresentative", null));
        profile.setContactName(getString(map, "contactName", "enterpriseContact", null));
        profile.setContactPhone(getString(map, "contactPhone", "phone", null));
        profile.setRegisterCapital(toDecimal(getString(map, "registerCapital", "regCapital", null)));
        profile.setEmployeeCount(toInt(getString(map, "employeeCount", "staffNumber", null)));
        profile.setDataSource("EXTERNAL");
        return profile;
    }

    public Map<String, Object> fetchSsoUserInfo(String ecspCode) {
        if (!appProperties.getExternal().getQf().isEnabled()) {
            throw new BizException("外部企业接口未启用");
        }
        String code = require(ecspCode, "ecspcode不能为空");
        if (code.length() < 16) {
            throw new BizException("ecspcode长度不足16位");
        }

        String baseUrl = require(appProperties.getExternal().getQf().getBaseUrl(), "app.external.qf.base-url 未配置");
        String policyName = require(appProperties.getExternal().getQf().getPolicyName(), "app.external.qf.policy-name 未配置");
        String policyPwd = require(appProperties.getExternal().getQf().getPolicyPwd(), "app.external.qf.policy-pwd 未配置");
        String apiName = require(appProperties.getExternal().getQf().getSsoApiName(), "app.external.qf.sso-api-name 未配置");
        log.info("[单点登录][企服] 开始获取用户信息，baseUrl={}，apiName={}，ecspcode={}，policyName={}",
            baseUrl, apiName, code, policyName);

        String token = getToken(baseUrl, policyName, policyPwd);
        log.info("[单点登录][企服] 已获取平台 token，token={}", token);
        String desRaw = callAccess(baseUrl, token, apiName, "token=" + code, policyPwd);
        log.info("[单点登录][企服] access 接口 DES 解密后报文长度={}，内容={}", desRaw.length(), desRaw);
        String sm4CipherText = extractSsoCipherText(desRaw);
        log.info("[单点登录][企服] 已提取 SM4 密文，长度={}，内容={}", sm4CipherText.length(), sm4CipherText);
        String sm4Key = code.substring(0, 16);
        log.info("[单点登录][企服] 使用 ecspcode 前缀作为 SM4 密钥，key={}", sm4Key);
        String rawUser = decryptSm4(sm4CipherText, sm4Key);
        log.info("[单点登录][企服] SM4 解密后的用户 JSON 长度={}，内容={}", rawUser.length(), rawUser);
        Map<String, Object> userMap = parseObjectMap(rawUser, "解析SSO用户信息失败");
        if (!hasValidSsoUserInfo(userMap)) {
            log.warn("[单点登录][企服] 解析后的用户信息无效，字段={}", userMap.keySet());
            throw new BizException("没有获取到有效的用户信息");
        }
        log.info("[单点登录][企服] 解析后的用户字段={}", userMap.keySet());
        return userMap;
    }

    private String getToken(String baseUrl, String policyName, String policyPwd) {
        if (cachedToken != null && tokenExpireAt != null && LocalDateTime.now().isBefore(tokenExpireAt.minusMinutes(2))) {
            log.info("[单点登录][企服] 使用缓存的平台 token，过期时间={}", tokenExpireAt);
            return cachedToken;
        }

        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss.SSS"));
        String sign = cryptoUtils.md5Lower32(time + policyName + policyPwd);
        log.info("[单点登录][企服] 开始请求 /token，time={}，sign={}", time, sign);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("time", time);
        form.add("policyName", policyName);
        form.add("loginSign", sign);

        Map<String, Object> json = postForm(baseUrl + "/token", form);
        log.info("[单点登录][企服] /token 响应，code={}，msg={}", json.get("code"), json.get("msg"));
        if (!Integer.valueOf(1).equals(parseCode(json))) {
            throw new BizException("获取企服平台token失败: " + json.get("msg"));
        }
        cachedToken = String.valueOf(json.get("data"));
        tokenExpireAt = LocalDateTime.now().plusHours(2);
        log.info("[单点登录][企服] /token 调用成功，token={}，过期时间={}", cachedToken, tokenExpireAt);
        return cachedToken;
    }

    private String callAccess(String baseUrl, String token, String apiName, String rawData, String policyPwd) {
        log.info("[单点登录][企服] 开始请求 /access，apiName={}，rawData={}，token={}",
            apiName, rawData, token);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        form.add("apiName", apiName);
        form.add("data", cryptoUtils.desEncrypt(rawData, policyPwd));

        Map<String, Object> json = postForm(baseUrl + "/access", form);
        log.info("[单点登录][企服] /access 响应，code={}，msg={}", json.get("code"), json.get("msg"));
        if (!Integer.valueOf(1).equals(parseCode(json))) {
            throw new BizException("调用企服平台接口失败: " + json.get("msg"));
        }
        Object data = json.get("data");
        if (data == null) {
            throw new BizException("企服平台返回空数据");
        }
        String decrypted = cryptoUtils.desDecrypt(String.valueOf(data), policyPwd);
        log.info("[单点登录][企服] /access 数据 DES 解密成功，长度={}，内容={}", decrypted.length(), decrypted);
        return decrypted;
    }

    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        org.springframework.http.HttpEntity<MultiValueMap<String, String>> req = new org.springframework.http.HttpEntity<>(form, headers);
        log.info("[单点登录][企服] POST 请求，url={}，headers={}，form={}", url, headers, form.toSingleValueMap());
        ResponseEntity<String> response = restTemplate.postForEntity(url, req, String.class);
        String body = response.getBody();
        log.info("[单点登录][企服] POST 响应，url={}，status={}，headers={}，bodyLength={}，body={}",
            url,
            response.getStatusCode().value(),
            response.getHeaders(),
            body == null ? 0 : body.length(),
            body);
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BizException("企服平台返回数据格式异常");
        }
    }

    private Integer parseCode(Map<String, Object> json) {
        Object code = json.get("code");
        if (code == null) {
            return -1;
        }
        return Integer.parseInt(String.valueOf(code));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFirstObject(String raw) {
        return parseObjectMap(raw, "解析企业画像数据失败");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObjectMap(String raw, String errorMsg) {
        try {
            Object obj = objectMapper.readValue(raw, Object.class);
            if (obj instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            if (obj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            return new HashMap<>();
        } catch (Exception e) {
            throw new BizException(errorMsg);
        }
    }

    private String decryptSm4(String cipherText, String key16) {
        try {
            String raw = SM4Utils.decryptData_ECB(cipherText, key16);
            log.info("[单点登录][企服] SM4 ECB 解密成功");
            return raw;
        } catch (Exception e) {
            try {
                String raw = SM4Utils.decryptData_CBC(cipherText, key16, key16);
                log.info("[单点登录][企服] SM4 CBC 解密成功");
                return raw;
            } catch (Exception ex) {
                throw new BizException("SM4解密用户信息失败");
            }
        }
    }

    private String extractSsoCipherText(String desRaw) {
        if (!StringUtils.hasText(desRaw)) {
            throw new BizException("企服平台未返回用户信息");
        }
        String trimmed = desRaw.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return trimmed;
        }
        try {
            Object parsed = objectMapper.readValue(trimmed, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Object code = map.get("code");
                Object msg = map.get("msg");
                if (code != null && !"1".equals(String.valueOf(code))) {
                    String message = StringUtils.hasText(String.valueOf(msg))
                        ? String.valueOf(msg)
                        : "code=" + code;
                    throw new BizException("企服平台未返回有效SSO数据: " + message);
                }
                Object data = map.get("data");
                if (!StringUtils.hasText(String.valueOf(data))) {
                    throw new BizException("企服平台返回成功但缺少SSO密文数据");
                }
                return String.valueOf(data).trim();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception ignored) {
        }
        return trimmed;
    }

    private boolean hasValidSsoUserInfo(Map<String, Object> userMap) {
        if (userMap == null || userMap.isEmpty()) {
            return false;
        }
        return hasText(userMap, "id")
            || hasText(userMap, "userName")
            || hasText(userMap, "cn")
            || hasText(userMap, "parUserId")
            || hasText(userMap, "idcardnumber");
    }

    private boolean hasText(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null && StringUtils.hasText(String.valueOf(value));
    }

    private String getString(Map<String, Object> map, String key1, String key2, String key3, String fallback) {
        Object val = map.get(key1);
        if (val == null && key2 != null) {
            val = map.get(key2);
        }
        if (val == null && key3 != null) {
            val = map.get(key3);
        }
        return val == null ? fallback : String.valueOf(val);
    }

    private String getString(Map<String, Object> map, String key1, String key2, String fallback) {
        return getString(map, key1, key2, null, fallback);
    }

    private String getString(Map<String, Object> map, String key1, String key2, String key3, String key4, String fallback) {
        Object val = map.get(key1);
        if (val == null && key2 != null) {
            val = map.get(key2);
        }
        if (val == null && key3 != null) {
            val = map.get(key3);
        }
        if (val == null && key4 != null) {
            val = map.get(key4);
        }
        return val == null ? fallback : String.valueOf(val);
    }

    private BigDecimal toDecimal(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer toInt(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String require(String val, String msg) {
        if (!StringUtils.hasText(val)) {
            throw new BizException(msg);
        }
        return val.trim();
    }

}

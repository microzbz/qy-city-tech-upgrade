package com.qy.citytechupgrade.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.enums.RoleCode;
import com.qy.citytechupgrade.common.enums.UserStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.enterprise.EnterpriseProfile;
import com.qy.citytechupgrade.enterprise.EnterpriseProfileRepository;
import com.qy.citytechupgrade.enterprise.QfClientService;
import com.qy.citytechupgrade.user.SysUser;
import com.qy.citytechupgrade.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final QfClientService qfClientService;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final ObjectMapper objectMapper;

    public LoginResponse login(LoginRequest request) {
        SysUser user = userService.findByUsername(request.getUsername())
            .orElseThrow(() -> new BizException("账号或密码错误"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException("账号已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException("账号或密码错误");
        }

        List<String> roles = userService.getRoleCodesByUserId(user.getId());
        String token = jwtTokenService.createToken(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEnterpriseId(),
            roles
        );

        user.setLastLoginAt(LocalDateTime.now());
        userService.save(user);
        auditService.log(user.getId(), "AUTH", "LOGIN", null, "登录成功");

        return buildLoginResponse(user, roles, token);
    }

    @Transactional
    public LoginResponse ssoLogin(String ecspCode) {
        log.info("[SSO] service start, ecspcode.mask={}, ecspcode.len={}", mask(ecspCode), ecspCode == null ? 0 : ecspCode.length());
        Map<String, Object> userInfo = qfClientService.fetchSsoUserInfo(ecspCode);
        log.info("[SSO] user info fetched, keys={}", userInfo.keySet());
        SysUser user = upsertSsoUser(userInfo);
        log.info("[SSO] user upsert done, userId={}, username={}, enterpriseId={}", user.getId(), user.getUsername(), user.getEnterpriseId());

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException("账号已被禁用");
        }

        userService.ensureRole(user.getId(), RoleCode.ENTERPRISE_USER.name());
        List<String> roles = userService.getRoleCodesByUserId(user.getId());
        log.info("[SSO] roles after ensureRole, userId={}, roles={}", user.getId(), roles);
        String token = jwtTokenService.createToken(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEnterpriseId(),
            roles
        );
        log.info("[SSO] jwt created, userId={}, token.len={}", user.getId(), token == null ? 0 : token.length());

        user.setLastLoginAt(LocalDateTime.now());
        userService.save(user);
        auditService.log(user.getId(), "AUTH", "SSO_LOGIN", null, "SSO登录成功");
        log.info("[SSO] service success, userId={}, username={}", user.getId(), user.getUsername());
        return buildLoginResponse(user, roles, token);
    }

    public LoginResponse.UserInfo me(CurrentUser currentUser) {
        return LoginResponse.UserInfo.builder()
            .userId(currentUser.getUserId())
            .username(currentUser.getUsername())
            .displayName(currentUser.getDisplayName())
            .enterpriseId(currentUser.getEnterpriseId())
            .build();
    }

    private SysUser upsertSsoUser(Map<String, Object> userInfo) {
        String externalId = firstNonBlank(
            readString(userInfo, "id"),
            readString(userInfo, "idcardnumber"),
            readString(userInfo, "parUserId")
        );
        if (!StringUtils.hasText(externalId)) {
            throw new BizException("SSO用户标识缺失");
        }
        log.info("[SSO] resolve externalId={}, usertype={}", mask(externalId), readString(userInfo, "usertype"));

        String username = buildSsoUsername(externalId);
        String displayName = firstNonBlank(readString(userInfo, "userName"), readString(userInfo, "cn"), username);
        EnterpriseProfile enterprise = upsertEnterprise(userInfo, externalId);

        SysUser user = userService.findByUsername(username).orElseGet(SysUser::new);
        boolean isNew = user.getId() == null;
        if (isNew) {
            user.setUsername(username);
            user.setPasswordHash(generateDefaultPasswordHash());
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setDisplayName(limit(displayName, 128));
        user.setEnterpriseId(enterprise == null ? null : enterprise.getId());
        if (!StringUtils.hasText(user.getPasswordHash())) {
            user.setPasswordHash(generateDefaultPasswordHash());
        }
        if (user.getStatus() == null) {
            user.setStatus(UserStatus.ACTIVE);
        }
        log.info("[SSO] preparing user save, username={}, displayName={}, enterpriseId={}, isNew={}",
            username, displayName, enterprise == null ? null : enterprise.getId(), isNew);
        return userService.save(user);
    }

    private EnterpriseProfile upsertEnterprise(Map<String, Object> userInfo, String externalId) {
        Map<String, Object> enterpriseInfo = resolveEnterpriseInfo(userInfo);
        String creditCode = resolveCreditCode(enterpriseInfo, userInfo, externalId);
        String enterpriseName = firstNonBlank(
            readString(enterpriseInfo, "userName"),
            readString(enterpriseInfo, "enterpriseName"),
            readString(enterpriseInfo, "name"),
            readString(enterpriseInfo, "cn"),
            readString(userInfo, "userName"),
            readString(userInfo, "cn"),
            "企服企业_" + externalId
        );
        log.info("[SSO] enterprise upsert start, creditCode.mask={}, enterpriseName={}", mask(creditCode), enterpriseName);

        EnterpriseProfile enterprise = enterpriseProfileRepository.findByCreditCode(creditCode).orElseGet(EnterpriseProfile::new);
        if (enterprise.getId() == null) {
            enterprise.setCreditCode(creditCode);
        }
        if (!StringUtils.hasText(enterprise.getCreditCode())) {
            enterprise.setCreditCode(creditCode);
        }
        enterprise.setEnterpriseName(limit(enterpriseName, 255));
        String legalPerson = firstNonBlank(readString(enterpriseInfo, "legalPerson"), readString(userInfo, "legalPerson"));
        if (StringUtils.hasText(legalPerson)) {
            enterprise.setLegalPerson(limit(legalPerson, 128));
        }
        String contactName = firstNonBlank(
            readString(enterpriseInfo, "linkPersonName"),
            readString(userInfo, "linkPersonName"),
            readString(enterpriseInfo, "contactName")
        );
        if (StringUtils.hasText(contactName)) {
            enterprise.setContactName(limit(contactName, 128));
        }
        String contactPhone = firstNonBlank(
            readString(enterpriseInfo, "telPhone"),
            readString(userInfo, "telPhone"),
            readString(enterpriseInfo, "contactPhone")
        );
        if (StringUtils.hasText(contactPhone)) {
            enterprise.setContactPhone(limit(contactPhone, 64));
        }
        enterprise.setDataSource("SSO");
        EnterpriseProfile saved = enterpriseProfileRepository.save(enterprise);
        log.info("[SSO] enterprise upsert done, id={}, creditCode.mask={}, dataSource={}",
            saved.getId(), mask(saved.getCreditCode()), saved.getDataSource());
        return saved;
    }

    private String resolveCreditCode(Map<String, Object> enterpriseInfo, Map<String, Object> userInfo, String externalId) {
        String creditCode = firstNonBlank(resolveCreditCodeFromMap(enterpriseInfo), resolveCreditCodeFromMap(userInfo));
        if (StringUtils.hasText(creditCode)) {
            return limit(creditCode, 64);
        }
        String placeholderKey = firstNonBlank(readString(enterpriseInfo, "parUserId"), readString(userInfo, "parUserId"), externalId);
        return buildCreditPlaceholder(placeholderKey);
    }

    private String resolveCreditCodeFromMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        String idType = readString(data, "idcardtype");
        String idNumber = readString(data, "idcardnumber");
        if ("49".equals(idType) && StringUtils.hasText(idNumber)) {
            return idNumber;
        }
        return firstNonBlank(
            readString(data, "creditCode"),
            readString(data, "companyCode"),
            readString(data, "tyshxydm")
        );
    }

    private Map<String, Object> resolveEnterpriseInfo(Map<String, Object> userInfo) {
        if ("2".equals(readString(userInfo, "usertype"))) {
            return userInfo;
        }
        Object parUserInfo = userInfo.get("parUserInfo");
        Map<String, Object> parsed = parseObjectMap(parUserInfo);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        return userInfo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObjectMap(Object obj) {
        if (obj == null) {
            return new HashMap<>();
        }
        if (obj instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<>() {});
        }
        if (obj instanceof String s && StringUtils.hasText(s)) {
            try {
                Object parsed = objectMapper.readValue(s, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    return objectMapper.convertValue(map, new TypeReference<>() {});
                }
            } catch (Exception ignored) {
                return new HashMap<>();
            }
        }
        return new HashMap<>();
    }

    private String buildSsoUsername(String externalId) {
        String safe = sanitize(externalId);
        String username = "DG_" + safe;
        if (username.length() <= 64) {
            return username;
        }
        return "DG_" + sha256Hex(externalId).substring(0, 61);
    }

    private String buildCreditPlaceholder(String key) {
        String safe = sanitize(key);
        String raw = "ECSP_" + safe;
        if (raw.length() <= 64) {
            return raw;
        }
        return "ECSP_" + sha256Hex(key).substring(0, 59);
    }

    private String sanitize(String val) {
        String source = StringUtils.hasText(val) ? val.trim() : UUID.randomUUID().toString();
        String safe = source.replaceAll("[^0-9A-Za-z._-]", "_");
        return StringUtils.hasText(safe) ? safe : "UNKNOWN";
    }

    private String generateDefaultPasswordHash() {
        String raw = UUID.randomUUID() + ":" + System.nanoTime();
        return passwordEncoder.encode(raw);
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
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

    private String readString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object val = map.get(key);
        if (val == null) {
            return null;
        }
        String text = String.valueOf(val).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String limit(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private LoginResponse buildLoginResponse(SysUser user, List<String> roles, String token) {
        return LoginResponse.builder()
            .accessToken(token)
            .roles(roles)
            .userInfo(LoginResponse.UserInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .enterpriseId(user.getEnterpriseId())
                .build())
            .build();
    }

    private String mask(String text) {
        if (!StringUtils.hasText(text)) {
            return "***";
        }
        String source = text.trim();
        if (source.length() <= 8) {
            return source.charAt(0) + "***" + source.charAt(source.length() - 1);
        }
        return source.substring(0, 4) + "***" + source.substring(source.length() - 4);
    }
}

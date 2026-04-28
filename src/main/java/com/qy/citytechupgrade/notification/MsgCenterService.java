package com.qy.citytechupgrade.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qy.citytechupgrade.common.enums.SubmissionStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.util.CryptoUtils;
import com.qy.citytechupgrade.common.util.SubmissionNoUtils;
import com.qy.citytechupgrade.config.AppProperties;
import com.qy.citytechupgrade.enterprise.EnterpriseProfile;
import com.qy.citytechupgrade.enterprise.EnterpriseProfileRepository;
import com.qy.citytechupgrade.enterprise.QfClientService;
import com.qy.citytechupgrade.submission.SubmissionBasicInfo;
import com.qy.citytechupgrade.submission.SubmissionBasicInfoRepository;
import com.qy.citytechupgrade.submission.SubmissionForm;
import com.qy.citytechupgrade.submission.SubmissionFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MsgCenterService {
    private static final DateTimeFormatter TOKEN_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss.SSS");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CryptoUtils cryptoUtils;
    private final AppProperties appProperties;
    private final SubmissionFormRepository submissionFormRepository;
    private final SubmissionBasicInfoRepository submissionBasicInfoRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final QfClientService qfClientService;

    private String cachedToken;
    private LocalDateTime tokenExpireAt;

    public void sendApprovalResult(Long submissionId, SubmissionStatus status, String actionName, String comment) {
        AppProperties.External.MsgCenter msgCenterConfig = appProperties.getExternal().getMsgCenter();
        AppProperties.External.Qf qfConfig = appProperties.getExternal().getQf();
        boolean sendMsgCenter = msgCenterConfig.isEnabled();
        boolean sendQfSystemMsg = qfConfig.isEnabled() && qfConfig.isSystemMsgEnabled();
        if (!sendMsgCenter && !sendQfSystemMsg) {
            log.info("[通知发送] 外部通知均未启用，跳过发送，submissionId={}，status={}，actionName={}",
                submissionId, status, actionName);
            return;
        }

        NotificationContext context = loadContext(submissionId);
        if (context == null) {
            return;
        }

        if (sendMsgCenter) {
            try {
                sendApprovalResultInternal(context, status, actionName, comment, msgCenterConfig);
            } catch (Exception e) {
                log.error("[通知中心] 审批结果发送失败，submissionId={}，status={}，actionName={}，message={}",
                    submissionId, status, actionName, e.getMessage(), e);
            }
        } else {
            log.info("[通知中心] 功能未启用，跳过发送，submissionId={}，status={}，actionName={}",
                submissionId, status, actionName);
        }

        if (sendQfSystemMsg) {
            try {
                sendQfSystemMsg(context, status, actionName, comment, qfConfig);
            } catch (Exception e) {
                log.error("[企服站内信] 审批结果发送失败，submissionId={}，status={}，actionName={}，message={}",
                    submissionId, status, actionName, e.getMessage(), e);
            }
        } else {
            log.info("[企服站内信] 功能未启用，跳过发送，submissionId={}，status={}，actionName={}",
                submissionId, status, actionName);
        }
    }

    private NotificationContext loadContext(Long submissionId) {
        SubmissionForm form = submissionFormRepository.findById(submissionId).orElse(null);
        if (form == null) {
            log.warn("[通知发送] 未找到单据，submissionId={}", submissionId);
            return null;
        }
        EnterpriseProfile enterprise = enterpriseProfileRepository.findById(form.getEnterpriseId()).orElse(null);
        if (enterprise == null) {
            log.warn("[通知发送] 未找到企业，submissionId={}，enterpriseId={}", submissionId, form.getEnterpriseId());
            return null;
        }
        SubmissionBasicInfo basic = submissionBasicInfoRepository.findBySubmissionId(submissionId).orElse(null);
        return new NotificationContext(submissionId, form, basic, enterprise);
    }

    private void sendApprovalResultInternal(NotificationContext context,
                                            SubmissionStatus status,
                                            String actionName,
                                            String comment,
                                            AppProperties.External.MsgCenter config) {
        String receiveParamType = defaultIfBlank(config.getReceiveParamType(), "2");
        String receiveParam = resolveReceiveParam(receiveParamType, context.enterprise());
        if (!StringUtils.hasText(receiveParam)) {
            log.warn("[通知中心] 联系人接收参数缺失，submissionId={}，enterpriseId={}，receiveParamType={}，contactName={}，contactCertNo={}，contactPhone={}",
                context.submissionId(), context.enterprise().getId(), receiveParamType, context.enterprise().getContactName(),
                context.enterprise().getContactCertNo(), context.enterprise().getContactPhone());
            return;
        }

        String templateId = require(config.getTemplateId(), "app.external.msg-center.template-id 未配置");
        Map<String, Object> dataJson = buildDataJson(context, actionName, comment);
        Map<String, Object> extData = buildExtData(context, status, actionName, comment, receiveParamType, receiveParam);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("templateId", templateId);
        params.put("receiveParamType", receiveParamType);
        params.put("receiveParam", receiveParam);
        params.put("dataJson", toJson(dataJson));
        params.put("extDataType", defaultIfBlank(config.getExtDataType(), "city-tech-upgrade-approval"));
        params.put("extData", toJson(extData));
        log.info("[通知中心] 开始发送审批结果，submissionId={}，documentNo={}，enterpriseId={}，templateId={}，receiveParamType={}，receiveParam={}，dataJson={}，extData={}",
            context.submissionId(), resolveDocumentNo(context.form(), context.basic(), context.submissionId()), context.enterprise().getId(),
            templateId, receiveParamType, receiveParam, params.get("dataJson"), params.get("extData"));

        String baseUrl = require(config.getBaseUrl(), "app.external.msg-center.base-url 未配置");
        String policyName = require(config.getPolicyName(), "app.external.msg-center.policy-name 未配置");
        String policyPwd = require(config.getPolicyPwd(), "app.external.msg-center.policy-pwd 未配置");

        String token = getToken(baseUrl, policyName, policyPwd);
        Map<String, Object> sendResponse = callAccess(
            baseUrl,
            token,
            require(config.getSendApiName(), "app.external.msg-center.send-api-name 未配置"),
            params,
            policyPwd
        );
        String messageId = extractMessageId(sendResponse);
        log.info("[通知中心] 审批结果发送完成，submissionId={}，documentNo={}，messageId={}，receiveParamType={}，receiveParam={}，actionName={}",
            context.submissionId(), resolveDocumentNo(context.form(), context.basic(), context.submissionId()),
            messageId, receiveParamType, receiveParam, actionName);

        if (config.isQueryResultEnabled() && StringUtils.hasText(messageId)) {
            Map<String, Object> resultResponse = callAccess(
                baseUrl,
                token,
                require(config.getSendResultApiName(), "app.external.msg-center.send-result-api-name 未配置"),
                Map.of("id", messageId),
                policyPwd
            );
            log.info("[通知中心] 审批结果发送状态查询完成，submissionId={}，messageId={}，result={}",
                context.submissionId(), messageId, resultResponse);
        }
    }

    private void sendQfSystemMsg(NotificationContext context,
                                 SubmissionStatus status,
                                 String actionName,
                                 String comment,
                                 AppProperties.External.Qf config) {
        String creditCode = resolveCreditCode(context.basic(), context.enterprise());
        String enterpriseName = resolveEnterpriseName(context.basic(), context.enterprise());
        String documentNo = resolveDocumentNo(context.form(), context.basic(), context.submissionId());
        List<QfClientService.SystemMsgAssociation> associations = buildAssociations(creditCode, enterpriseName);
        if (associations.isEmpty()) {
            log.warn("[企服站内信] 企业信用代码和名称均缺失，按空 associationList 继续发送，submissionId={}，enterpriseId={}",
                context.submissionId(), context.enterprise().getId());
        } else if (!StringUtils.hasText(creditCode) || !StringUtils.hasText(enterpriseName)) {
            log.warn("[企服站内信] 企业信用代码或名称缺失，按可用字段继续发送，submissionId={}，enterpriseId={}，creditCode={}，enterpriseName={}",
                context.submissionId(), context.enterprise().getId(), creditCode, enterpriseName);
        }
        qfClientService.sendSystemMsg(new QfClientService.SystemMsgRequest(
            buildMessageTitle(actionName),
            trimToNull(config.getSystemMsgLink()),
            buildMessageContent(context.form(), context.basic(), context.submissionId(), actionName, comment),
            trimToNull(config.getSystemMsgDeptCode()),
            trimToNull(config.getSystemMsgDeptName()),
            trimToNull(config.getSystemMsgBizType()),
            null,
            associations
        ));
        log.info("[企服站内信] 审批结果发送完成，submissionId={}，documentNo={}，status={}，actionName={}",
            context.submissionId(), documentNo, status, actionName);
    }

    private Map<String, Object> buildDataJson(NotificationContext context,
                                              String actionName,
                                              String comment) {
        String messageContent = buildMessageContent(context.form(), context.basic(), context.submissionId(), actionName, comment);
        String messageTitle = buildMessageTitle(actionName);
        String sendDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> data = new HashMap<>();
        data.put("msg", messageContent);
        data.put("msg_title", messageTitle);
        data.put("msgtitle", messageTitle);
        data.put("msg_sendDate", sendDate);
        data.put("msgsendDate", sendDate);
        data.put("msgsenddate", sendDate);
        return data;
    }

    private String buildMessageTitle(String actionName) {
        if (StringUtils.hasText(actionName)) {
            return "技改城市业务处理意见：" + actionName.trim();
        }
        return "技改城市业务处理意见";
    }

    private String buildMessageContent(SubmissionForm form,
                                       SubmissionBasicInfo basic,
                                       Long submissionId,
                                       String actionName,
                                       String comment) {
        String documentNo = resolveDocumentNo(form, basic, submissionId);
        StringBuilder text = new StringBuilder()
            .append("您好，您提交的新型技改城市项目材料处理结果已更新。")
            .append("\n")
            .append("\n")
            .append("单据号：").append(documentNo)
            .append("\n")
            .append("处理结果：").append(actionName);
        if (StringUtils.hasText(comment)) {
            text.append("\n").append("处理意见：").append(comment.trim());
        }
        return text.toString();
    }

    private Map<String, Object> buildExtData(NotificationContext context,
                                             SubmissionStatus status,
                                             String actionName,
                                             String comment,
                                             String receiveParamType,
                                             String receiveParam) {
        Map<String, Object> data = new HashMap<>();
        data.put("submissionId", context.submissionId());
        data.put("enterpriseId", context.enterprise().getId());
        data.put("enterpriseName", resolveEnterpriseName(context.basic(), context.enterprise()));
        data.put("documentNo", resolveDocumentNo(context.form(), context.basic(), context.submissionId()));
        data.put("approvalStatus", status == null ? null : status.name());
        data.put("approvalResult", actionName);
        data.put("approvalComment", StringUtils.hasText(comment) ? comment.trim() : "");
        data.put("receiveParamType", receiveParamType);
        data.put("receiveParam", receiveParam);
        data.put("contactName", context.enterprise().getContactName());
        data.put("contactCertNo", context.enterprise().getContactCertNo());
        data.put("contactCertType", context.enterprise().getContactCertType());
        data.put("contactPhone", context.enterprise().getContactPhone());
        return data;
    }

    private String resolveReceiveParam(String receiveParamType, EnterpriseProfile enterprise) {
        if ("1".equals(receiveParamType)) {
            return trimToNull(enterprise.getContactPhone());
        }
        return trimToNull(enterprise.getContactCertNo());
    }

    private String extractMessageId(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return String.valueOf(list.get(0));
        }
        return null;
    }

    private String getToken(String baseUrl, String policyName, String policyPwd) {
        if (cachedToken != null && tokenExpireAt != null && LocalDateTime.now().isBefore(tokenExpireAt.minusMinutes(2))) {
            log.info("[通知中心] 使用缓存 token，过期时间={}", tokenExpireAt);
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        String time = LocalDateTime.now().format(TOKEN_TIME_FORMAT);
        form.add("time", time);
        form.add("policyName", policyName);
        form.add("loginSign", cryptoUtils.md5Lower32(time + policyName + policyPwd));
        log.info("[通知中心] 请求 /token，baseUrl={}，time={}，policyName={}，loginSign={}", baseUrl, time, policyName, form.getFirst("loginSign"));

        Map<String, Object> json = postForm(baseUrl + "/token", form);
        if (!Integer.valueOf(1).equals(parseCode(json))) {
            throw new BizException("获取通知中心token失败: " + json.get("msg"));
        }
        cachedToken = String.valueOf(json.get("data"));
        tokenExpireAt = LocalDateTime.now().plusHours(2);
        log.info("[通知中心] 获取 token 成功，token={}，过期时间={}", cachedToken, tokenExpireAt);
        return cachedToken;
    }

    private Map<String, Object> callAccess(String baseUrl,
                                           String token,
                                           String apiName,
                                           Map<String, String> rawParams,
                                           String policyPwd) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        form.add("apiName", apiName);
        String rawData = buildRawData(rawParams);
        form.add("data", cryptoUtils.desEncrypt(rawData, policyPwd));
        log.info("[通知中心] 请求 /access，baseUrl={}，apiName={}，token={}，rawData={}，cipherLength={}",
            baseUrl, apiName, token, rawData, form.getFirst("data") == null ? 0 : form.getFirst("data").length());

        Map<String, Object> json = postForm(baseUrl + "/access", form);
        if (!Integer.valueOf(1).equals(parseCode(json))) {
            throw new BizException("调用通知中心接口失败: " + json.get("msg"));
        }
        String encryptedData = String.valueOf(json.get("data"));
        String decrypted = cryptoUtils.desDecrypt(encryptedData, policyPwd);
        log.info("[通知中心] /access 响应解密成功，apiName={}，encryptedLength={}，decrypted={}",
            apiName, encryptedData == null ? 0 : encryptedData.length(), decrypted);
        try {
            return objectMapper.readValue(decrypted, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BizException("解析通知中心响应失败");
        }
    }

    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        org.springframework.http.HttpEntity<MultiValueMap<String, String>> request = new org.springframework.http.HttpEntity<>(form, headers);
        log.info("[通知中心] POST 请求，url={}，headers={}，form={}", url, headers, form.toSingleValueMap());
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("[通知中心] POST 响应，url={}，status={}，headers={}，body={}",
            url, response.getStatusCode().value(), response.getHeaders(), response.getBody());
        try {
            return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new BizException("通知中心返回数据格式异常");
        }
    }

    private String buildRawData(Map<String, String> params) {
        List<String> pairs = new ArrayList<>();
        params.forEach((key, value) -> {
            if (!StringUtils.hasText(value)) {
                return;
            }
            if (shouldEncode(key)) {
                pairs.add("encode_" + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            } else {
                pairs.add(key + "=" + value);
            }
        });
        return String.join("&", pairs);
    }

    private boolean shouldEncode(String key) {
        return "dataJson".equals(key) || "extData".equals(key);
    }

    private Integer parseCode(Map<String, Object> json) {
        Object code = json.get("code");
        if (code == null) {
            return -1;
        }
        return Integer.parseInt(String.valueOf(code));
    }

    private String resolveEnterpriseName(SubmissionBasicInfo basic, EnterpriseProfile enterprise) {
        if (basic != null && StringUtils.hasText(basic.getEnterpriseName())) {
            return basic.getEnterpriseName().trim();
        }
        return enterprise == null ? null : trimToNull(enterprise.getEnterpriseName());
    }

    private String resolveCreditCode(SubmissionBasicInfo basic, EnterpriseProfile enterprise) {
        if (basic != null && StringUtils.hasText(basic.getCreditCode())) {
            return basic.getCreditCode().trim();
        }
        return enterprise == null ? null : trimToNull(enterprise.getCreditCode());
    }

    private String resolveDocumentNo(SubmissionForm form, SubmissionBasicInfo basic, Long submissionId) {
        if (form != null && StringUtils.hasText(form.getDocumentNo())) {
            return form.getDocumentNo().trim();
        }
        return SubmissionNoUtils.buildNo(resolveEnterpriseName(basic, null), submissionId, 0);
    }

    private List<QfClientService.SystemMsgAssociation> buildAssociations(String creditCode, String enterpriseName) {
        if (StringUtils.hasText(creditCode) && StringUtils.hasText(enterpriseName)) {
            return List.of(new QfClientService.SystemMsgAssociation(creditCode.trim(), enterpriseName.trim()));
        }
        return List.of();
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BizException("通知中心请求报文序列化失败");
        }
    }

    private String require(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record NotificationContext(
        Long submissionId,
        SubmissionForm form,
        SubmissionBasicInfo basic,
        EnterpriseProfile enterprise
    ) {
    }
}

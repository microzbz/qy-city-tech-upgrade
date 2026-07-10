package com.qy.citytechupgrade.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.util.CryptoUtils;
import com.qy.citytechupgrade.config.AppProperties;
import com.qy.citytechupgrade.utils.SM4Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QfClientServiceTest {
    private static final String BASE_URL = "https://qf.example/api/";
    private static final String POLICY_NAME = "新型技改城市平台";
    private static final String POLICY_PWD = "12345678";
    private static final String ECSP_CODE = "1234567890abcdef-sso-code";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CryptoUtils cryptoUtils = new CryptoUtils();

    private MockRestServiceServer server;
    private QfClientService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        AppProperties properties = new AppProperties();
        AppProperties.External.Qf qf = properties.getExternal().getQf();
        qf.setEnabled(true);
        qf.setBaseUrl(BASE_URL);
        qf.setPolicyName(POLICY_NAME);
        qf.setPolicyPwd(POLICY_PWD);
        qf.setSsoApiName("getUserInfoByToken");

        service = new QfClientService(restTemplate, objectMapper, cryptoUtils, properties);
        ReflectionTestUtils.setField(service, "cachedToken", "old-token");
        ReflectionTestUtils.setField(service, "tokenExpireAt", LocalDateTime.now().plusHours(1));
    }

    @Test
    void refreshesRejectedCachedTokenAndRetriesAccessOnce() throws Exception {
        expectAccess("old-token", tokenExpiredResponse());
        expectToken("new-token");
        expectAccess("new-token", successfulSsoResponse());

        Map<String, Object> userInfo = service.fetchSsoUserInfo(ECSP_CODE);

        assertThat(userInfo)
            .containsEntry("id", "user-1")
            .containsEntry("userName", "测试企业用户");
        assertThat(ReflectionTestUtils.getField(service, "cachedToken")).isEqualTo("new-token");
        server.verify();
    }

    @Test
    void throwsAfterSingleRetryWhenRefreshedTokenIsAlsoRejected() throws Exception {
        expectAccess("old-token", tokenExpiredResponse());
        expectToken("new-token");
        expectAccess("new-token", tokenExpiredResponse());

        assertThatThrownBy(() -> service.fetchSsoUserInfo(ECSP_CODE))
            .isInstanceOf(BizException.class)
            .hasMessage("调用企服平台接口失败: 令牌已失效，请重新获取令牌。");
        server.verify();
    }

    private void expectToken(String token) throws Exception {
        server.expect(once(), requestTo(BASE_URL + "token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("policyName=")))
            .andExpect(content().string(containsString("loginSign=")))
            .andRespond(withSuccess(json(Map.of(
                "code", 1,
                "msg", "操作成功",
                "data", token
            )), MediaType.APPLICATION_JSON));
    }

    private void expectAccess(String token, String responseBody) {
        server.expect(once(), requestTo(BASE_URL + "access"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("token=" + token)))
            .andExpect(content().string(containsString("apiName=getUserInfoByToken")))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private String successfulSsoResponse() throws Exception {
        String key = ECSP_CODE.substring(0, 16);
        String userJson = json(Map.of(
            "id", "user-1",
            "userName", "测试企业用户"
        ));
        String sm4CipherText = SM4Utils.encryptData_CBC(userJson, key, key);
        String desCipherText = cryptoUtils.desEncrypt(sm4CipherText, POLICY_PWD);
        return json(Map.of(
            "code", 1,
            "msg", "操作成功",
            "data", desCipherText
        ));
    }

    private String tokenExpiredResponse() throws Exception {
        return json(Map.of(
            "code", 0,
            "msg", "令牌已失效，请重新获取令牌。",
            "message", "令牌已失效，请重新获取令牌。"
        ));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}

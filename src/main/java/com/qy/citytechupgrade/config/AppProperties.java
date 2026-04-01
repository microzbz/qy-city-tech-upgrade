package com.qy.citytechupgrade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private FileStorage fileStorage = new FileStorage();
    private External external = new External();

    @Data
    public static class Jwt {
        private String secret = "city-tech-upgrade-secret-key-change-me-2026";
        private long expireSeconds = 7200;
    }

    @Data
    public static class FileStorage {
        private String basePath = "/data/city-tech-upgrade/uploads";
        private String previewBasePath = "";
        private String subDirPattern = "yyyy/M/d";
    }

    @Data
    public static class External {
        private Qf qf = new Qf();
        private MsgCenter msgCenter = new MsgCenter();

        @Data
        public static class Qf {
            private boolean enabled = false;
            private String baseUrl;
            private String policyName;
            private String policyPwd;
            private String enterpriseApiName = "enterprisePortrait";
            private String ssoApiName = "getUserInfoByToken";
        }

        @Data
        public static class MsgCenter {
            private boolean enabled = false;
            private String baseUrl;
            private String policyName;
            private String policyPwd;
            private String sendApiName = "msgCentreSend";
            private String sendResultApiName = "msgCentreSendResult";
            private String templateId = "c0bc0e91-0582-4749-a93b-4463c0be7f74";
            private String receiveParamType = "2";
            private String extDataType = "city-tech-upgrade-approval";
            private boolean queryResultEnabled = true;
        }
    }
}

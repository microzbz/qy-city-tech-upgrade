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

        @Data
        public static class Qf {
            private boolean enabled = false;
            private String baseUrl;
            private String policyName;
            private String policyPwd;
            private String enterpriseApiName = "enterprisePortrait";
            private String ssoApiName = "getUserInfoByToken";
        }
    }
}

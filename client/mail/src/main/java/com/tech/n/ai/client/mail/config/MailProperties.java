package com.tech.n.ai.client.mail.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mail")
@Data
public class MailProperties {
    
    private String fromAddress;
    private String fromName = "Shrimp TM";
    private String baseUrl;
    private Template template = new Template();
    private Async async = new Async();
    
    @Data
    public static class Template {
        private String verificationSubject = "이메일 인증을 완료해주세요";
        private String passwordResetSubject = "비밀번호 재설정 안내";
    }
    
    @Data
    public static class Async {
        private int corePoolSize = 2;
        private int maxPoolSize = 5;
        private int queueCapacity = 100;
    }
}

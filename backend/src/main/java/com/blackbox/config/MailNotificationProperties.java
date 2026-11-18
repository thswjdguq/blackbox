package com.blackbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 이메일 알림의 발송 여부와 수신 허용 목록(K-41 2·3장). SMTP 연결 설정은 spring.mail.*에 있다.
 * 기본은 꺼짐·빈 목록이라, 설정하지 않은 서버는 아무에게도 보내지 않는다
 */
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailNotificationProperties {

    private boolean enabled;
    private List<String> recipientAllowlist = new ArrayList<>();

    public boolean isEnabled()                                   { return enabled; }
    public void setEnabled(boolean enabled)                      { this.enabled = enabled; }
    public List<String> getRecipientAllowlist()                  { return recipientAllowlist; }
    public void setRecipientAllowlist(List<String> allowlist)    { this.recipientAllowlist = allowlist == null ? new ArrayList<>() : allowlist; }
}

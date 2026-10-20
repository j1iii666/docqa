package com.j111.docqa.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 相关配置，对应 application.yml 里的 docqa.jwt.*
 */
@Component
@ConfigurationProperties(prefix = "docqa.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求至少 32 字节 */
    private String secret;

    /** 签发者，解析时会校验 */
    private String issuer = "docqa";

    /** 有效期（秒） */
    private long expireSeconds = 604800;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }
}

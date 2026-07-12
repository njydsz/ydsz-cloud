package com.njydsz.pmis.common.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 邮件配置属性类
 *
 * <p>封装邮件发送所需的全部配置信息，支持通过 application.yml 配置。
 * 配置项以 {@code remi.notify.email} 为前缀。</p>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * remi:
 *   notify:
 *     email:
 *       enabled: true
 *       protocol: smtp
 *       smtp-host: smtp.example.com
 *       smtp-port: 465
 *       from-name: Remi系统
 *       from-mail: noreply@example.com
 *       password: your-auth-code
 *       encoding: UTF-8
 *       auth: true
 *       starttls: false
 *       connection-timeout: 10000
 *       timeout: 10000
 *       write-timeout: 10000
 *       ssl:
 *         enabled: true
 *         ssl-port: 465
 *         protocols: TLSv1.2
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "remi.notify.email")
public class EmailProperties {

    /** 是否启用邮件模块（默认 true），关闭后 EmailConfiguration 不会自动注册 JavaMailSender 和 EmailService */
    private boolean enabled = true;
    private String protocol = "smtp";
    private String smtpHost;
    @Min(1)
    @Max(65535)
    private int smtpPort = 465;
    private String fromName;
    private String fromMail;
    private String password;
    private String encoding = "UTF-8";
    private boolean auth = true;
    private boolean starttls = false;
    @Min(100)
    private Integer connectionTimeout = 10000;
    @Min(100)
    private Integer timeout = 10000;
    @Min(100)
    private Integer writeTimeout = 10000;
    private SslConfig ssl = new SslConfig();
    private boolean debug = false;
    private String defaultEmailType = "HTML";

    /**
     * 批量邮件发送并行度
     * <p>默认值为可用处理器数，最大不超过 16
     */
    private int batchParallelism = Math.min(Runtime.getRuntime().availableProcessors(), 16);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getFromMail() {
        return fromMail;
    }

    public void setFromMail(String fromMail) {
        this.fromMail = fromMail;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean isAuth() {
        return auth;
    }

    public void setAuth(boolean auth) {
        this.auth = auth;
    }

    public boolean isStarttls() {
        return starttls;
    }

    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Integer writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public SslConfig getSsl() {
        return ssl;
    }

    public void setSsl(SslConfig ssl) {
        this.ssl = ssl;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String getDefaultEmailType() {
        return defaultEmailType;
    }

    public void setDefaultEmailType(String defaultEmailType) {
        this.defaultEmailType = defaultEmailType;
    }

    public int getBatchParallelism() {
        return batchParallelism;
    }

    public void setBatchParallelism(int batchParallelism) {
        this.batchParallelism = batchParallelism;
    }

    /**
     * SSL 配置
     */
    public static class SslConfig {
        /** 是否启用 SSL */
        private boolean enabled = true;
        /** SSL 端口 */
        private Integer sslPort;
        /** SSL 协议版本 */
        private String protocols = "TLSv1.2";
        /** 是否校验服务器身份 */
        private boolean checkServerIdentity = true;
        /** 信任存储路径 */
        private String trustStorePath;
        /** 信任存储密码 */
        private String trustStorePassword;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getSslPort() {
            return sslPort;
        }

        public void setSslPort(Integer sslPort) {
            this.sslPort = sslPort;
        }

        public String getProtocols() {
            return protocols;
        }

        public void setProtocols(String protocols) {
            this.protocols = protocols;
        }

        public boolean isCheckServerIdentity() {
            return checkServerIdentity;
        }

        public void setCheckServerIdentity(boolean checkServerIdentity) {
            this.checkServerIdentity = checkServerIdentity;
        }

        public String getTrustStorePath() {
            return trustStorePath;
        }

        public void setTrustStorePath(String trustStorePath) {
            this.trustStorePath = trustStorePath;
        }

        public String getTrustStorePassword() {
            return trustStorePassword;
        }

        public void setTrustStorePassword(String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
        }
    }
}
package com.njydsz.pmis.common.email.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * 邮件配置类
 *
 * <p>负责邮件发送能力的自动装配，核心功能包括：
 * <ul>
 *   <li>创建并配置 JavaMailSenderImpl 实例</li>
 *   <li>设置 SMTP 连接参数（认证、TLS/SSL、超时等）</li>
 *   <li>支持通过配置文件自定义邮件发送行为</li>
 * </ul>
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * remi:
 *   email:
 *     enabled: true
 *     smtp-host: smtp.example.com
 *     smtp-port: 465
 *     from-mail: noreply@example.com
 *     from-name: Remi系统
 *     password: your-auth-code
 *     connection-timeout: 10000
 *     timeout: 10000
 *     ssl:
 *       enabled: true
 *       ssl-port: 465
 *       protocols: TLSv1.2
 * }</pre>
 *
 * <h3>端口与加密方式对应关系</h3>
 * <ul>
 *   <li><b>465 (SSL)</b>：ssl.enabled=true, starttls=false</li>
 *   <li><b>587 (STARTTLS)</b>：ssl.enabled=false, starttls=true</li>
 *   <li><b>25 (非加密)</b>：ssl.enabled=false, starttls=false（不推荐）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see JavaMailSenderImpl
 * @see EmailProperties
 */
@AutoConfiguration
@EnableConfigurationProperties(EmailProperties.class)
@ConditionalOnProperty(prefix = "remi.notify.email", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(JavaMailSenderImpl.class)
public class EmailConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmailConfiguration.class);

    private final EmailProperties emailProperties;

    public EmailConfiguration(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
    }

    /**
     * 创建并配置 JavaMailSender 实例
     *
     * @return 配置好的 JavaMailSenderImpl 实例
     */
    @Bean
    @ConditionalOnMissingBean(JavaMailSenderImpl.class)
    public JavaMailSenderImpl javaMailSenderImpl() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setProtocol(emailProperties.getProtocol());
        mailSender.setHost(emailProperties.getSmtpHost());
        mailSender.setPort(emailProperties.getSmtpPort());
        mailSender.setDefaultEncoding(emailProperties.getEncoding());
        mailSender.setUsername(emailProperties.getFromMail());
        mailSender.setPassword(emailProperties.getPassword());

        Properties props = buildMailProperties();
        mailSender.setJavaMailProperties(props);

        if (emailProperties.isDebug()) {
            props.setProperty("mail.debug", "true");
        }

        log.info("[EmailConfiguration] JavaMailSender configured, smtpHost={}, smtpPort={}, SSL={}",
                emailProperties.getSmtpHost(), emailProperties.getSmtpPort(), emailProperties.getSsl().isEnabled());

        return mailSender;
    }

    /**
     * 构建邮件发送属性配置
     *
     * <p>根据 EmailProperties 配置项，组装 JavaMail 所需的 Properties，
     * 包括 SMTP 认证、STARTTLS、SSL、超时等参数。
     *
     * @return 邮件发送属性配置
     */
    private Properties buildMailProperties() {
        Properties props = new Properties();

        props.setProperty("mail.smtp.auth", String.valueOf(emailProperties.isAuth()));
        props.setProperty("mail.smtp.starttls.enable", String.valueOf(emailProperties.isStarttls()));
        props.setProperty("mail.smtp.connectiontimeout", String.valueOf(emailProperties.getConnectionTimeout()));
        props.setProperty("mail.smtp.timeout", String.valueOf(emailProperties.getTimeout()));
        props.setProperty("mail.smtp.writetimeout", String.valueOf(emailProperties.getWriteTimeout()));

        EmailProperties.SslConfig sslConfig = emailProperties.getSsl();
        if (sslConfig.isEnabled()) {
            props.setProperty("mail.smtp.ssl.enable", "true");
            props.setProperty("mail.smtp.ssl.protocols", sslConfig.getProtocols());
            props.setProperty("mail.smtp.ssl.checkserveridentity", String.valueOf(sslConfig.isCheckServerIdentity()));

            if (sslConfig.getSslPort() != null) {
                props.setProperty("mail.smtp.socketFactory.port", String.valueOf(sslConfig.getSslPort()));
            }

            if (sslConfig.getTrustStorePath() != null && !sslConfig.getTrustStorePath().isBlank()) {
                props.setProperty("mail.smtp.ssl.trust", sslConfig.getTrustStorePath());
            }
        } else {
            props.setProperty("mail.smtp.ssl.enable", "false");
        }

        if (emailProperties.isStarttls() && !sslConfig.isEnabled()) {
            props.setProperty("mail.smtp.starttls.required", "true");
        }

        return props;
    }
}
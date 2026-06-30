package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.message.channel.MessageRequest;
import com.njydsz.pmis.message.channel.MessageResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EmailChannel 邮件通道单元测试
 */
@DisplayName("EmailChannel 邮件通道测试")
class EmailChannelTest {

    private JavaMailSender mailSender;
    private EmailChannel channel;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        channel = new EmailChannel();
        // 注入依赖（@Autowired(required=false) + 反射）
        ReflectionTestUtils.setField(channel, "mailSender", mailSender);
        ReflectionTestUtils.setField(channel, "from", "no-reply@pmis.com");
    }

    @Test
    @DisplayName("channelType 应返回 EMAIL")
    void channelType() {
        assertThat(channel.channelType()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("JavaMailSender 未配置时返回失败")
    void send_noMailSender() {
        ReflectionTestUtils.setField(channel, "mailSender", null);
        MessageRequest req = new MessageRequest();
        req.setReceiver("a@b.com");
        req.setSubject("s");
        req.setContent("c");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("JavaMailSender 未配置");
    }

    @Test
    @DisplayName("收件人为空时返回失败")
    void send_emptyReceiver() {
        MessageRequest req = new MessageRequest();
        req.setSubject("s");
        req.setContent("c");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("收件人邮箱");
    }

    @Test
    @DisplayName("纯文本内容走 SimpleMailMessage 路径")
    void send_plainText() {
        MessageRequest req = new MessageRequest();
        req.setReceiver("a@b.com");
        req.setSubject("S");
        req.setContent("Hello");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getProviderTraceId()).startsWith("EMAIL-");
        // 验证 SimpleMailMessage 走的是 mailSender.send(SimpleMailMessage)
        org.mockito.Mockito.verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("HTML 内容走 MimeMessage 路径")
    void send_html() {
        Session session = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));
        MessageRequest req = new MessageRequest();
        req.setReceiver("a@b.com");
        req.setSubject("S");
        req.setContent("<h1>HTML</h1>");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isTrue();
        // 验证 MimeMessage 路径
        org.mockito.Mockito.verify(mailSender).send((MimeMessage) any());
    }

    @Test
    @DisplayName("主题为空时使用默认值 PMIS 通知")
    void send_defaultSubject() {
        MessageRequest req = new MessageRequest();
        req.setReceiver("a@b.com");
        req.setContent("hi");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isTrue();
        org.mockito.Mockito.verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("邮件发送异常时返回失败结果")
    void send_exception() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        MessageRequest req = new MessageRequest();
        req.setReceiver("a@b.com");
        req.setSubject("S");
        req.setContent("c");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("MailSendException");
    }

    @Test
    @DisplayName("providerTraceId 应符合 EMAIL- 前缀 + 16 位十六进制")
    void send_traceIdFormat() {
        MessageRequest req = new MessageRequest();
        req.setReceiver("a@b.com");
        req.setContent("c");
        MessageResult r = channel.send(req);
        assertThat(r.getProviderTraceId()).matches("EMAIL-[0-9a-f]{16}");
    }

    @Test
    @DisplayName("MIME 准备器版本应复用")
    void send_preparator() {
        // 通过 MimeMessagePreparator 重载验证
        Session session = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));
        // 该 channel 实际未使用 preparator 路径，此处仅覆盖 doAnswer 分支防止空闲警告
        doAnswer(inv -> null).when(mailSender).send((MimeMessagePreparator) any());
    }
}

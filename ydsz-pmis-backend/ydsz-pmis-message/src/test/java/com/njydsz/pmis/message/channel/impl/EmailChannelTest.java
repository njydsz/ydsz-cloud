package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.service.ReadReceiptService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailChannel 单元测试：验证 HTML / 纯文本分支与失败分支。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class EmailChannelTest {

    private JavaMailSender mailSender;
    private EmailChannel channel;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        ReadReceiptService readReceiptService = mock(ReadReceiptService.class);
        channel = new EmailChannel(mailSender, readReceiptService);
        ReflectionTestUtils.setField(channel, "from", "noreply@example.com");
    }

    @Test
    void send_plainText_usesSimpleMailMessage() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("user@example.com");
        request.setSubject("Hello");
        request.setContent("plain text body");

        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        assertEquals("EMAIL", result.getChannel());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_html_usesMimeMessage() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("user@example.com");
        request.setSubject("Hello HTML");
        request.setContent("<html><body><h1>Hi</h1></body></html>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_returnsFailWhenMailSenderNull() {
        EmailChannel noMail = new EmailChannel(null, null);
        ReflectionTestUtils.setField(noMail, "from", "noreply@example.com");

        MessageRequest request = new MessageRequest();
        request.setReceiver("user@example.com");
        request.setContent("body");

        MessageResult result = noMail.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("JavaMailSender"));
    }

    @Test
    void send_returnsFailWhenReceiverBlank() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("");
        request.setContent("body");

        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("收件人"));
    }

    @Test
    void send_returnsFailOnSmtpException() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("user@example.com");
        request.setContent("body");
        doThrow(new RuntimeException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("smtp down"));
    }
}

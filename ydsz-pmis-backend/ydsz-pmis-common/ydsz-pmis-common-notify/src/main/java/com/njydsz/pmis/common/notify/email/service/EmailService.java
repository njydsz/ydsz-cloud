package com.njydsz.pmis.common.email.service;

import com.njydsz.pmis.common.email.config.EmailProperties;
import com.njydsz.pmis.common.email.domain.Email;
import com.njydsz.pmis.common.email.domain.EmailAttachment;
import com.njydsz.pmis.common.email.domain.EmailInlineResource;
import com.njydsz.pmis.common.email.domain.SendResult;
import com.njydsz.pmis.common.email.enums.EmailType;
import com.njydsz.pmis.common.email.listener.EmailSendListener;
import com.njydsz.pmis.common.util.ExecutorUtils;
import com.njydsz.pmis.common.util.StringUtils;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 邮件服务核心�?
 *
 * <p>提供完善的邮件发送能力，支持�?
 * <ul>
 *   <li>纯文本邮件发�?/li>
 *   <li>HTML邮件发�?/li>
 *   <li>带附件邮件发�?/li>
 *   <li>内嵌资源邮件发送（如嵌入图片）</li>
 *   <li>Thymeleaf模板邮件发�?/li>
 *   <li>Freemarker模板邮件发�?/li>
 *   <li>批量邮件发�?/li>
 *   <li>异步发送模�?/li>
 *   <li>发送结果统一封装</li>
 *   <li>发送过程监听回�?/li>
 * </ul>
 *
 * <h3>快速开�?/h3>
 * <pre>{@code
 * @Resource
 * private EmailService emailService;
 *
 * // 发送HTML邮件
 * Email email = Email.builder()
 *         .to("user@example.com")
 *         .subject("测试邮件")
 *         .content("<h1>Hello World</h1>")
 *         .build();
 * SendResult result = emailService.send(email);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final String FREEMARKER_TEMPLATE_SUFFIX = ".ftl";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final Configuration freemarkerConfiguration;
    private final EmailProperties emailProperties;

    private final List<EmailSendListener> listeners = new ArrayList<>();
    private final Validator validator;

    /**
     * 并行邮件发送线程池（按需懒初始化�?
     */
    private volatile Executor batchExecutor;

    /**
     * 注册邮件发送监听器
     *
     * <p>注册后，监听器将在邮件发送前、发送成功、发送失败时收到回调�?
     * 多个监听器按 {@link EmailSendListener#getOrder()} 排序后依次调用�?
     *
     * @param listener 邮件发送监听器，为 null 时忽�?
     */
    public void registerListener(EmailSendListener listener) {
        if (listener != null) {
            listeners.add(listener);
            listeners.sort(Comparator.comparingInt(EmailSendListener::getOrder));
        }
    }

    /**
     * 注销邮件发送监听器
     *
     * @param listener 待注销的邮件发送监听器，为 null 时忽�?
     */
    public void unregisterListener(EmailSendListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * 发送邮件（自动生成追踪ID�?
     *
     * <p>根据邮件对象中设定的类型（TEXT/HTML/ATTACHMENT/INLINE/THYMELEAF/FREEMARKER），
     * 自动选择对应的发送方式进行发送�?
     *
     * @param email 邮件对象，不能为 null
     * @return 发送结果，包含成功状态、消息ID、发送时间等信息
     */
    public SendResult send(Email email) {
        return sendWithTrace(UUID.randomUUID().toString(), email);
    }

    /**
     * 发送邮件（指定追踪ID，用于链路追踪）
     *
     * @param traceId 追踪ID，便于日志串�?
     * @param email   邮件对象，不能为 null
     * @return 发送结果，包含成功状态、消息ID、发送时间等信息
     */
    public SendResult sendWithTrace(String traceId, Email email) {
        if (email == null) {
            return SendResult.failure("邮件内容不能为空", new String[0]);
        }

        List<String> validationErrors = validateEmail(email);
        if (!validationErrors.isEmpty()) {
            return SendResult.failure(String.join("; ", validationErrors), email.getToArray());
        }

        triggerBeforeSend(email);

        try {
            SendResult result = doSend(traceId, email);
            triggerSuccess(email, result);
            return result;
        } catch (Exception e) {
            triggerFailure(email, e);
            return SendResult.failure(e.getMessage(), email.getToArray());
        }
    }

    /**
     * 校验邮件对象
     *
     * <p>使用 Jakarta Bean Validation 对邮件对象进行校验，返回所有不符合约束的错误信息�?
     *
     * @param email 待校验的邮件对象
     * @return 校验错误信息列表，无错误时返回空列表；邮件对象为 null 时返回包含提示信息的列表
     */
    public List<String> validateEmail(Email email) {
        List<String> errors = new ArrayList<>();
        if (email == null) {
            errors.add("邮件内容不能为空");
            return errors;
        }

        Set<ConstraintViolation<Email>> violations = validator.validate(email);
        for (ConstraintViolation<Email> violation : violations) {
            errors.add(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        return errors;
    }

    /**
     * 异步发送邮件（自动生成追踪ID�?
     *
     * <p>基于 Spring {@link Async} 注解异步执行，方法立即返回，不等待发送结果�?
     *
     * @param email 邮件对象，不能为 null
     */
    @Async
    public void sendAsync(Email email) {
        send(email);
    }

    /**
     * 异步发送邮件（指定追踪ID�?
     *
     * @param traceId 追踪ID，便于日志串�?
     * @param email   邮件对象，不能为 null
     */
    @Async
    public void sendAsyncWithTrace(String traceId, Email email) {
        sendWithTrace(traceId, email);
    }

    /**
     * 发送纯文本邮件（同步）
     *
     * <p>邮件内容将作为纯文本发送，不会解析HTML标签�?
     *
     * @param email 邮件对象，将自动设置邮件类型�?TEXT
     * @return 发送结�?
     */
    public SendResult sendTextMail(Email email) {
        email.setEmailType(EmailType.TEXT);
        return send(email);
    }

    /**
     * 发送纯文本邮件（异步）
     *
     * @param email 邮件对象，将自动设置邮件类型�?TEXT
     */
    @Async
    public void sendTextMailAsync(Email email) {
        sendTextMail(email);
    }

    /**
     * 发送HTML邮件（同步）
     *
     * <p>邮件内容作为富文本HTML发送，支持HTML标签和CSS样式�?
     *
     * @param email 邮件对象，将自动设置邮件类型�?HTML
     * @return 发送结�?
     */
    public SendResult sendHtmlMail(Email email) {
        email.setEmailType(EmailType.HTML);
        return send(email);
    }

    /**
     * 发送HTML邮件（异步）
     *
     * @param email 邮件对象，将自动设置邮件类型�?HTML
     */
    @Async
    public void sendHtmlMailAsync(Email email) {
        sendHtmlMail(email);
    }

    /**
     * 发送带附件邮件（同步）
     *
     * <p>邮件以HTML格式发送，并附�?{@link Email#getAttachments()} 中指定的附件文件�?
     *
     * @param email 邮件对象，需设置附件列表，将自动设置邮件类型�?ATTACHMENT
     * @return 发送结�?
     */
    public SendResult sendAttachmentsMail(Email email) {
        email.setEmailType(EmailType.ATTACHMENT);
        return send(email);
    }

    /**
     * 发送带附件邮件（异步）
     *
     * @param email 邮件对象，需设置附件列表，将自动设置邮件类型�?ATTACHMENT
     */
    @Async
    public void sendAttachmentsMailAsync(Email email) {
        sendAttachmentsMail(email);
    }

    /**
     * 发送内嵌资源邮件（同步�?
     *
     * <p>支持在HTML邮件中内嵌图片等资源，通过 {@link Email#getInlineResources()} 指定内嵌资源�?
     * 如果同时设置了附件，附件也会一并发送�?
     *
     * @param email 邮件对象，需设置内嵌资源列表，将自动设置邮件类型�?INLINE
     * @return 发送结�?
     */
    public SendResult sendInlineMail(Email email) {
        email.setEmailType(EmailType.INLINE);
        return send(email);
    }

    /**
     * 发送内嵌资源邮件（异步�?
     *
     * @param email 邮件对象，需设置内嵌资源列表，将自动设置邮件类型�?INLINE
     */
    @Async
    public void sendInlineMailAsync(Email email) {
        sendInlineMail(email);
    }

    /**
     * 发送Thymeleaf模板邮件（同步）
     *
     * <p>使用Thymeleaf模板引擎渲染邮件内容，需设置 {@link Email#getTemplate()} 指定模板名称�?
     * 并通过 {@link Email#getVariables()} 传入模板变量�?
     * 需要项目引�?spring-boot-starter-thymeleaf 依赖�?
     *
     * @param email 邮件对象，需设置模板名称和模板变量，将自动设置邮件类型为 THYMELEAF
     * @return 发送结�?
     */
    public SendResult sendThymeleafMail(Email email) {
        email.setEmailType(EmailType.THYMELEAF);
        return send(email);
    }

    /**
     * 发送Thymeleaf模板邮件（异步）
     *
     * @param email 邮件对象，需设置模板名称和模板变量，将自动设置邮件类型为 THYMELEAF
     */
    @Async
    public void sendThymeleafMailAsync(Email email) {
        sendThymeleafMail(email);
    }

    /**
     * 发送Freemarker模板邮件（同步）
     *
     * <p>使用Freemarker模板引擎渲染邮件内容，需设置 {@link Email#getTemplate()} 指定模板名称
     * （不�?.ftl 后缀），并通过 {@link Email#getVariables()} 传入模板变量�?
     * 需要项目引�?spring-boot-starter-freemarker 依赖�?
     *
     * @param email 邮件对象，需设置模板名称和模板变量，将自动设置邮件类型为 FREEMARKER
     * @return 发送结�?
     */
    public SendResult sendFreemarkerMail(Email email) {
        email.setEmailType(EmailType.FREEMARKER);
        return send(email);
    }

    /**
     * 发送Freemarker模板邮件（异步）
     *
     * @param email 邮件对象，需设置模板名称和模板变量，将自动设置邮件类型为 FREEMARKER
     */
    @Async
    public void sendFreemarkerMailAsync(Email email) {
        sendFreemarkerMail(email);
    }

    /**
     * 批量发送邮件（同步等待全部完成�?
     *
     * <p>根据配置 {@code email.batch-parallelism} 自动选择串行或并行发送：
     * <ul>
     *   <li>并行�?<= 1 时，使用串行发�?/li>
     *   <li>并行�?> 1 时，使用固定大小线程池并行发�?/li>
     * </ul>
     *
     * @param emails 邮件列表，可以为�?
     * @return 每一封邮件的发送结果列表，顺序与输入邮件列表一�?
     */
    public List<SendResult> batchSend(List<Email> emails) {
        List<SendResult> results = new ArrayList<>();
        if (emails == null || emails.isEmpty()) {
            return results;
        }

        String traceId = UUID.randomUUID().toString();
        int parallelism = emailProperties.getBatchParallelism();
        if (parallelism <= 1) {
            return batchSendSerial(traceId, emails);
        }

        log.info("[Email] 开始批量并行发送邮件，�?{} 封，并行�?{}，traceId={}", emails.size(), parallelism, traceId);

        Executor executor = getBatchExecutor(parallelism);
        List<CompletableFuture<SendResult>> futures = new ArrayList<>();

        for (int i = 0; i < emails.size(); i++) {
            final int index = i;
            final Email email = emails.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return sendWithTrace(traceId + "-" + index, email);
                } catch (Exception e) {
                    return SendResult.failure(e.getMessage(), email != null ? email.getToArray() : new String[0]);
                }
            }, executor));
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture<?>[0])
        );
        allOf.join();

        for (CompletableFuture<SendResult> future : futures) {
            results.add(future.join());
        }

        long successCount = results.stream().filter(SendResult::isSuccess).count();
        log.info("[Email] 批量并行发送完成，成功 {} / {} 封，traceId={}", successCount, emails.size(), traceId);

        return results;
    }

    /**
     * 串行批量发送（并行�?=1时使用）
     */
    private List<SendResult> batchSendSerial(String traceId, List<Email> emails) {
        List<SendResult> results = new ArrayList<>();
        log.info("[Email] 开始批量串行发送邮件，�?{} 封，traceId={}", emails.size(), traceId);

        for (int i = 0; i < emails.size(); i++) {
            Email email = emails.get(i);
            try {
                SendResult result = sendWithTrace(traceId + "-" + i, email);
                results.add(result);
            } catch (Exception e) {
                results.add(SendResult.failure(e.getMessage(), email != null ? email.getToArray() : new String[0]));
            }
        }

        long successCount = results.stream().filter(SendResult::isSuccess).count();
        log.info("[Email] 批量串行发送完成，成功 {} / {} 封，traceId={}", successCount, emails.size(), traceId);

        return results;
    }

    /**
     * 获取批量发送线程池（懒初始化）
     */
    private Executor getBatchExecutor(int parallelism) {
        if (batchExecutor == null) {
            synchronized (this) {
                if (batchExecutor == null) {
                    batchExecutor = ExecutorUtils.newFixedThreadPool(parallelism, "email-batch-sender");
                }
            }
        }
        return batchExecutor;
    }

    /**
     * 批量发送邮件（异步，立即返回不等待结果�?
     *
     * @param emails 邮件列表，可以为�?
     */
    @Async
    public void batchSendAsync(List<Email> emails) {
        batchSend(emails);
    }

    /**
     * 根据邮件类型分发到具体的发送方�?
     *
     * @param traceId 追踪ID
     * @param email   邮件对象
     * @return 发送结�?
     * @throws MessagingException 发送过程中可能抛出的异�?
     */
    private SendResult doSend(String traceId, Email email) throws MessagingException {
        EmailType emailType = email.getEmailType();

        log.debug("[Email] 开始发送邮件，traceId={}, type={}, to={}", traceId, emailType, email.getTo());

        return switch (emailType) {
            case TEXT -> sendSimpleTextMail(traceId, email);
            case HTML -> sendHtmlEmail(traceId, email);
            case ATTACHMENT -> sendAttachmentEmail(traceId, email);
            case INLINE -> sendInlineResourceEmail(traceId, email);
            case THYMELEAF -> sendThymeleafTemplateEmail(traceId, email);
            case FREEMARKER -> sendFreemarkerTemplateEmail(traceId, email);
        };
    }

    /**
     * 发送纯文本邮件
     *
     * @param traceId 追踪ID
     * @param email   邮件对象
     * @return 发送结�?
     * @throws MessagingException 发送过程中可能抛出的异�?
     */
    private SendResult sendSimpleTextMail(String traceId, Email email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, DEFAULT_ENCODING);

        fillBasicFields(helper, email);
        helper.setText(email.getContent(), false);

        mailSender.send(message);

        log.info("[Email] 文本邮件发送成功，traceId={}, 收件�?{}", traceId, email.getTo());
        return buildSendResult(traceId, email, message);
    }

    private SendResult sendHtmlEmail(String traceId, Email email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, DEFAULT_ENCODING);

        fillBasicFields(helper, email);
        helper.setText(email.getContent(), true);

        mailSender.send(message);

        log.info("[Email] HTML邮件发送成功，traceId={}, 收件�?{}", traceId, email.getTo());
        return buildSendResult(traceId, email, message);
    }

    /**
     * 发送带附件邮件
     *
     * @param traceId 追踪ID
     * @param email   邮件对象
     * @return 发送结�?
     * @throws MessagingException 发送过程中可能抛出的异�?
     */
    private SendResult sendAttachmentEmail(String traceId, Email email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, DEFAULT_ENCODING);

        fillBasicFields(helper, email);
        helper.setText(email.getContent(), true);
        addAttachments(helper, email);

        mailSender.send(message);

        log.info("[Email] 附件邮件发送成功，traceId={}, 收件�?{}, 附件�?{}",
                traceId, email.getTo(), email.getAttachments() != null ? email.getAttachments().size() : 0);
        return buildSendResult(traceId, email, message);
    }

    /**
     * 发送内嵌资源邮�?
     *
     * @param traceId 追踪ID
     * @param email   邮件对象
     * @return 发送结�?
     * @throws MessagingException 发送过程中可能抛出的异�?
     */
    private SendResult sendInlineResourceEmail(String traceId, Email email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, DEFAULT_ENCODING);

        fillBasicFields(helper, email);
        helper.setText(email.getContent(), true);
        addInlineResources(helper, email);
        addAttachments(helper, email);

        mailSender.send(message);

        log.info("[Email] 内嵌资源邮件发送成功，traceId={}, 收件�?{}", traceId, email.getTo());
        return buildSendResult(traceId, email, message);
    }

    /**
     * 发送Thymeleaf模板邮件
     *
     * @param traceId 追踪ID
     * @param email   邮件对象
     * @return 发送结�?
     * @throws MessagingException 发送过程中可能抛出的异�?
     */
    private SendResult sendThymeleafTemplateEmail(String traceId, Email email) throws MessagingException {
        if (templateEngine == null) {
            throw new IllegalStateException("Thymeleaf模板引擎未配置，请引�?spring-boot-starter-thymeleaf 依赖");
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, DEFAULT_ENCODING);

        fillBasicFields(helper, email);

        Context context = new Context();
        if (email.getVariables() != null && !email.getVariables().isEmpty()) {
            context.setVariables(email.getVariables());
        }

        String templateName = email.getTemplate();
        if (StringUtils.isBlank(templateName)) {
            throw new IllegalArgumentException("Thymeleaf模板名称不能为空");
        }

        String htmlContent = templateEngine.process(templateName, context);
        helper.setText(htmlContent, true);

        addAttachments(helper, email);

        mailSender.send(message);

        log.info("[Email] Thymeleaf模板邮件发送成功，traceId={}, 收件�?{}", traceId, email.getTo());
        return buildSendResult(traceId, email, message);
    }

    /**
     * 发送Freemarker模板邮件
     *
     * @param traceId 追踪ID
     * @param email   邮件对象
     * @return 发送结�?
     * @throws MessagingException 发送过程中可能抛出的异�?
     */
    private SendResult sendFreemarkerTemplateEmail(String traceId, Email email) throws MessagingException {
        if (freemarkerConfiguration == null) {
            throw new IllegalStateException("Freemarker模板引擎未配置，请引�?spring-boot-starter-freemarker 依赖");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, DEFAULT_ENCODING);

            fillBasicFields(helper, email);

            String templateName = email.getTemplate();
            if (StringUtils.isBlank(templateName)) {
                throw new IllegalArgumentException("Freemarker模板名称不能为空");
            }

            Template template = freemarkerConfiguration.getTemplate(templateName + FREEMARKER_TEMPLATE_SUFFIX);
            Map<String, Object> variables = email.getVariables() != null ? email.getVariables() : Collections.emptyMap();
            String htmlContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, variables);

            helper.setText(htmlContent, true);

            addAttachments(helper, email);

            mailSender.send(message);

            log.info("[Email] Freemarker模板邮件发送成功，traceId={}, 收件�?{}", traceId, email.getTo());
            return buildSendResult(traceId, email, message);
        } catch (IOException | freemarker.template.TemplateException e) {
            throw new MessagingException("Freemarker模板处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 填充邮件基本字段（发件人、收件人、主题、抄送、密送、回复地址、已读回执、优先级�?
     *
     * @param helper MIME消息辅助�?
     * @param email  邮件对象
     * @throws MessagingException 填充字段时可能抛出的异常
     */
    private void fillBasicFields(MimeMessageHelper helper, Email email) throws MessagingException {
        helper.setFrom(buildFromAddress(email));
        helper.setTo(email.getToArray());
        helper.setSubject(email.getSubject());

        String[] ccArray = email.getCcArray();
        if (ccArray != null && ccArray.length > 0) {
            helper.setCc(ccArray);
        }

        String[] bccArray = email.getBccArray();
        if (bccArray != null && bccArray.length > 0) {
            helper.setBcc(bccArray);
        }

        if (StringUtils.isNotBlank(email.getReplyTo())) {
            helper.setReplyTo(email.getReplyTo());
        }

        if (email.isReadReceipt()) {
            helper.getMimeMessage().setHeader("Disposition-Notification-To", "*");
        }

        Integer priority = email.getPriority();
        if (priority != null) {
            helper.setPriority(priority);
        }
    }

    /**
     * 构建发件人地址，支持自定义发件人名�?
     *
     * @param email 邮件对象
     * @return 发件人Internet地址
     * @throws MessagingException 构建地址时可能抛出的异常
     */
    private InternetAddress buildFromAddress(Email email) throws MessagingException {
        String fromName = email.getFromName();
        String fromAddress = null;
        if (mailSender instanceof JavaMailSenderImpl mailSenderImpl) {
            fromAddress = mailSenderImpl.getUsername();
        }
        if (StringUtils.isBlank(fromAddress)) {
            if (StringUtils.isNotBlank(email.getReplyTo())) {
                return InternetAddress.parse(email.getReplyTo())[0];
            }
            throw new IllegalStateException("邮件发送地址(from)未配置，请在 email 配置中设�?username");
        }
        try {
            if (StringUtils.isNotBlank(fromName)) {
                return new InternetAddress(fromAddress, fromName);
            }
            return new InternetAddress(fromAddress);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * 添加附件到邮�?
     *
     * @param helper MIME消息辅助�?
     * @param email  邮件对象
     * @throws MessagingException 添加附件时可能抛出的异常
     */
    private void addAttachments(MimeMessageHelper helper, Email email) throws MessagingException {
        List<EmailAttachment> attachments = email.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        for (EmailAttachment attachment : attachments) {
            if (!attachment.isValid()) {
                log.warn("[Email] 跳过无效附件");
                continue;
            }

            File file = attachment.getFile();
            if (!file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("邮件附件不存�? " + file.getPath());
            }

            FileSystemResource resource = new FileSystemResource(file);
            String displayName = encodeFileName(attachment.getDisplayName());
            helper.addAttachment(displayName, resource);
        }
    }

    private void addInlineResources(MimeMessageHelper helper, Email email) throws MessagingException {
        List<EmailInlineResource> resources = email.getInlineResources();
        if (resources == null || resources.isEmpty()) {
            return;
        }

        for (EmailInlineResource resource : resources) {
            if (!resource.isValid()) {
                log.warn("[Email] 跳过无效内嵌资源");
                continue;
            }

            File file = resource.getFile();
            if (!file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("邮件内嵌资源不存�? " + file.getPath());
            }

            FileSystemResource fsResource = new FileSystemResource(file);
            helper.addInline(resource.getResourceId(), fsResource);
        }
    }

    private String encodeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return fileName;
        }
        try {
            boolean hasNonAscii = false;
            for (int i = 0; i < fileName.length(); i++) {
                if (fileName.charAt(i) > 127) {
                    hasNonAscii = true;
                    break;
                }
            }
            if (hasNonAscii) {
                return jakarta.mail.internet.MimeUtility.encodeText(fileName, "UTF-8", "B");
            }
            return fileName;
        } catch (java.io.UnsupportedEncodingException e) {
            log.warn("[Email] 文件名编码转换失败，使用原始文件�? {}", fileName);
            return fileName;
        }
    }

    /**
     * 构建邮件发送结果对�?
     *
     * @param traceId 追踪ID
     * @param email   邮件对象
     * @param message MIME消息
     * @return 发送结�?
     * @throws MessagingException 获取消息ID时可能抛出的异常
     */
    private SendResult buildSendResult(String traceId, Email email, MimeMessage message) throws MessagingException {
        int attachmentsCount = email.getAttachments() != null ? email.getAttachments().size() : 0;
        return SendResult.builder()
                .success(true)
                .messageId(message.getMessageID())
                .sentAt(LocalDateTime.now())
                .recipients(email.getToArray())
                .ccRecipients(email.getCcArray())
                .bccRecipients(email.getBccArray())
                .subject(email.getSubject())
                .emailType(email.getEmailType().getCode())
                .traceId(traceId)
                .attachmentsCount(attachmentsCount)
                .build();
    }

    /**
     * 触发发送前回调
     *
     * @param email 邮件对象
     */
    private void triggerBeforeSend(Email email) {
        for (EmailSendListener listener : listeners) {
            try {
                listener.onBeforeSend(email);
            } catch (Exception e) {
                log.warn("[Email] 监听�?onBeforeSend 执行异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 触发发送成功回�?
     *
     * @param email  邮件对象
     * @param result 发送结�?
     */
    private void triggerSuccess(Email email, SendResult result) {
        for (EmailSendListener listener : listeners) {
            try {
                listener.onSuccess(email, result);
            } catch (Exception e) {
                log.warn("[Email] 监听�?onSuccess 执行异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 触发发送失败回�?
     *
     * @param email     邮件对象
     * @param exception 异常信息
     */
    private void triggerFailure(Email email, Throwable exception) {
        for (EmailSendListener listener : listeners) {
            try {
                listener.onFailure(email, exception);
            } catch (Exception e) {
                log.warn("[Email] 监听�?onFailure 执行异常: {}", e.getMessage());
            }
        }
    }
}
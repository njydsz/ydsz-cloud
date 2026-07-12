package com.njydsz.pmis.common.email.domain;

import com.njydsz.pmis.common.email.enums.EmailType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
/**
 * 邮件实体类
 *
 * <p>封装邮件发送所需的完整信息，包括收件人、抄送、密送、主题、内容、
 * 附件、内嵌资源以及模板渲染所需的变量等。</p>
 *
 * <p>支持两种构建方式：
 * <ul>
 *   <li>使用 {@link #builder()} 链式构建</li>
 *   <li>直接使用构造方法</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 构建简单文本邮件
 * Email email = Email.builder()
 *         .to("a@example.com,b@example.com")
 *         .subject("主题")
 *         .content("内容")
 *         .build();
 *
 * // 构建带附件的 HTML 邮件
 * Email email = Email.builder()
 *         .to("user@example.com")
 *         .cc("manager@example.com")
 *         .subject("报告")
 *         .content("<h1>月度报告</h1>")
 *         .emailType(EmailType.HTML)
 *         .attachments(List.of(
 *             EmailAttachment.builder()
 *                 .filePath("D:/report.xlsx")
 *                 .fileName("2024月度报告.xlsx")
 *                 .build()
 *         ))
 *         .build();
 *
 * // 构建 Thymeleaf 模板邮件
 * Email email = Email.builder()
 *         .to("user@example.com")
 *         .subject("模板邮件")
 *         .emailType(EmailType.THYMELEAF)
 *         .template("thymeleaf")
 *         .variables(Map.of("name", "张三", "amount", 1000))
 *         .build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Email {

    /** 收件人，多个以逗号分隔 */
    @NotBlank(message = "收件人不能为空")
    private String to;

    /** 抄送人，多个以逗号分隔 */
    private String cc;

    /** 密送人，多个以逗号分隔 */
    private String bcc;

    /** 邮件主题 */
    @NotBlank(message = "邮件主题不能为空")
    @Size(max = 200, message = "邮件主题长度不能超过200个字符")
    private String subject;

    /** 邮件正文内容 */
    private String content;

    /** 邮件类型 */
    @Builder.Default
    private EmailType emailType = EmailType.HTML;

    /** 附件列表 */
    private List<EmailAttachment> attachments;

    /** 内嵌资源列表 */
    private List<EmailInlineResource> inlineResources;

    /** 模板名称 */
    private String template;

    /** 模板变量 */
    private Map<String, Object> variables;

    /** 回复地址 */
    @jakarta.validation.constraints.Email(message = "回复地址格式不正确")
    private String replyTo;

    /** 发件人显示名称 */
    private String fromName;

    /** 优先级（1=最高，5=最低） */
    @Builder.Default
    private Integer priority = 3;

    /** 是否需要已读回执 */
    @Builder.Default
    private boolean readReceipt = false;

    /**
     * 获取收件人数组
     *
     * @return 收件人字符串数组
     */
    public String[] getToArray() {
        return parseRecipients(to);
    }

    public String[] getCcArray() {
        return parseRecipients(cc);
    }

    public String[] getBccArray() {
        return parseRecipients(bcc);
    }

    private String[] parseRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    public boolean isTemplateMail() {
        return emailType == EmailType.THYMELEAF || emailType == EmailType.FREEMARKER;
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    /**
     * 判断是否有内嵌资源
     *
     * @return 有内嵌资源返回 true，否则返回 false
     */
    public boolean hasInlineResources() {
        return inlineResources != null && !inlineResources.isEmpty();
    }
}
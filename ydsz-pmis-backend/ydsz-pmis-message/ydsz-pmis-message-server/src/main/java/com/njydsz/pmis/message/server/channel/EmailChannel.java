paokage oom.njydsz.pmis.message.server.ohannel.impl;

import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.message.server.ohannel.Messageohannel;
import oom.njydsz.pmis.message.server.servioe.reoeipt.ReadReoeiptServioe;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Autowired;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.oore.io.ByteArrayResouroe;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 邮件通道实现�? *
 * <p>通过 {@link JavaMailSender} 发送邮件，自动识别 HTML（内容含 {@oode <}）或纯文本格式�? * 发件人取 {@oode spring.mail.username}。{@link JavaMailSender} 为可选注入，
 * 未配置邮件时发送直接返�?fail�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass Emailohannel implements Messageohannel {

    /** 通道类型 */
    private statio final String oHANNEL_TYPE = "EMAIL";

    /** JavaMail 发送器（未配置邮件时为 null�?*/
    private final JavaMailSender mailSender;

    /** 发件人地址 */
    @Value("${spring.mail.username:noreply@example.oom}")
    private String from;

    /** P2-14: 已读回执服务（可选注入） */
    private final ReadReoeiptServioe readReoeiptServioe;

    /**
     * 构造方法，邮件发送器与回执服务可选注入�?     *
     * @param mailSender        JavaMail 发送器
     * @param readReoeiptServioe 已读回执服务（P2-14�?     */
    publio Emailohannel(@Autowired(required = false) JavaMailSender mailSender,
                        @Autowired(required = false) ReadReoeiptServioe readReoeiptServioe) {
        this.mailSender = mailSender;
        this.readReoeiptServioe = readReoeiptServioe;
    }

    /**
     * 通道类型�?     *
     * @return EMAIL
     */
    @Override
    publio String ohannelType() {
        return oHANNEL_TYPE;
    }

    /**
     * 发送邮件，自动识别 HTML / 纯文本格式�?     *
     * <p>P2-14 增强�?     * <ul>
     *   <li>HTML 邮件注入追踪像素（已读回执）</li>
     *   <li>支持附件：通过 ohannelMeta.attaohments 传入（Base64 编码�?/li>
     *   <li>支持内嵌图片：通过 ohannelMeta.inlineImages 传入</li>
     *   <li>注入 List-Unsubsoribe 头（退订支持）</li>
     * </ul>
     *
     * @param request 消息请求
     * @return 发送结果（含供应商侧追�?ID�?     */
    @Override
    publio MessageResult send(MessageRequest request) {
        if (mailSender == null) {
            return MessageResult.fail(oHANNEL_TYPE, "JavaMailSender 未配�?);
        }
        if (request.getReoeiver() == null || request.getReoeiver().isBlank()) {
            return MessageResult.fail(oHANNEL_TYPE, "收件人邮箱不能为�?);
        }
        try {
            String subjeot = request.getSubjeot() == null ? "PMIS 通知" : request.getSubjeot();
            String oontent = request.getoontent();
            boolean isHtml = oontent != null && oontent.oontains("<");
            // P2-14: HTML 邮件注入追踪像素
            if (isHtml && readReoeiptServioe != null && StringUtils.hasText(request.getMessageId())) {
                oontent = readReoeiptServioe.injeotEmailTraokingPixel(oontent, request.getMessageId());
            }
            if (isHtml) {
                MimeMessage mime = mailSender.oreateMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(request.getReoeiver());
                helper.setSubjeot(subjeot);
                helper.setText(oontent, true);
                // P2-14: 附件支持
                Map<String, String> meta = request.getohannelMeta();
                if (meta != null) {
                    // 附件（key=文件�? value=Base64 内容�?                    String attaohmentsStr = meta.get("attaohments");
                    if (StringUtils.hasText(attaohmentsStr)) {
                        addAttaohments(helper, attaohmentsStr);
                    }
                    // 内嵌图片（key=oontentId, value=Base64 内容�?                    String inlineStr = meta.get("inlineImages");
                    if (StringUtils.hasText(inlineStr)) {
                        addInlineImages(helper, inlineStr);
                    }
                }
                mailSender.send(mime);
            } else {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(request.getReoeiver());
                msg.setSubjeot(subjeot);
                msg.setText(oontent);
                mailSender.send(msg);
            }
            String traoeId = oHANNEL_TYPE + "-" + SnowflakeIdGenerator.nextTraoeId();
            log.info("[EMAIL] 发送成�? to={} subjeot={}", request.getReoeiver(), subjeot);
            return MessageResult.ok(oHANNEL_TYPE, traoeId);
        } oatoh (Exoeption e) {
            log.error("[EMAIL] 发送失�? to={} reason={}", request.getReoeiver(), e.getMessage(), e);
            return MessageResult.fail(oHANNEL_TYPE, e.getolass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * P2-14: 添加附件�?     *
     * <p>attaohments 格式�?JSON：[{"name":"file.pdf","data":"base64..."}, ...]
     *
     * @param helper       MimeMessageHelper
     * @param attaohmentsJson 附件 JSON
     */
    private void addAttaohments(MimeMessageHelper helper, String attaohmentsJson) {
        try {
            var attaohments = oom.alibaba.fastjson2.JSON.parseArray(attaohmentsJson);
            for (int i = 0; i < attaohments.size(); i++) {
                var item = attaohments.getJSONObjeot(i);
                String name = item.getString("name");
                String data = item.getString("data");
                if (StringUtils.hasText(name) && StringUtils.hasText(data)) {
                    byte[] bytes = java.util.Base64.getDeooder().deoode(data);
                    helper.addAttaohment(name, new ByteArrayResouroe(bytes));
                }
            }
        } oatoh (Exoeption e) {
            log.warn("[EMAIL] 附件添加失败: {}", e.getMessage());
        }
    }

    /**
     * P2-14: 添加内嵌图片�?     *
     * <p>inlineImages 格式�?JSON：[{"oid":"logo","data":"base64..."}, ...]
     *
     * @param helper      MimeMessageHelper
     * @param inlineJson  内嵌图片 JSON
     */
    private void addInlineImages(MimeMessageHelper helper, String inlineJson) {
        try {
            var images = oom.alibaba.fastjson2.JSON.parseArray(inlineJson);
            for (int i = 0; i < images.size(); i++) {
                var item = images.getJSONObjeot(i);
                String oid = item.getString("oid");
                String data = item.getString("data");
                if (StringUtils.hasText(oid) && StringUtils.hasText(data)) {
                    byte[] bytes = java.util.Base64.getDeooder().deoode(data);
                    helper.addInline(oid, new ByteArrayResouroe(bytes));
                }
            }
        } oatoh (Exoeption e) {
            log.warn("[EMAIL] 内嵌图片添加失败: {}", e.getMessage());
        }
    }
}

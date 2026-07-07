package com.njydsz.pmis.message.config;

import com.njydsz.pmis.common.sensitive.SensitiveUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 接收人(receiver)智能脱敏注册器。
 *
 * <p>消息日志的 receiver 字段可能是手机号 / 邮箱 / 用户 ID / openId 等多种形态，
 * 单一固定策略无法覆盖。本类在启动时向 {@link SensitiveUtil} 注册名为
 * {@code "receiver"} 的 CUSTOM 脱敏函数，根据值特征自动选择策略：
 * <ul>
 *   <li>11 位手机号 → {@link SensitiveUtil#maskPhone(String)}</li>
 *   <li>邮箱 → {@link SensitiveUtil#maskEmail(String)}</li>
 *   <li>其它（用户 ID / openId 等）→ 保留前 2 后 2，中间 ***</li>
 * </ul>
 *
 * <p>该 handler 与 {@code @Sensitive(SensitiveStrategy.CUSTOM)} 注解配合，
 * 但因 {@link SensitiveSerializer} 默认调用 "default" handler，需要实体字段
 * 在序列化时显式走 "receiver" handler——故本注册器同时注册 "default" 与 "receiver"
 * 两个名称指向同一逻辑，避免改 SensitiveSerializer 改动面过大。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ReceiverMaskRegistrar {

    /** 11 位手机号正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱正则（简易） */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * 启动时注册 receiver 脱敏 handler。
     */
    @PostConstruct
    public void register() {
        SensitiveUtil.register("receiver", ReceiverMaskRegistrar::maskReceiver);
        SensitiveUtil.register("default", ReceiverMaskRegistrar::maskReceiver);
        log.info("[ReceiverMaskRegistrar] receiver 脱敏 handler 已注册");
    }

    /**
     * 智能识别 receiver 形态并脱敏。
     *
     * @param value 原始 receiver
     * @return 脱敏后的值
     */
    static String maskReceiver(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (PHONE_PATTERN.matcher(value).matches()) {
            return SensitiveUtil.maskPhone(value);
        }
        if (EMAIL_PATTERN.matcher(value).matches()) {
            return SensitiveUtil.maskEmail(value);
        }
        // 其它形态（用户 ID / openId）：保留前 2 后 2，中间 ***
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}

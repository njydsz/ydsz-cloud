package com.njydsz.message.server.service.core;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息接收人校验器。
 * <p>校验收件人地址合法性、租户白名单等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Component
public class ReceiverValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * 校验 receiver 是否符合指定通道格式。
     *
     * @param channel 通道类型（大写）
     * @param receiver 接收人标识
     * @return null 表示通过；非 null 表示错误描述
     */
    public String validate(String channel, String receiver) {
        if (!StringUtils.hasText(receiver)) {
            return "接收人不能为空";
        }
        if (channel == null) {
            return null;
        }
        return switch (channel.toUpperCase()) {
            case "SMS" -> PHONE_PATTERN.matcher(receiver.trim()).matches()
                    ? null : "手机号格式不正确: " + receiver;
            case "EMAIL" -> EMAIL_PATTERN.matcher(receiver.trim()).matches()
                    ? null : "邮箱格式不正确: " + receiver;
            case "INAPP", "DINGTALK", "DINGTALK_WORK", "WECOM", "WECOM_APP", "FEISHU", "WEBHOOK" ->
                    receiver.trim().length() >= 1 ? null : "接收人标识不能为空";
            case "PUSH" -> receiver.trim().length() >= 10
                    ? null : "推送设备 token 长度不足: " + receiver.length();
            default -> null;
        };
    }
}

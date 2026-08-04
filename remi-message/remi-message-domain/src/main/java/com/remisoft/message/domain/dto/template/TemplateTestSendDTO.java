package com.remisoft.message.domain.dto.template;


import java.util.Map;

import lombok.Data;

/**
 * 模板试发请求 DTO。
 *
 * <p>P1-6: 使用指定模板向测试接收人发送一条真实消息，验证模板渲染效果和通道连通性。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class TemplateTestSendDTO {

    /** 模板编码 */
    private String templateCode;

    /** 语言区域 */
    private String locale;

    /** 渲染参数 */
    private Map<String, Object> params;

    /** 测试接收人（手机号 / 邮箱 / userId） */
    private String testReceiver;

    /** 测试通道（为空时使用模板绑定的通道） */
    private String testChannel;
}

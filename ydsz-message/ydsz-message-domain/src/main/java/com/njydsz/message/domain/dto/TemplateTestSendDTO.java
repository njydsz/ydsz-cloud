package com.njydsz.message.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模板试发请求 DTO。
 *
 * <p>用于模板编辑后的真实发送验证，向测试接收人发送一条真实消息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class TemplateTestSendDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 模板编码 */
  @NotBlank(message = "模板编码不能为空")
  private String templateCode;

  /** 测试接收人（用户 ID 或手机号/邮箱等） */
  @NotBlank(message = "测试接收人不能为空")
  private String testReceiver;

  /** 模板变量参数 */
  private Map<String, Object> params;

  /** 测试通道（可选，未指定时使用模板默认通道） */
  private String testChannel;
}

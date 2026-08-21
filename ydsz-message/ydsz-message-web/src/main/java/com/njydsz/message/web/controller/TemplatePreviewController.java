package com.njydsz.message.web.controller.template;

import java.util.HashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.infra.entity.MsgTemplate;
import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.server.service.template.TemplateService;
import com.njydsz.message.server.template.TemplateEngine;
import com.njydsz.message.server.template.TemplateVariableValidator;

/**
 * 模板预览（Template Preview）Controller。
 *
 * <p>提供<b>消息模板实时渲染预览</b>的 HTTP API，是模板编辑器的核心支撑。 在模板编辑时，前端实时调用本 Controller 给定参数预览渲染结果，
 * 避免每次都保存到数据库再触发发送测试。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/template/preview/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>按模板编码预览</b>：{@code POST /by-code} — 给定 templateCode/channel/locale/params 预览已发布模板的渲染结果
 *   <li><b>预览原始内容</b>：{@code POST /raw} — 给定任意模板字符串和参数，直接渲染（不依赖数据库中的模板）
 * </ul>
 *
 * <p><b>变量校验：</b>{@code /by-code} 流程中：
 *
 * <ol>
 *   <li>从 {@code ydsz_msg_template} 加载模板（含 subject / content / variableDefs）
 *   <li>按 {@code variableDefs}（JSON 格式：{@code {"name": "code", "type": "string", "required": true,
 *       "defaultValue": "000000"}}）解析变量定义
 *   <li>调用 {@link com.njydsz.message.server.template.TemplateVariableValidator#validateAndFill}
 *       校验必填项 + 填充默认值
 *   <li>用 {@link com.njydsz.message.server.template.TemplateEngine#render} 替换 {@code ${var}} 占位符
 *   <li>返回渲染后的 content / subject
 * </ol>
 *
 * <p><b>多租户隔离：</b>按 {@link com.njydsz.common.security.TenantContext#getTenantId()} 当前租户加载模板，
 * 跨租户模板不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>{@code /raw} 接口（自定义内容）启用 {@link Idempotent} 5s 防重，防止被刷渲染资源
 *   <li>{@code /raw} 接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>变量替换在沙箱内执行，避免模板注入风险（{@code TemplateEngine} 使用纯字符串替换，不求值 Groovy / OGNL 等表达式）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.template.TemplateEngine 模板渲染引擎
 * @see com.njydsz.message.server.template.TemplateVariableValidator 变量校验器
 * @see com.njydsz.message.server.service.template.TemplateService 模板服务
 */
@Tag(name = "模板预览", description = "模板渲染预览")
@Slf4j
@RequestMapping("/api/v1/message/template/preview")
@RequiredArgsConstructor
public class TemplatePreviewController {

  private final TemplateService templateService;
  private final TemplateEngine templateEngine;
  private final TemplateVariableValidator variableValidator;

  /**
   * 按模板编码预览已发布模板的渲染结果。
   *
   * <p>从 DB 加载指定 {@code templateCode/channel/locale}（默认值 INAPP / zh-CN）的模板， 按 {@code variableDefs}
   * 校验必填变量并填充默认值，再用 {@link TemplateEngine} 渲染 subject / content。模板不存在或编码为空时返回 error，不抛异常。
   *
   * @param req 预览请求（含 templateCode 与渲染参数 params）
   * @return 渲染后的 content / subject 映射；失败返回错误响应
   */
  @Operation(summary = "按模板编码预览渲染结果")
  @PostMapping("/by-code")
  public YdszResponse<Map<String, String>> previewByCode(@RequestBody PreviewRequest req) {
    if (req == null || !StringUtils.hasText(req.getTemplateCode())) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "模板编码不能为空");
    }
    MsgTemplate template =
        templateService.loadByCodeAndChannel(
            req.getTemplateCode(),
            StringUtils.hasText(req.getChannel()) ? req.getChannel() : "INAPP",
            StringUtils.hasText(req.getLocale()) ? req.getLocale() : "zh-CN",
            TenantContextHolder.getTenantId());
    if (template == null) {
      return YdszResponse.error(
          MessageExceptionCode.TEMPLATE_NOT_FOUND, "模板不存在: " + req.getTemplateCode());
    }

    Map<String, Object> params =
        req.getParams() == null ? new HashMap<>() : new HashMap<>(req.getParams());

    // P0-3: 变量校验+填充默认值
    if (StringUtils.hasText(template.getVariableDefs())) {
      var varDefs = variableValidator.parse(template.getVariableDefs());
      if (!varDefs.isEmpty()) {
        variableValidator.validateAndFill(params, varDefs, req.getTemplateCode());
      }
    }

    Map<String, String> result = new HashMap<>();
    result.put("content", templateEngine.render(template.getContent(), params));
    result.put(
        "subject",
        templateEngine.render(template.getSubject() == null ? "" : template.getSubject(), params));
    return YdszResponse.success(result);
  }

  /**
   * 预览自定义模板字符串的渲染结果（不依赖数据库模板）。
   *
   * <p>直接对调用方传入的任意模板字符串与参数做 {@link TemplateEngine} 纯字符串替换， 不校验变量定义、不查库。模板内容为空时返回 error。 因接受外部内容，启用
   * 5s 幂等防重与 50 QPS 限流，且渲染在沙箱内进行（不求值 Groovy/OGNL 等表达式），避免模板注入风险。
   *
   * @param req 原始预览请求（含 template 字符串与渲染参数 params）
   * @return 渲染后的字符串；失败返回错误响应
   */
  @Operation(summary = "预览自定义模板内容")
  @RateLimit(resource = "message.templatepreview.previewRaw", threshold = 50)
  @Idempotent(key = "ydsz:message:TemplatePreviewController:previewRaw:lock", ttlSeconds = 5)
  @PostMapping("/raw")
  public YdszResponse<String> previewRaw(@RequestBody RawPreviewRequest req) {
    if (req == null || !StringUtils.hasText(req.getTemplate())) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "模板内容不能为空");
    }
    Map<String, Object> params = req.getParams() == null ? new HashMap<>() : req.getParams();
    String rendered = templateEngine.render(req.getTemplate(), params);
    return YdszResponse.success(rendered);
  }

  /** 模板预览请求体（按模板编码渲染）。 */
  @lombok.Data
  public static class PreviewRequest {
    /** 模板编码（对应已注册的模板） */
    private String templateCode;

    /** 消息渠道（sms / mail / dingtalk 等） */
    private String channel;

    /** 语言区域（如 zh-CN），影响模板国际化 */
    private String locale;

    /** 模板变量参数 */
    private Map<String, Object> params;
  }

  /** 原始模板预览请求体（直接提交模板内容渲染）。 */
  @lombok.Data
  public static class RawPreviewRequest {
    /** 模板原始内容（含占位符如 ${name}） */
    private String template;

    /** 模板变量参数 */
    private Map<String, Object> params;
  }
}

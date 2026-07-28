package com.njydsz.message.web.controller.template;

import java.util.HashMap;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.Map;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.security.TenantContext;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.server.service.template.TemplateService;
import com.njydsz.message.server.template.TemplateEngine;
import com.njydsz.message.server.template.TemplateVariableValidator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 模板预览（Template Preview）Controller。
 *
 * <p>提供<b>消息模板实时渲染预览</b>的 HTTP API，是模板编辑器的核心支撑。
 * 在模板编辑时，前端实时调用本 Controller 给定参数预览渲染结果，
 * 避免每次都保存到数据库再触发发送测试。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/template/preview/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>按模板编码预览</b>：{@code POST /by-code} — 给定 templateCode/channel/locale/params 预览已发布模板的渲染结果</li>
 *   <li><b>预览原始内容</b>：{@code POST /raw} — 给定任意模板字符串和参数，直接渲染（不依赖数据库中的模板）</li>
 * </ul>
 *
 * <p><b>变量校验：</b>{@code /by-code} 流程中：
 * <ol>
 *   <li>从 {@code ydsz_msg_template} 加载模板（含 subject / content / variableDefs）</li>
 *   <li>按 {@code variableDefs}（JSON 格式：{@code {"name": "code", "type": "string", "required": true, "defaultValue": "000000"}}）解析变量定义</li>
 *   <li>调用 {@link com.njydsz.message.server.template.TemplateVariableValidator#validateAndFill} 校验必填项 + 填充默认值</li>
 *   <li>用 {@link com.njydsz.message.server.template.TemplateEngine#render} 替换 {@code ${var}} 占位符</li>
 *   <li>返回渲染后的 content / subject</li>
 * </ol>
 *
 * <p><b>多租户隔离：</b>按 {@link com.njydsz.common.security.TenantContext#getTenantId()} 当前租户加载模板，
 * 跨租户模板不可见。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>{@code /raw} 接口（自定义内容）启用 {@link Idempotent} 5s 防重，防止被刷渲染资源</li>
 *   <li>{@code /raw} 接口启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>变量替换在沙箱内执行，避免模板注入风险（{@code TemplateEngine} 使用纯字符串替换，不求值 Groovy / OGNL 等表达式）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.template.TemplateEngine 模板渲染引擎
 * @see com.njydsz.message.server.template.TemplateVariableValidator 变量校验器
 * @see com.njydsz.message.server.service.template.TemplateService 模板服务
 */
@Tag(name = "模板预览", description = "模板渲染预览")
@RestController
@RequestMapping("/api/v1/message/template/preview")
@RequiredArgsConstructor
public class TemplatePreviewController {

    private final TemplateService templateService;
    private final TemplateEngine templateEngine;
    private final TemplateVariableValidator variableValidator;

    @Operation(summary = "按模板编码预览渲染结果")
    @PostMapping("/by-code")
    public BaseResponse<Map<String, String>> previewByCode(@RequestBody PreviewRequest req) {
        if (req == null || !StringUtils.hasText(req.getTemplateCode())) {
            return BaseResponse.error("模板编码不能为空");
        }
        MsgTemplate template = templateService.loadByCodeAndChannel(
                req.getTemplateCode(),
                StringUtils.hasText(req.getChannel()) ? req.getChannel() : "INAPP",
                StringUtils.hasText(req.getLocale()) ? req.getLocale() : "zh-CN",
                TenantContext.getTenantId());
        if (template == null) {
            return BaseResponse.error("模板不存在: " + req.getTemplateCode());
        }

        Map<String, Object> params = req.getParams() == null ? new HashMap<>() : new HashMap<>(req.getParams());

        // P0-3: 变量校验+填充默认值
        if (StringUtils.hasText(template.getVariableDefs())) {
            var varDefs = variableValidator.parse(template.getVariableDefs());
            if (!varDefs.isEmpty()) {
                variableValidator.validateAndFill(params, varDefs, req.getTemplateCode());
            }
        }

        Map<String, String> result = new HashMap<>();
        result.put("content", templateEngine.render(template.getContent(), params));
        result.put("subject", templateEngine.render(
                template.getSubject() == null ? "" : template.getSubject(), params));
        return BaseResponse.success(result);
    }

    @Operation(summary = "预览自定义模板内容")
    @RateLimit(resource = "message.templatepreview.previewRaw", threshold = 50)
    @Idempotent(key = "ydsz:message:TemplatePreviewController:previewRaw:lock", ttlSeconds = 5)
    @PostMapping("/raw")
    public BaseResponse<String> previewRaw(@RequestBody RawPreviewRequest req) {
        if (req == null || !StringUtils.hasText(req.getTemplate())) {
            return BaseResponse.error("模板内容不能为空");
        }
        Map<String, Object> params = req.getParams() == null ? new HashMap<>() : req.getParams();
        String rendered = templateEngine.render(req.getTemplate(), params);
        return BaseResponse.success(rendered);
    }

    @lombok.Data
    public static class PreviewRequest {
        private String templateCode;
        private String channel;
        private String locale;
        private Map<String, Object> params;
    }

    @lombok.Data
    public static class RawPreviewRequest {
        private String template;
        private Map<String, Object> params;
    }
}

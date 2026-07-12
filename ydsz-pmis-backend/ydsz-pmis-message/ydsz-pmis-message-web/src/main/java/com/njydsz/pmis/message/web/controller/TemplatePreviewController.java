package com.njydsz.pmis.message.web.controller.template;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import com.njydsz.pmis.message.server.service.template.TemplateService;
import com.njydsz.pmis.message.server.template.TemplateEngine;
import com.njydsz.pmis.message.server.template.TemplateVariableValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 模板预览 Controller（P1-4）。
 *
 * <p>提供模板渲染预览接口,前端编辑模板时可实时预览渲染效果。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Tag(name = "模板预览", description = "模板渲染预览")
@RestController
@RequestMapping("/template/preview")
@RequiredArgsConstructor
public class TemplatePreviewController {

    private final TemplateService templateService;
    private final TemplateEngine templateEngine;
    private final TemplateVariableValidator variableValidator;

    @Operation(summary = "按模板编码预览渲染结果")
    @PostMapping("/by-code")
    public BaseResponse<Map<String, String>> previewByCode(@RequestBody PreviewRequest req) {
        if (req == null || !StringUtils.hasText(req.getTemplateCode())) {
            return BaseResponse.fail("模板编码不能为空");
        }
        MsgTemplateDO template = templateService.loadByCodeAndChannel(
                req.getTemplateCode(),
                StringUtils.hasText(req.getChannel()) ? req.getChannel() : "INAPP",
                StringUtils.hasText(req.getLocale()) ? req.getLocale() : "zh-CN",
                TenantContext.getTenantId());
        if (template == null) {
            return BaseResponse.fail("模板不存在: " + req.getTemplateCode());
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
        BaseResponse.put("content", templateEngine.render(template.getContent(), params));
        BaseResponse.put("subject", templateEngine.render(
                template.getSubject() == null ? "" : template.getSubject(), params));
        return BaseResponse.ok(result);
    }

    @Operation(summary = "预览自定义模板内容")
    @PostMapping("/raw")
    public BaseResponse<String> previewRaw(@RequestBody RawPreviewRequest req) {
        if (req == null || !StringUtils.hasText(req.getTemplate())) {
            return BaseResponse.fail("模板内容不能为空");
        }
        Map<String, Object> params = req.getParams() == null ? new HashMap<>() : req.getParams();
        String rendered = templateEngine.render(req.getTemplate(), params);
        return BaseResponse.ok(rendered);
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

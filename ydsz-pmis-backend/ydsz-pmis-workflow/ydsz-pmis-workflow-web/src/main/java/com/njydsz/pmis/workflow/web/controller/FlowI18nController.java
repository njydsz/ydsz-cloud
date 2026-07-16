package com.njydsz.pmis.workflow.web.controller.definition;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.workflow.server.service.FlowI18nService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-3: 工作流国际化(i18n) Controller
 *
 * <p>提供工作流枚举值的多语言描述查询接口。
 * 前端按 locale 请求对应语言的描述文本。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-i18n", description = "工作流国际化接口")
@RequestMapping("/workflow/i18n")
@RequiredArgsConstructor
public class FlowI18nController {

    private final FlowI18nService i18nService;

    /**
     * 获取指定枚举类的全部描述。
     *
     * @param enumType 枚举类型（FlowTaskStatus/FlowInstanceStatus/FlowNodeType 等）
     * @param locale   语言（zh_CN/en_US），为空默认 zh_CN
     * @return 枚举描述列表
     */
    @GetMapping("/enum/{enumType}")
    @Operation(summary = "获取枚举类型的全部描述")
    public BaseResponse<List<Map<String, String>>> enumDescriptions(
            @PathVariable String enumType,
            @RequestParam(required = false) String locale) {
        return BaseResponse.ok(i18nService.getEnumDescriptions(enumType, locale));
    }

    /**
     * 获取单个枚举值的描述。
     *
     * @param enumType 枚举类型
     * @param enumName 枚举值名称
     * @param locale   语言
     * @return 描述文本
     */
    @GetMapping("/enum/{enumType}/{enumName}")
    @Operation(summary = "获取单个枚举值的描述")
    public BaseResponse<String> enumDescription(
            @PathVariable String enumType,
            @PathVariable String enumName,
            @RequestParam(required = false) String locale) {
        return BaseResponse.ok(i18nService.getEnumDescription(enumType, enumName, locale));
    }

    /**
     * 获取所有支持的语言列表。
     *
     * @return 语言列表
     */
    @GetMapping("/locales")
    @Operation(summary = "获取支持的语言列表")
    public BaseResponse<List<Map<String, String>>> supportedLocales() {
        return BaseResponse.ok(i18nService.getSupportedLocales());
    }
}

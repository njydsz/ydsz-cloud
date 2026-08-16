package com.njydsz.workflow.web.controller.definition;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.workflow.server.service.FlowI18nService;

/**
 * 工作流国际化 (i18n) Controller（P2-3）
 *
 * <p>提供工作流<b>枚举值的多语言描述</b>查询接口。前端按 {@code locale}（语言）请求
 * 对应语言的描述文本（展示给最终用户），与代码侧的英文常量解耦。
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>流程列表 / 实例详情页：状态列展示「运行中 / 已完成 / 已驳回」等多语言描述</li>
 *   <li>审批面板：节点类型列展示「人工审批 / 抄送 / 邮件通知」等多语言描述</li>
 *   <li>监控看板：异常类型列展示「超期 / 卡单 / 重复驳回」等多语言描述</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/i18n/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>{@code GET /enum/{enumType}} — 获取指定枚举类的全部描述</li>
 *   <li>{@code GET /enum/{enumType}/{enumName}} — 获取单个枚举值的描述</li>
 *   <li>{@code GET /locales} — 获取所有支持的语言列表</li>
 * </ul>
 *
 * <p><b>支持的语言：</b>zh_CN（简体中文，默认）、en_US（美式英语）。
 * 其它语言暂未内置，需业务方在 {@code ydsz_workflow_i18n} 表中自行扩展。
 *
 * <p><b>性能优化：</b>所有描述走 Redis 缓存（{@code ydsz:workflow:i18n:{locale}:{enumType}}），
 * TTL 24h；缓存未命中回源 DB 后回填。语言列表变更时由 Service 层主动失效缓存。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传与语言兜底；
 * 翻译资源加载、缓存管理、fallback 策略下沉到 {@link FlowI18nService}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowI18nService 国际化服务
 */
@Slf4j
@RestController
@Tag(name = "workflow-i18n", description = "工作流国际化接口")
@RequestMapping("/api/v1/workflow/i18n")
@RequiredArgsConstructor
public class FlowI18nController {

    /** 国际化服务，负责多语言枚举描述的查询、缓存与 fallback */
    private final FlowI18nService i18nService;

    /**
     * 获取指定枚举类的全部描述
     *
     * <p>返回的 Map 列表中每条形如 {@code {name: "RUNNING", label: "运行中"}}，
     * 前端可直接用于下拉框 / 单选 / 状态筛选组件渲染。
     * <p>当前支持的 {@code enumType}：
     * <ul>
     *   <li>{@code FlowTaskStatus} — 任务状态（PENDING/CLAIMED/COMPLETED/REJECTED/...）</li>
     *   <li>{@code FlowInstanceStatus} — 实例状态（RUNNING/COMPLETED/REJECTED/...）</li>
     *   <li>{@code FlowNodeType} — 节点类型（USER_TASK/SERVICE_TASK/GATEWAY/...）</li>
     *   <li>{@code FlowDelegateScope} — 委派范围（ALL/FLOW/NODE/ROLE）</li>
     *   <li>{@code FlowCanaryStrategy} — 灰度策略（USER_HASH/RANDOM/WHITELIST）</li>
     * </ul>
     *
     * @param enumType 枚举类型（FlowTaskStatus / FlowInstanceStatus / FlowNodeType 等）
     * @param locale   语言（zh_CN/en_US），为空默认 zh_CN
     * @return 枚举描述列表（含 name + label 字段）
     */
    @GetMapping("/enum/{enumType}")
    @Operation(summary = "获取枚举类型的全部描述")
    public BaseResponse<List<Map<String, String>>> enumDescriptions(
            @PathVariable String enumType,
            @RequestParam(required = false) String locale) {
        return BaseResponse.success(i18nService.getEnumDescriptions(enumType, locale));
    }

    /**
     * 获取单个枚举值的描述
     *
     * <p>适用于只需要展示某一个枚举值描述的场景（如详情页某字段的状态文案）。
     * <p>查无翻译时按 fallback 链 {@code locale → zh_CN → name 原值} 返回。
     *
     * @param enumType 枚举类型
     * @param enumName 枚举值名称（如 {@code RUNNING}）
     * @param locale   语言
     * @return 描述文本（无翻译时回退到原值）
     */
    @GetMapping("/enum/{enumType}/{enumName}")
    @Operation(summary = "获取单个枚举值的描述")
    public BaseResponse<String> enumDescription(
            @PathVariable String enumType,
            @PathVariable String enumName,
            @RequestParam(required = false) String locale) {
        return BaseResponse.success(i18nService.getEnumDescription(enumType, enumName, locale));
    }

    /**
     * 获取所有支持的语言列表
     *
     * <p>前端首次加载时调用，构建语言切换下拉框；返回形如
     * {@code [{code: "zh_CN", name: "简体中文"}, {code: "en_US", name: "English"}]}。
     * <p>语言列表来源：{@code ydsz_workflow_i18n} 表中 {@code locale} 字段去重。
     *
     * @return 语言列表（含 code 与 name 字段）
     */
    @GetMapping("/locales")
    @Operation(summary = "获取支持的语言列表")
    public BaseResponse<List<Map<String, String>>> supportedLocales() {
        return BaseResponse.success(i18nService.getSupportedLocales());
    }
}

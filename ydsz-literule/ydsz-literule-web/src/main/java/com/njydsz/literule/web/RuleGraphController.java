package com.njydsz.literule.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.expr.ExpressionFunctionDef;
import com.njydsz.literule.domain.vo.ExpressionFunctionDefVO;
import com.njydsz.literule.domain.vo.ExpressionPreviewResultVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.StringVO;
import com.njydsz.literule.server.expr.ExpressionValidationService;
import com.njydsz.literule.server.orchestrator.RuleChainGraph;
import com.njydsz.literule.server.orchestrator.RuleGraphValidator;
import com.njydsz.literule.server.spi.GraphExecutionProvider;
import com.njydsz.literule.server.spi.RuleChainGraphProvider;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.literule.domain.converter.LiteruleConverter;

/**
 * 规则链画布 Controller
 *
 * <p>业务背景：规则链画布是规则编排的可视化载体，运营人员通过拖拽节点和连线
 * 组织规则执行顺序与依赖关系。画布保存为 JSON 结构（nodes/edges/viewport/metadata），
 * 引擎执行时按画布拓扑转换为可执行规则链。
 *
 * <p>核心能力：
 * <ul>
 *   <li>画布 CRUD（含结构校验、版本递增）</li>
 *   <li>画布 Dry-run 仿真（按画布执行评估）</li>
 *   <li>失效引用检测（引用了已删除/禁用的规则）</li>
 *   <li>表达式求值预览（编辑器实时预览）</li>
 *   <li>表达式函数市场（CodeMirror 自动补全）</li>
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径
 * {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则链画布", description = "规则链画布编辑、校验、Dry-run 与表达式函数市场")
public class RuleGraphController {

    /** 规则链图服务（SPI，由 project 模块提供实现） */
    private final RuleChainGraphProvider ruleChainGraphProvider;
    /** 图执行服务（SPI，由 project 模块提供实现） */
    private final GraphExecutionProvider graphExecutionProvider;
    /** 表达式校验服务 */
    private final ExpressionValidationService expressionValidationService;

    /**
     * 查询规则的画布
     *
     * <p>P0-1：返回 RuleChainGraph JSON 内容（含 nodes/edges/viewport/metadata）。
     * 不存在时返回 200 + null，前端按空画布初始化。
     *
     * @param ruleCode 规则编码
     * @return 画布对象
     */
    @GetMapping("/{ruleCode}/graph")
    public BaseResponse<RuleChainGraphVO> getChainGraph(@PathVariable String ruleCode) {
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(ruleChainGraphProvider.getByRuleCode(ruleCode)));
    }

    /**
     * 保存或更新画布
     *
     * <p>保存前先用 {@link RuleGraphValidator} 校验画布结构，存在 ERROR 级问题则拒绝保存。
     * 校验通过后自动递增画布版本号。
     *
     * @param ruleCode 规则编码
     * @param graph    画布
     * @param operator 操作人
     * @return 保存后的画布
     */
    @Idempotent(key = "ruleAdmin:saveChainGraph", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @PostMapping("/{ruleCode}/graph")
    public BaseResponse<Map<String, Object>> saveChainGraph(@PathVariable String ruleCode,
                                                       @Valid @RequestBody RuleChainGraph graph,
                                                       @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        // 1. 结构校验
        List<RuleGraphValidator.GraphValidationIssue> issues = RuleGraphValidator.validate(graph);
        if (!RuleGraphValidator.isValid(issues)) {
            return BaseResponse.success(Map.of(
                    "valid", false,
                    "issues", issues,
                    "message", "画布结构不合法，请先修复错误"
            ));
        }
        // 2. 保存
        RuleChainGraph saved = ruleChainGraphProvider.save(ruleCode, graph, operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("issues", issues);
        result.put("graph", saved);
        return BaseResponse.success(result);
    }

    /**
     * 删除画布
     */
    @Idempotent(key = "ruleAdmin:deleteChainGraph", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteChainGraph'")
    @DeleteMapping("/{ruleCode}/graph")
    public BaseResponse<Void> deleteChainGraph(@PathVariable String ruleCode) {
        ruleChainGraphProvider.delete(ruleCode);
        return BaseResponse.success();
    }

    /**
     * 校验画布结构（不保存）
     *
     * <p>供前端"实时校验"按钮调用，返回 ERROR/WARN 两级问题。
     */
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'validateChainGraph'")
    @PostMapping("/{ruleCode}/graph/validate")
    public BaseResponse<List<RuleGraphValidator.GraphValidationIssue>> validateChainGraph(@RequestBody RuleChainGraph graph) {
        return BaseResponse.success(RuleGraphValidator.validate(graph));
    }

    /**
     * 表达式求值预览（P2-8）
     *
     * <p>给定表达式与样例事实数据，返回求值结果，供前端表达式编辑器实时预览。
     *
     * @param expression 表达式
     * @param facts      样例事实数据
     * @return 求值结果（含 value / type / error）
     */
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'previewExpression'")
    @PostMapping("/expressionPreview")
    public BaseResponse<ExpressionPreviewResultVO> previewExpression(
            @RequestParam String expression,
            @RequestBody Map<String, Object> facts) {
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(expressionValidationService.previewEvaluate(expression, facts)));
    }

    /**
     * 画布 Dry-run 仿真（P0-1 执行闭环）
     *
     * <p>将画布转换为可执行规则链后执行 Dry-run 评估，返回已触发的规则结果。
     * 画布不存在时返回空列表；画布校验失败返回 400。
     *
     * @param ruleCode 规则编码（画布关联 key）
     * @param facts    事实数据
     * @return 评估结果列表
     */
    @Idempotent(key = "ruleAdmin:dryRunGraph", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'dryRunGraph'")
    @PostMapping("/{ruleCode}/graph/dryRun")
    public BaseResponse<List<RuleResultVO>> dryRunGraph(@PathVariable String ruleCode,
                                                 @RequestBody Map<String, Object> facts) {
        try {
            List<RuleResult> results = graphExecutionProvider.dryRunGraph(ruleCode, facts);
            return BaseResponse.success(results.stream().map(LiteruleConverter.INSTANT::entityToVO).toList());
        } catch (IllegalArgumentException e) {
            log.warn("[RuleAdmin] 画布 dry-run 失败: ruleCode={}, err={}", ruleCode, e.getMessage());
            return BaseResponse.error(e.getMessage());
        }
    }

    /**
     * 检查画布中失效的规则引用（P0-1 执行闭环）
     *
     * <p>返回画布中引用了但已不存在或已禁用的规则编码列表，
     * 供前端在保存画布时提示用户修复失效节点。
     *
     * @param ruleCode 规则编码
     * @return 失效规则编码列表
     */
    @GetMapping("/{ruleCode}/graph/invalidRefs")
    public BaseResponse<List<StringVO>> invalidGraphRefs(@PathVariable String ruleCode) {
        return BaseResponse.success(graphExecutionProvider.collectInvalidReferences(ruleCode).stream().map(StringVO::new).toList());
    }

    /**
     * 获取已注册表达式函数列表
     *
     * <p>P1-7 函数市场：前端 CodeMirror 编辑器拉取此接口，渲染自动补全 + 悬浮文档。
     * 当前默认返回 18 个内置函数（string/math/convert/datetime/logic/type 六类）。
     *
     * @param engine 引擎类型（liteexpr/all），默认 all
     * @return 函数定义列表
     */
    @GetMapping("/expressionFunctions")
    public BaseResponse<List<ExpressionFunctionDefVO>> expressionFunctions(
            @RequestParam(value = "engine", defaultValue = "all") String engine) {
        List<ExpressionFunctionDef> all = ExpressionFunctionDef.defaults();
        List<ExpressionFunctionDef> filtered = all.stream()
                .filter(f -> "all".equalsIgnoreCase(engine)
                        || engine.equalsIgnoreCase(f.getSupportedEngines()))
                .toList();
        return BaseResponse.success(filtered.stream().map(LiteruleConverter.INSTANT::entityToVO).toList());
    }
}

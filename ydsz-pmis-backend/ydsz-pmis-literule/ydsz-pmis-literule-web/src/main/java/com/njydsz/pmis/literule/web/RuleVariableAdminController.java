package com.njydsz.pmis.literule.web;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.literule.server.expr.ExpressionValidationService;
import com.njydsz.pmis.literule.server.expr.VariableDefinition;
import com.njydsz.pmis.literule.server.expr.VariableRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则变量管理 Controller
 *
 * <p>提供规则变量空间元数据的 CRUD 与缓存刷新 REST API，供前端表达式编辑器
 * 自动补全、变量校验配置使用。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/variables")
@RequiredArgsConstructor
@Validated
public class RuleVariableAdminController {

    /** 变量注册表（由消费方提供数据库实现） */
    private final VariableRegistry variableRegistry;
    /** 表达式校验服务 */
    private final ExpressionValidationService expressionValidationService;

    /**
     * 列出全部已启用变量（支持按类别过滤）
     *
     * @param category 变量类别（可选，如 EVM / PROJECT / FINANCE）
     * @return 变量定义列表
     */
    @GetMapping
    public BaseResponse<List<VariableDefinition>> list(@RequestParam(required = false) String category) {
        List<VariableDefinition> all = variableRegistry.listAll();
        if (category == null || category.isBlank()) {
            return BaseResponse.ok(all);
        }
        return BaseResponse.ok(all.stream()
                .filter(v -> category.equals(v.getCategory()))
                .collect(Collectors.toList()));
    }

    /**
     * 查询单个变量定义
     *
     * @param varName 变量名
     * @return 变量定义
     */
    @GetMapping("/{varName}")
    public BaseResponse<VariableDefinition> get(@PathVariable String varName) {
        VariableDefinition def = variableRegistry.lookup(varName);
        if (def == null) {
            return BaseResponse.fail("变量不存在: " + varName);
        }
        return BaseResponse.ok(def);
    }

    /**
     * 新增/更新变量定义（upsert 语义，按 varName 唯一）
     *
     * @param definition 变量定义
     * @return 保存后的变量定义
     */
    @Idempotent(key = "ruleVariableAdmin:save", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<VariableDefinition> save(@RequestBody VariableDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            return BaseResponse.fail("变量定义及 name 不能为空");
        }
        variableRegistry.register(definition);
        return BaseResponse.ok(variableRegistry.lookup(definition.getName()));
    }

    /**
     * 删除变量定义
     *
     * @param varName 变量名
     * @return 操作结果
     */
    @OperationLog(module = "规则变量", action = "删除变量定义", bizType = "RULE_VARIABLE")
    @Idempotent(key = "ruleVariableAdmin:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{varName}")
    public BaseResponse<Void> delete(@PathVariable String varName) {
        variableRegistry.unregister(varName);
        return BaseResponse.ok();
    }

    /**
     * 手动刷新变量缓存（变量在 DB 侧被直接修改后调用）
     *
     * @return 操作结果
     */
    @Idempotent(key = "ruleVariableAdmin:refresh", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/refresh")
    public BaseResponse<Void> refresh() {
        variableRegistry.refresh();
        return BaseResponse.ok();
    }

    /**
     * 列出表达式校验服务当前可用的全部变量
     *
     * <p>等价于 {@link ExpressionValidationService#listAvailableVariables()}，
     * 与 {@code GET /} 的区别：本端点反映 {@link ExpressionValidationService}
     * 实际注入的 {@link VariableRegistry} 视角，
     * 便于排查 Bean 装配问题。
     *
     * @return 可用变量定义列表
     */
    @GetMapping("/available")
    public BaseResponse<List<VariableDefinition>> listAvailable() {
        return BaseResponse.ok(expressionValidationService.listAvailableVariables());
    }
}

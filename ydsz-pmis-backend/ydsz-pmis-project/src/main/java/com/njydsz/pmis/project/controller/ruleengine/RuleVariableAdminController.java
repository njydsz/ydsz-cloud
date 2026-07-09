package com.njydsz.pmis.project.controller.ruleengine;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.literule.expr.ExpressionValidationService;
import com.njydsz.pmis.literule.expr.VariableDefinition;
import com.njydsz.pmis.project.literule.DatabaseVariableRegistry;
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
@RequestMapping("/rule-engine/variables")
@RequiredArgsConstructor
@Validated
public class RuleVariableAdminController {

    /** 数据库变量注册表 */
    private final DatabaseVariableRegistry variableRegistry;
    /** 表达式校验服务 */
    private final ExpressionValidationService expressionValidationService;

    /**
     * 列出全部已启用变量（支持按类别过滤）
     *
     * @param category 变量类别（可选，如 EVM / PROJECT / FINANCE）
     * @return 变量定义列表
     */
    @GetMapping
    public Result<List<VariableDefinition>> list(@RequestParam(required = false) String category) {
        List<VariableDefinition> all = variableRegistry.listAll();
        if (category == null || category.isBlank()) {
            return Result.ok(all);
        }
        return Result.ok(all.stream()
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
    public Result<VariableDefinition> get(@PathVariable String varName) {
        VariableDefinition def = variableRegistry.lookup(varName);
        if (def == null) {
            return Result.fail("变量不存在: " + varName);
        }
        return Result.ok(def);
    }

    /**
     * 新增/更新变量定义（upsert 语义，按 varName 唯一）
     *
     * @param definition 变量定义
     * @return 保存后的变量定义
     */
    @Idempotent(key = "rule-variable-admin:save", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<VariableDefinition> save(@RequestBody VariableDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            return Result.fail("变量定义及 name 不能为空");
        }
        variableRegistry.register(definition);
        return Result.ok(variableRegistry.lookup(definition.getName()));
    }

    /**
     * 删除变量定义
     *
     * @param varName 变量名
     * @return 操作结果
     */
    @OperationLog(module = "规则变量", action = "删除变量定义", bizType = "RULE_VARIABLE")
    @Idempotent(key = "rule-variable-admin:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{varName}")
    public Result<Void> delete(@PathVariable String varName) {
        variableRegistry.unregister(varName);
        return Result.ok();
    }

    /**
     * 手动刷新变量缓存（变量在 DB 侧被直接修改后调用）
     *
     * @return 操作结果
     */
    @Idempotent(key = "rule-variable-admin:refresh", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        variableRegistry.refresh();
        return Result.ok();
    }

    /**
     * 列出表达式校验服务当前可用的全部变量
     *
     * <p>等价于 {@link ExpressionValidationService#listAvailableVariables()}，
     * 与 {@code GET /} 的区别：本端点反映 {@link ExpressionValidationService}
     * 实际注入的 {@link com.njydsz.pmis.literule.expr.VariableRegistry} 视角，
     * 便于排查 Bean 装配问题。
     *
     * @return 可用变量定义列表
     */
    @GetMapping("/available")
    public Result<List<VariableDefinition>> listAvailable() {
        return Result.ok(expressionValidationService.listAvailableVariables());
    }
}

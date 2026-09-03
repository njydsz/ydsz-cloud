package com.njydsz.literule.web.controller;

import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.domain.vo.VariableDefinitionVO;
import com.njydsz.literule.server.expression.ExpressionValidationService;
import com.njydsz.literule.server.expression.VariableDefinition;
import com.njydsz.literule.server.expression.VariableRegistry;

/**
 * 规则变量管理 Controller
 *
 * <p>提供规则变量空间元数据的 CRUD 与缓存刷新 REST API，供前端表达式编辑器 自动补全、变量校验配置使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/variables")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则变量管理", description = "变量注册表 CRUD、变量分类查询")
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
  public YdszResponse<List<VariableDefinitionVO>> list(
      @RequestParam(required = false) String category) {
    List<VariableDefinition> all = variableRegistry.listAll();
    if (category == null || category.isBlank()) {
      return YdszResponse.success(all.stream().map(this::toVariableVO).toList());
    }
    return YdszResponse.success(
        all.stream()
            .filter(v -> category.equals(v.getCategory()))
            .map(this::toVariableVO)
            .collect(Collectors.toList()));
  }

  /**
   * 查询单个变量定义
   *
   * @param varName 变量名
   * @return 变量定义
   */
  @GetMapping("/{varName}")
  public YdszResponse<VariableDefinitionVO> get(@PathVariable String varName) {
    VariableDefinition def = variableRegistry.lookup(varName);
    if (def == null) {
      return YdszResponse.error(LiteruleExceptionCode.VARIABLE_DEF_NOT_FOUND, "变量不存在: " + varName);
    }
    return YdszResponse.success(toVariableVO(def));
  }

  /**
   * 新增/更新变量定义（upsert 语义，按 varName 唯一）
   *
   * @param definition 变量定义
   * @return 保存后的变量定义
   */
  @Idempotent(key = "ruleVariableAdmin:save", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "变量管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'save'")
  @RateLimit(resource = "literule.rule_variable_admin.save", threshold = 50)
  @PostMapping
  public YdszResponse<VariableDefinitionVO> save(@RequestBody VariableDefinition definition) {
    if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "变量定义及 name 不能为空");
    }
    variableRegistry.register(definition);
    return YdszResponse.success(toVariableVO(variableRegistry.lookup(definition.getName())));
  }

  /**
   * 删除变量定义
   *
   * @param varName 变量名
   * @return 操作结果
   */
  @Idempotent(key = "ruleVariableAdmin:delete", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "变量管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'delete'")
  @RateLimit(resource = "literule.rule_variable_admin.delete", threshold = 50)
  @DeleteMapping("/{varName}")
  public YdszResponse<Void> delete(@PathVariable String varName) {
    variableRegistry.unregister(varName);
    return YdszResponse.success();
  }

  /**
   * 手动刷新变量缓存（变量在 DB 侧被直接修改后调用）
   *
   * @return 操作结果
   */
  @Idempotent(key = "ruleVariableAdmin:refresh", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "变量管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'refresh'")
  @RateLimit(resource = "literule.rule_variable_admin.refresh", threshold = 50)
  @PostMapping("/refresh")
  public YdszResponse<Void> refresh() {
    variableRegistry.refresh();
    return YdszResponse.success();
  }

  /**
   * 列出表达式校验服务当前可用的全部变量
   *
   * <p>等价于 {@link ExpressionValidationService#listAvailableVariables()}， 与 {@code GET /} 的区别：本端点反映
   * {@link ExpressionValidationService} 实际注入的 {@link VariableRegistry} 视角， 便于排查 Bean 装配问题。
   *
   * @return 可用变量定义列表
   */
  @GetMapping("/available")
  public YdszResponse<List<VariableDefinitionVO>> listAvailable() {
    return YdszResponse.success(
        expressionValidationService.listAvailableVariables().stream()
            .map(this::toVariableVO)
            .toList());
  }

  /** VariableDefinition → VariableDefinitionVO 转换 */
  private VariableDefinitionVO toVariableVO(VariableDefinition v) {
    if (v == null) {
      return null;
    }
    VariableDefinitionVO vo = new VariableDefinitionVO();
    vo.setName(v.getName());
    vo.setType(v.getType());
    vo.setDescription(v.getDescription());
    vo.setSampleValue(v.getSampleValue());
    vo.setCategory(v.getCategory());
    return vo;
  }
}

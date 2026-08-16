package com.njydsz.literule.web;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.api.dto.RuleDependencyAddDTO;
import com.njydsz.literule.domain.converter.LiteruleConverter;
import com.njydsz.literule.domain.vo.RuleDependencyVO;
import com.njydsz.literule.domain.vo.StringVO;
import com.njydsz.literule.server.spi.RuleDependencyProvider;

/**
 * 规则依赖 Controller
 *
 * <p>业务背景：复杂业务场景下规则之间存在依赖关系（如规则 A 触发后才能执行规则 B）， 通过显式声明依赖关系，规则引擎可在禁用规则时自动级联禁用下游规则，避免误判。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>添加/删除规则依赖
 *   <li>查询正向依赖（依赖了哪些规则）与反向依赖（被哪些规则依赖）
 *   <li>级联禁用影响分析
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径 {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/rule-engine/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则依赖", description = "规则间依赖关系管理与级联禁用分析")
public class RuleDependencyController {

  /** 规则依赖服务（SPI，由 project 模块提供实现） */
  private final RuleDependencyProvider ruleDependencyProvider;

  /** 添加规则依赖 */
  @Idempotent(key = "ruleAdmin:addDependency", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'addDependency'")
  @RateLimit(resource = "literule.rule_dependency.addDependency", threshold = 50)
  @PostMapping("/{ruleCode}/dependencies")
  public BaseResponse<RuleDependencyVO> addDependency(
      @PathVariable String ruleCode,
      @Valid @RequestBody RuleDependencyAddDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    String dependsOn = dto.getDependsOnRuleCode();
    String depType = dto.getDependencyType() == null ? "EXECUTE" : dto.getDependencyType();
    Boolean cascade = dto.getCascadeOnDisable() == null ? false : dto.getCascadeOnDisable();
    String description = dto.getDescription();
    return BaseResponse.success(
        LiteruleConverter.INSTANT.entityToVO(
            ruleDependencyProvider.add(
                ruleCode, dependsOn, depType, cascade, description, operator)));
  }

  /** 删除规则依赖 */
  @Idempotent(key = "ruleAdmin:removeDependency", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'removeDependency'")
  @RateLimit(resource = "literule.rule_dependency.removeDependency", threshold = 50)
  @DeleteMapping("/{ruleCode}/dependencies/{dependsOnRuleCode}")
  public BaseResponse<Void> removeDependency(
      @PathVariable String ruleCode, @PathVariable String dependsOnRuleCode) {
    ruleDependencyProvider.remove(ruleCode, dependsOnRuleCode);
    return BaseResponse.success();
  }

  /** 查询规则的依赖（正向：依赖了哪些） */
  @GetMapping("/{ruleCode}/dependencies")
  public BaseResponse<List<RuleDependencyVO>> listDependencies(@PathVariable String ruleCode) {
    return BaseResponse.success(
        LiteruleConverter.INSTANT.ruleDependencyListToVO(
            ruleDependencyProvider.listDependencies(ruleCode)));
  }

  /** 查询被依赖（反向：被哪些规则依赖） */
  @GetMapping("/{ruleCode}/dependents")
  public BaseResponse<List<RuleDependencyVO>> listDependents(@PathVariable String ruleCode) {
    return BaseResponse.success(
        LiteruleConverter.INSTANT.ruleDependencyListToVO(
            ruleDependencyProvider.listDependents(ruleCode)));
  }

  /** 查询级联禁用影响（disable ruleCode 时，需要级联禁用的规则列表） */
  @GetMapping("/{ruleCode}/cascading-disable")
  public BaseResponse<List<StringVO>> cascadingDisable(@PathVariable String ruleCode) {
    return BaseResponse.success(
        ruleDependencyProvider.cascadingDisable(ruleCode).stream().map(StringVO::new).toList());
  }
}

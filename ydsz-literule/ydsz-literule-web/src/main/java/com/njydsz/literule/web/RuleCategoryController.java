package com.njydsz.literule.web;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.vo.CategoryNodeVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.spi.RuleCategoryProvider;

/**
 * 规则目录树 Controller
 *
 * <p>业务背景：随着规则数量增长，需要按业务分类组织规则形成目录树， 同时支持按责任人（Owner）维度查询和分配规则，便于运营人员分模块管理。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>构建规则分类目录树（含每节点规则数与 Owner 列表）
 *   <li>按分类路径前缀 / Owner 查询规则
 *   <li>设置规则的责任人与分类路径
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径 {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则目录树", description = "规则分类目录树与责任人管理")
public class RuleCategoryController {

  /** 规则分类树服务（SPI，由 project 模块提供实现） */
  private final RuleCategoryProvider ruleCategoryProvider;

  /** 规则管理服务 */
  private final RuleAdminService ruleAdminService;

  /**
   * 获取规则目录树
   *
   * <p>树根为虚拟 ROOT，children 为一级分类。叶子节点或中间节点都包含该路径下的规则数与 Owner 列表。
   * @return 规则目录树根节点
   */
  @GetMapping("/category-tree")
  public YdszResponse<CategoryNodeVO> categoryTree() {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(ruleCategoryProvider.buildTree()));
  }

  /**
   * 按分类路径前缀查询规则
   *
   * @param path 分类路径前缀，例如 "finance" / "finance/credit"
   * @return 规则定义列表
   */
  @GetMapping("/by-category-path")
  public YdszResponse<List<RuleDefinitionVO>> listByCategoryPath(
      @RequestParam(value = "path", required = false) String path) {
    return YdszResponse.success(
        ruleCategoryProvider.listDefinitionsByCategoryPath(path).stream()
            .map(LiteruleWebConverter.INSTANCE::entityToVO)
            .toList());
  }

  /** 按 Owner 查询规则
   * @param owner 责任人用户名
   * @return 规则定义列表
   */
  @GetMapping("/by-owner")
  public YdszResponse<List<RuleDefinitionVO>> listByOwner(
      @RequestParam(value = "owner") String owner) {
    return YdszResponse.success(
        ruleCategoryProvider.listDefinitionsByOwner(owner).stream()
            .map(LiteruleWebConverter.INSTANCE::entityToVO)
            .toList());
  }

  /** 设置规则责任人
   * @param ruleCode 规则唯一编码
   * @param owner 责任人用户名
   * @param operator 操作人用户名
   * @return 无返回内容
   */
  @Idempotent(key = "ruleAdmin:setOwner", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'setOwner'")
  @RateLimit(resource = "literule.rule_category.setOwner", threshold = 50)
  @PutMapping("/{ruleCode}/owner")
  public YdszResponse<Void> setOwner(
      @PathVariable String ruleCode,
      @RequestParam(value = "owner") String owner,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    ruleAdminService.updateOwner(ruleCode, owner, operator);
    return YdszResponse.success();
  }

  /** 设置规则分类路径
   * @param ruleCode 规则唯一编码
   * @param path 分类路径（如 "finance/credit"）
   * @param operator 操作人用户名
   * @return 无返回内容
   */
  @Idempotent(key = "ruleAdmin:setCategoryPath", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'setCategoryPath'")
  @RateLimit(resource = "literule.rule_category.setCategoryPath", threshold = 50)
  @PutMapping("/{ruleCode}/category-path")
  public YdszResponse<Void> setCategoryPath(
      @PathVariable String ruleCode,
      @RequestParam(value = "path") String path,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    ruleAdminService.updateCategoryPath(ruleCode, path, operator);
    return YdszResponse.success();
  }
}


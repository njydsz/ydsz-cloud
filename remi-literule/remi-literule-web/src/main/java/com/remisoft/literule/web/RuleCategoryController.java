package com.remisoft.literule.web;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.literule.domain.vo.CategoryNodeVO;
import com.remisoft.literule.domain.vo.RuleDefinitionVO;
import com.remisoft.literule.server.config.RuleAdminService;
import com.remisoft.literule.server.spi.RuleCategoryProvider;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.remisoft.literule.domain.converter.LiteruleConverter;

/**
 * 规则目录树 Controller
 *
 * <p>业务背景：随着规则数量增长，需要按业务分类组织规则形成目录树，
 * 同时支持按责任人（Owner）维度查询和分配规则，便于运营人员分模块管理。
 *
 * <p>核心能力：
 * <ul>
 *   <li>构建规则分类目录树（含每节点规则数与 Owner 列表）</li>
 *   <li>按分类路径前缀 / Owner 查询规则</li>
 *   <li>设置规则的责任人与分类路径</li>
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径
 * {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/rules")
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
     */
    @GetMapping("/categoryTree")
    public BaseResponse<CategoryNodeVO> categoryTree() {
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(ruleCategoryProvider.buildTree()));
    }

    /**
     * 按分类路径前缀查询规则
     *
     * @param path 分类路径前缀，例如 "finance" / "finance/credit"
     */
    @GetMapping("/byCategoryPath")
    public BaseResponse<List<RuleDefinitionVO>> listByCategoryPath(
            @RequestParam(value = "path", required = false) String path) {
        return BaseResponse.success(ruleCategoryProvider.listDefinitionsByCategoryPath(path).stream().map(LiteruleConverter.INSTANT::entityToVO).toList());
    }

    /**
     * 按 Owner 查询规则
     */
    @GetMapping("/byOwner")
    public BaseResponse<List<RuleDefinitionVO>> listByOwner(
            @RequestParam(value = "owner") String owner) {
        return BaseResponse.success(ruleCategoryProvider.listDefinitionsByOwner(owner).stream().map(LiteruleConverter.INSTANT::entityToVO).toList());
    }

    /**
     * 设置规则责任人
     */
    @Idempotent(key = "ruleAdmin:setOwner", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'setOwner'")
    @PutMapping("/{ruleCode}/owner")
    public BaseResponse<Void> setOwner(
            @PathVariable String ruleCode,
            @RequestParam(value = "owner") String owner,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.updateOwner(ruleCode, owner, operator);
        return BaseResponse.success();
    }

    /**
     * 设置规则分类路径
     */
    @Idempotent(key = "ruleAdmin:setCategoryPath", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'setCategoryPath'")
    @PutMapping("/{ruleCode}/categoryPath")
    public BaseResponse<Void> setCategoryPath(
            @PathVariable String ruleCode,
            @RequestParam(value = "path") String path,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.updateCategoryPath(ruleCode, path, operator);
        return BaseResponse.success();
    }
}

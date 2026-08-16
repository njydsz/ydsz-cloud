package com.njydsz.literule.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleStatus;
import com.njydsz.literule.api.dto.RuleBatchCategoryDTO;
import com.njydsz.literule.api.dto.RuleBatchPriorityDTO;
import com.njydsz.literule.api.dto.RuleBatchToggleDTO;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.spi.RuleChainGraphProvider;

/**
 * 规则批量操作 Controller
 *
 * <p>业务背景：规则列表页支持 checkbox 多选后批量操作，包括批量启停、
 * 批量调整优先级、批量调整分类。同时承载规则软删除接口，删除时将状态置为
 * ARCHIVED 并保留版本历史，同步清理画布。
 *
 * <p>核心能力：
 * <ul>
 *   <li>规则软删除（status=ARCHIVED + enabled=false + 清理画布）</li>
 *   <li>批量启停（启用时校验 status=PUBLISHED）</li>
 *   <li>批量调整优先级（钳制 0-100 范围）</li>
 *   <li>批量调整分类</li>
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
@Tag(name = "规则批量操作", description = "规则批量启停、优先级调整、分类调整与软删除")
public class RuleBatchController {

    /** 规则管理服务 */
    private final RuleAdminService ruleAdminService;
    /** 规则链图服务（SPI，由 project 模块提供实现） */
    private final RuleChainGraphProvider ruleChainGraphProvider;

    /**
     * 删除规则（软删除：将状态置为 ARCHIVED，保留版本历史）
     *
     * <p>P0-4 关键修复：补全前端规则引擎页"删除"按钮对应的后端接口。
     * 软删除策略：status=ARCHIVED + enabled=false，保留 ydsz_rule_def 原行；
     * 同步清理 ydsz_rule_chain_graph 画布。
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     * @return 操作结果
     */
    @Idempotent(key = "ruleAdmin:deleteRule", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteRule'")
    @RateLimit(resource = "literule.rule_batch.deleteRule", threshold = 50)
    @DeleteMapping("/{ruleCode}")
    @AuthApiPermission(apiCodes = "execution:rule:delete")
    public BaseResponse<Void> deleteRule(@PathVariable String ruleCode,
                                   @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
        }
        RuleStatus current = parseStatusSafely(def.getStatus());
        if (current == null) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_STATUS_INVALID, "规则状态非法: " + def.getStatus());
        }
        if (!current.canTransitionTo(RuleStatus.ARCHIVED)) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_STATUS_INVALID, "当前状态 " + current.getDesc() + " 不允许删除（归档），仅 DRAFT/REVIEW/PUBLISHED/DISABLED 可删除");
        }
        def.setStatus(RuleStatus.ARCHIVED.name());
        def.setEnabled(false);
        ruleAdminService.save(def, operator, "[删除] 软删除规则 status=ARCHIVED");
        // 同步删除画布
        ruleChainGraphProvider.delete(ruleCode);
        log.info("[LiteRule] 规则已删除: ruleCode={}, operator={}", ruleCode, operator);
        return BaseResponse.success();
    }

    /**
     * 安全解析规则状态，无效值返回 null 而非伪装成 PUBLISHED
     */
    private RuleStatus parseStatusSafely(String status) {
        try {
            return RuleStatus.valueOf(status);
        } catch (Exception e) {
            log.warn("规则状态解析失败，status={}", status, e);
            return null;
        }
    }

    /**
     * 批量启停规则
     *
     * <p>P0-5 关键修复：列表加 checkbox 后批量操作接口。
     * 启用时同时校验 status=PUBLISHED，未发布的规则不能启用。
     *
     * @param request  请求体，包含 ruleCodes / enabled
     * @param operator 操作人
     * @return 成功与失败明细
     */
    @Idempotent(key = "ruleAdmin:batchToggle", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @RateLimit(resource = "literule.rule_batch.batchToggle", threshold = 50)
    @PostMapping("/batchToggle")
    @AuthApiPermission(apiCodes = "execution:rule:toggle")
    public BaseResponse<Map<String, Object>> batchToggle(@Valid @RequestBody RuleBatchToggleDTO dto,
                                                   @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<String> ruleCodes = dto.getRuleCodes();
        Boolean enabled = dto.getEnabled();
        // @NotEmpty + @NotNull 已校验非空，移除手动校验
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (String code : ruleCodes) {
            try {
                RuleDefinition def = ruleAdminService.getByCode(code);
                if (def == null) {
                    failed.add(code + ": 不存在");
                    continue;
                }
                if (Boolean.TRUE.equals(enabled) && !"PUBLISHED".equals(def.getStatus())) {
                    failed.add(code + ": 未发布的规则不能启用");
                    continue;
                }
                def.setEnabled(enabled);
                ruleAdminService.save(def, operator, "[批量] " + (enabled ? "启用" : "停用"));
                success++;
            } catch (Exception e) {
                failed.add(code + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        return BaseResponse.success(result);
    }

    /**
     * 批量调整规则优先级
     *
     * @param request  请求体，包含 ruleCodes / delta（可为负）
     * @param operator 操作人
     * @return 成功与失败明细
     */
    @Idempotent(key = "ruleAdmin:batchPriority", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @RateLimit(resource = "literule.rule_batch.batchPriority", threshold = 50)
    @PostMapping("/batchPriority")
    public BaseResponse<Map<String, Object>> batchPriority(@Valid @RequestBody RuleBatchPriorityDTO dto,
                                                      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<String> ruleCodes = dto.getRuleCodes();
        Integer delta = dto.getDelta();
        // @NotEmpty + @NotNull 已校验非空；delta==0 需保留手动校验（JSR-303 无原生非零约束）
        if (delta == 0) {
            return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "delta 不能为 0");
        }
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (String code : ruleCodes) {
            try {
                RuleDefinition def = ruleAdminService.getByCode(code);
                if (def == null) {
                    failed.add(code + ": 不存在");
                    continue;
                }
                int newPriority = (def.getPriority()) + delta.intValue();
                // 钳制 0-100 范围
                newPriority = Math.max(0, Math.min(100, newPriority));
                def.setPriority(newPriority);
                ruleAdminService.save(def, operator, "[批量] 优先级调整 " + (delta > 0 ? "+" : "") + delta);
                success++;
            } catch (Exception e) {
                failed.add(code + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        return BaseResponse.success(result);
    }

    /**
     * 批量调整规则分类
     *
     * @param request  请求体，包含 ruleCodes / category
     * @param operator 操作人
     * @return 成功与失败明细
     */
    @Idempotent(key = "ruleAdmin:batchCategory", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @RateLimit(resource = "literule.rule_batch.batchCategory", threshold = 50)
    @PostMapping("/batchCategory")
    public BaseResponse<Map<String, Object>> batchCategory(@Valid @RequestBody RuleBatchCategoryDTO dto,
                                                      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<String> ruleCodes = dto.getRuleCodes();
        String category = dto.getCategory();
        // @NotEmpty + @NotBlank 已校验非空，移除手动校验
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (String code : ruleCodes) {
            try {
                RuleDefinition def = ruleAdminService.getByCode(code);
                if (def == null) {
                    failed.add(code + ": 不存在");
                    continue;
                }
                def.setCategory(category);
                ruleAdminService.save(def, operator, "[批量] 分类调整为 " + category);
                success++;
            } catch (Exception e) {
                failed.add(code + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        return BaseResponse.success(result);
    }
}

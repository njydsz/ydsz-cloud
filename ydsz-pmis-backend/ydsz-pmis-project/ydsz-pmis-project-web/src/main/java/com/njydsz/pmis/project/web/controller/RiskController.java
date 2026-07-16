package com.njydsz.pmis.project.web.controller.execution;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.project.domain.dto.RiskCreateDTO;
import com.njydsz.pmis.project.domain.dto.RiskStatusDTO;
import com.njydsz.pmis.project.domain.vo.RiskVO;
import com.njydsz.pmis.project.server.service.RiskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 项目风险 Controller
 *
 * <p>负责风险登记、状态迁移、分页查询及按等级聚合统计。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "风险管理", description = "风险管理相关接口")
@RestController
@RequestMapping("/api/project/execution/risk")
@RequiredArgsConstructor
@Validated
public class RiskController {

    /** 风险管理服务 */
    private final RiskService service;

    /**
     * 登记项目风险
     *
     * @param dto 风险创建参数
     * @return 新建风险 ID
     */
    @Operation(summary = "登记风险")
    @AuthApiPermission(apiCodes = "execution:risk:create")
    @Idempotent(key = "risk:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody RiskCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    /**
     * 风险状态迁移
     *
     * @param dto 状态变更参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @AuthApiPermission(apiCodes = "execution:risk:status")
    @Idempotent(key = "risk:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public BaseResponse<Void> changeStatus(@Valid @RequestBody RiskStatusDTO dto) {
        service.changeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除风险
     *
     * @param id 风险 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @AuthApiPermission(apiCodes = "execution:risk:delete")
    @Idempotent(key = "risk:delete", ttlSeconds = 5, message = "请勿重复提交")
    @OperationLog(module = "风险管理", action = "删除风险", bizType = "RISK")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@Parameter(description = "风险ID") @PathVariable String id) {
        service.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询风险详情
     *
     * @param id 风险 ID
     * @return 风险 VO（剥离 tenantId/providerTraceId/deleted/version）
     */
    @Operation(summary = "详情")
    @AuthApiPermission(apiCodes = "execution:risk:list")
    @GetMapping("/{id}")
    public BaseResponse<RiskVO> get(@Parameter(description = "风险ID") @PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    /**
     * 分页查询风险
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param riskLevel    风险等级过滤
     * @param initiationId 项目立项 ID
     * @return 分页结果（VO）
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apiCodes = "execution:risk:list")
    @GetMapping("/page")
    public BaseResponse<Page<RiskVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "风险等级") @RequestParam(required = false) String riskLevel,
            @Parameter(description = "立项ID") @RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(service.page(page, size, keyword, status, riskLevel, initiationId));
    }

    /**
     * 按风险等级聚合统计
     *
     * @param initiationId 项目立项 ID
     * @return 各等级风险数量列表
     */
    @Operation(summary = "按等级聚合")
    @AuthApiPermission(apiCodes = "execution:risk:list")
    @GetMapping("/aggregate/byLevel")
    public BaseResponse<List<Map<String, Object>>> aggregateByLevel(@Parameter(description = "立项ID") @RequestParam String initiationId) {
        return BaseResponse.ok(service.aggregateByLevel(initiationId));
    }
}

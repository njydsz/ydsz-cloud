package com.njydsz.pmis.project.web.controller.initiation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.project.domain.dto.ProjectChangeCreateDTO;
import com.njydsz.pmis.project.domain.dto.ProjectChangeStatusDTO;
import com.njydsz.pmis.project.domain.entity.ProjectChangeDO;
import com.njydsz.pmis.project.domain.enums.ChangeStatus;
import com.njydsz.pmis.project.server.service.ProjectChangeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 项目变更 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "项目变更管理")
@RestController
@RequestMapping("/initiation/change")
@RequiredArgsConstructor
@Validated
public class ProjectChangeController {

    /** 项目变更服务 */
    private final ProjectChangeService service;

    /**
     * 创建项目变更。
     *
     * @param dto 变更创建参数
     * @return 变更记录 ID
     */
    @Operation(summary = "创建项目变更")
    @AuthApiPermission(apiCodes = "project:change:create")
    @Idempotent(key = "projectChange:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody ProjectChangeCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    /**
     * 项目变更状态迁移（遵循 ChangeStatus 状态机）。
     *
     * @param dto 状态迁移参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @AuthApiPermission(apiCodes = "project:change:status")
    @Idempotent(key = "projectChange:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public BaseResponse<Void> changeStatus(@Valid @RequestBody ProjectChangeStatusDTO dto) {
        service.changeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除项目变更（逻辑删除）。
     *
     * @param id 变更 ID
     * @return 空结果
     */
    @Operation(summary = "删除变更")
    @AuthApiPermission(apiCodes = "project:change:delete")
    @Idempotent(key = "projectChange:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询变更详情。
     *
     * @param id 变更 ID
     * @return 变更实体
     */
    @Operation(summary = "变更详情")
    @AuthApiPermission(apiCodes = "project:change:list")
    @GetMapping("/{id}")
    public BaseResponse<ProjectChangeDO> get(@PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    /**
     * 分页查询项目变更列表。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编号/名称），可空
     * @param changeType   变更类型，可空
     * @param status       状态码，可空
     * @param initiationId 立项 ID，可空
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apiCodes = "project:change:list")
    @GetMapping("/page")
    public BaseResponse<Page<ProjectChangeDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(service.page(page, size, keyword, changeType, status, initiationId));
    }

    /**
     * 按立项查询变更记录列表。
     *
     * @param initiationId 立项 ID
     * @return 变更记录列表
     */
    @Operation(summary = "按项目查询变更列表")
    @AuthApiPermission(apiCodes = "project:change:list")
    @GetMapping("/listByInitiation/{initiationId}")
    public BaseResponse<List<ProjectChangeDO>> listByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(service.listByInitiation(initiationId));
    }

    /**
     * 按变更类型聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 每种变更类型对应的数量列表
     */
    @Operation(summary = "按变更类型聚合")
    @AuthApiPermission(apiCodes = "project:change:list")
    @GetMapping("/aggregate/type")
    public BaseResponse<List<Map<String, Object>>> aggregateByType(@RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(service.aggregateByType(tenantId));
    }

    /**
     * 按状态聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 每种状态对应的数量列表
     */
    @Operation(summary = "按状态聚合")
    @AuthApiPermission(apiCodes = "project:change:list")
    @GetMapping("/aggregate/status")
    public BaseResponse<List<Map<String, Object>>> aggregateByStatus(@RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(service.aggregateByStatus(tenantId));
    }

    /**
     * 统计项目的重大变更数量。
     *
     * @param initiationId 立项 ID
     * @return 重大变更数量
     */
    @Operation(summary = "统计项目重大变更数")
    @AuthApiPermission(apiCodes = "project:change:list")
    @GetMapping("/majorCount/{initiationId}")
    public BaseResponse<Integer> countMajor(@PathVariable String initiationId) {
        return BaseResponse.ok(service.countMajorByInitiation(initiationId));
    }

    /**
     * 获取某条变更的合法状态迁移列表
     * <p>
     * 前端使用: 进入详情或审批时拉取, 用于即时判断按钮可用性 + 友好文案.
     * 重大变更 (majorFlag=1) 在 UNDER_REVIEW → APPROVED 时需要双审批, 前端应额外提示.
     * </p>
     *
     * @param id 变更 ID
     * @return 合法目标状态码列表 (e.g. ["SUBMITTED", "CANCELLED"])
     */
    @Operation(summary = "获取合法状态迁移列表")
    @AuthApiPermission(apiCodes = "project:change:list")
    @GetMapping("/{id}/allowedTransitions")
    public BaseResponse<List<String>> getAllowedTransitions(@PathVariable String id) {
        ProjectChangeDO change = service.getById(id);
        if (change == null) {
            return BaseResponse.ok(List.of());
        }
        ChangeStatus current = ChangeStatus.fromCode(change.getStatus());
        if (current == null || current.isTerminal()) {
            return BaseResponse.ok(List.of());
        }
        List<String> allowed = Arrays.stream(ChangeStatus.values())
                .filter(s -> current.canTransitTo(s))
                .map(ChangeStatus::getCode)
                .collect(Collectors.toList());
        return BaseResponse.ok(allowed);
    }

    /**
     * 列出所有 ChangeStatus 状态码 + 中文描述
     * <p>前端使用: 渲染状态下拉 / 字典 / 国际化</p>
     */
    @Operation(summary = "获取所有变更状态字典")
    @AuthApiPermission(apiCodes = "project:change:list")
    @GetMapping("/statusDict")
    public BaseResponse<List<Map<String, String>>> getStatusDict() {
        List<Map<String, String>> list = new ArrayList<>();
        for (ChangeStatus s : ChangeStatus.values()) {
            list.add(Map.of(
                "code", s.getCode(),
                "desc", s.getDesc(),
                "terminal", String.valueOf(s.isTerminal())
            ));
        }
        return BaseResponse.ok(list);
    }
}

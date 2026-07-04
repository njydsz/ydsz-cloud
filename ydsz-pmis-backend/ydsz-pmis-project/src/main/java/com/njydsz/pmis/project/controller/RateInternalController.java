package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.RateInternalCreateDTO;
import com.njydsz.pmis.project.entity.RateInternalDO;
import com.njydsz.pmis.project.service.RateInternalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 对内成本费率 Controller
 *
 * <p>负责对内成本费率的创建、匹配（职级+部门优先）、分页查询及生效费率命中。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "对内成本费率")
@RestController
@RequestMapping("/api/v1/execution/rate-internal")
@RequiredArgsConstructor
@Validated
public class RateInternalController {

    private final RateInternalService service;

    /**
     * 创建对内成本费率
     *
     * @param dto 费率创建参数
     * @return 新建费率 ID
     */
    @Operation(summary = "创建对内成本费率")
    @PrePermission("execution:rate-internal:create")
    @Idempotent(key = "rate-internal:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody RateInternalCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 更新对内成本费率
     *
     * @param id  费率 ID
     * @param dto 费率更新参数
     * @return 空结果
     */
    @Operation(summary = "更新")
    @PrePermission("execution:rate-internal:update")
    @Idempotent(key = "rate-internal:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Min(1) Longid, @Valid @RequestBody RateInternalCreateDTO dto) {
        service.update(id, dto);
        return Result.ok();
    }

    /**
     * 删除对内成本费率
     *
     * @param id 费率 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @PrePermission("execution:rate-internal:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Longid) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询对内成本费率详情
     *
     * @param id 费率 ID
     * @return 费率实体
     */
    @Operation(summary = "详情")
    @PrePermission("execution:rate:list")
    @GetMapping("/{id}")
    public Result<RateInternalDO> get(@PathVariable @Min(1) Longid) {
        return Result.ok(service.getById(id));
    }

    /**
     * 命中有效成本费率（职级+部门+日期）
     *
     * @param levelCode    职级编码
     * @param departmentId 部门 ID，可选
     * @param date         生效日期，可选
     * @return 命中的费率实体
     */
    @Operation(summary = "命中有效成本费率（职级+部门+日期）")
    @PrePermission("execution:rate:list")
    @GetMapping("/match")
    public Result<RateInternalDO> match(
            @RequestParam String levelCode,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(service.matchEffective(levelCode, departmentId, date));
    }

    /**
     * 按职级+部门查询费率
     *
     * @param levelCode    职级编码
     * @param departmentId 部门 ID，可选
     * @return 费率列表
     */
    @Operation(summary = "按职级+部门查询")
    @PrePermission("execution:rate:list")
    @GetMapping("/by-level-dept")
    public Result<List<RateInternalDO>> listByLevelAndDept(
            @RequestParam String levelCode,
            @RequestParam(required = false) Long departmentId) {
        return Result.ok(service.listByLevelAndDept(levelCode, departmentId));
    }

    /**
     * 分页查询对内成本费率
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param levelCode    职级编码
     * @param departmentId 部门 ID
     * @param status       状态过滤
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @PrePermission("execution:rate:list")
    @GetMapping("/page")
    public Result<Page<RateInternalDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String levelCode,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, levelCode, departmentId, status));
    }
}

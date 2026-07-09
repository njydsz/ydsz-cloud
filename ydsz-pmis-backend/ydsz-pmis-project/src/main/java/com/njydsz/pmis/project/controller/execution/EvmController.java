package com.njydsz.pmis.project.controller.execution;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.execution.EvmMeasureCreateDTO;
import com.njydsz.pmis.project.service.execution.EvmMeasureService;
import com.njydsz.pmis.project.vo.execution.EvmMeasureVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * EVM 挣值管理 Controller
 *
 * <p>负责挣值测量数据的录入/更新（幂等）、偏差趋势及驾驶舱健康度查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "EVM 挣值管理")
@RestController
@RequestMapping("/execution/evm")
@RequiredArgsConstructor
@Validated
public class EvmController {

    /** EVM 挣值度量服务 */
    private final EvmMeasureService service;

    /**
     * 录入/更新 EVM 测量（按 initiation+wbs+period 幂等）
     *
     * @param dto EVM 测量参数
     * @return 测量记录 ID
     */
    @Operation(summary = "录入/更新 EVM 测量（按 initiation+wbs+period 幂等）")
    @PrePermission("execution:evm:save")
    @Idempotent(key = "evm:save", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> save(@Valid @RequestBody EvmMeasureCreateDTO dto) {
        return Result.ok(service.save(dto));
    }

    /**
     * 查询 EVM 测量详情
     *
     * @param id 测量 ID
     * @return 测量 VO（剥离 tenantId/providerTraceId/deleted）
     */
    @Operation(summary = "详情")
    @PrePermission("execution:evm:list")
    @GetMapping("/{id}")
    public Result<EvmMeasureVO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 按项目查询 EVM 测量列表
     *
     * @param initiationId 项目立项 ID
     * @return 测量 VO 列表
     */
    @Operation(summary = "按项目查询")
    @PrePermission("execution:evm:list")
    @GetMapping("/by-initiation")
    public Result<List<EvmMeasureVO>> listByInitiation(@RequestParam String initiationId) {
        return Result.ok(service.listByInitiation(initiationId));
    }

    /**
     * 按 WBS 任务查询 EVM 测量列表
     *
     * @param wbsTaskId WBS 任务 ID
     * @return 测量 VO 列表
     */
    @Operation(summary = "按 WBS 查询")
    @PrePermission("execution:evm:list")
    @GetMapping("/by-wbs")
    public Result<List<EvmMeasureVO>> listByWbs(@RequestParam String wbsTaskId) {
        return Result.ok(service.listByWbs(wbsTaskId));
    }

    /**
     * 查询项目偏差趋势（按周期）
     *
     * @param initiationId 项目立项 ID
     * @return 趋势数据列表
     */
    @Operation(summary = "项目偏差趋势（按周期）")
    @PrePermission("execution:evm:list")
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam String initiationId) {
        return Result.ok(service.trend(initiationId));
    }

    /**
     * 查询项目 EVM 健康仪表盘
     *
     * @param initiationId 项目立项 ID
     * @return 仪表盘数据
     */
    @Operation(summary = "项目 EVM 健康仪表盘")
    @PrePermission("execution:evm:dashboard")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@RequestParam String initiationId) {
        return Result.ok(service.dashboard(initiationId));
    }

    /**
     * 分页查询 EVM 测量
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param initiationId 项目立项 ID
     * @param alertLevel   预警等级过滤
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @PrePermission("execution:evm:list")
    @GetMapping("/page")
    public Result<Page<EvmMeasureVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String alertLevel) {
        return Result.ok(service.page(page, size, initiationId, alertLevel));
    }

    /**
     * 删除 EVM 测量
     *
     * @param id 测量 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @PrePermission("execution:evm:delete")
    @OperationLog(module = "挣值管理", action = "删除EVM测量", bizType = "EVM_MEASURE")
    @Idempotent(key = "evm:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }
}

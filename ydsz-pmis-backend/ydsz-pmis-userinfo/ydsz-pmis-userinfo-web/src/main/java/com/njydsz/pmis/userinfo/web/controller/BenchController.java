package com.njydsz.pmis.userinfo.web.controller.resource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.userinfo.domain.dto.resource.BenchRecordCreateDTO;
import com.njydsz.pmis.userinfo.domain.entity.resource.BenchRecordDO;
import com.njydsz.pmis.userinfo.server.service.impl.resource.BenchServiceImpl;
import com.njydsz.pmis.userinfo.server.service.resource.BenchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Bench 闲置池 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "Bench 闲置池管理")
@RestController
@RequestMapping("/bench")
@RequiredArgsConstructor
@Validated
public class BenchController {

    /** 闲置池服务 */
    private final BenchService benchService;

    /**
     * 入池 / 出池 业务动作
     *
     * @param dto 入/出池请求参数
     * @return 统一响应结果，包含 Bench 记录 ID
     */
    @Operation(summary = "入池 / 出池 业务动作")
    @AuthApiPermission(apiCodes = "resource:bench:act")
    @OperationLog(module = "Bench 池", action = "入/出池", bizType = "BENCH_RECORD")
    @Idempotent(key = "bench:act", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/act")
    public BaseResponse<String> act(@Valid @RequestBody BenchRecordCreateDTO dto) {
        return BaseResponse.ok(benchService.act(dto));
    }

    /**
     * 查询 Bench 记录详情
     *
     * @param id Bench 记录 ID
     * @return 统一响应结果，包含 Bench 记录
     */
    @Operation(summary = "Bench 详情")
    @GetMapping("/{id}")
    public BaseResponse<BenchRecordDO> get(@PathVariable String id) {
        return BaseResponse.ok(benchService.getById(id));
    }

    /**
     * 查询员工当前的 Bench 记录
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含 Bench 记录
     */
    @Operation(summary = "员工当前 Bench 记录")
    @GetMapping("/active/{employeeId}")
    public BaseResponse<BenchRecordDO> getActiveByEmployee(@PathVariable String employeeId) {
        return BaseResponse.ok(benchService.getActiveByEmployee(employeeId));
    }

    /**
     * 按资源池汇总 Bench 记录
     *
     * @return 统一响应结果，包含按池汇总数据
     */
    @Operation(summary = "按池汇总")
    @GetMapping("/aggregate/byPool")
    public BaseResponse<List<Map<String, Object>>> aggregateByPool() {
        return BaseResponse.ok(benchService.aggregateByPool());
    }

    /**
     * 按日期区间统计入/出池流动
     *
     * @param from 起始日期（可选）
     * @param to   截止日期（可选）
     * @return 统一响应结果，包含流动统计数据
     */
    @Operation(summary = "流动统计（按日期区间）")
    @GetMapping("/flow")
    public BaseResponse<List<Map<String, Object>>> flowByDateRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return BaseResponse.ok(benchService.flowByDateRange(from, to));
    }

    /**
     * 分页查询 Bench 记录
     *
     * @param page   页码
     * @param size   每页大小
     * @param poolId 资源池 ID（可选）
     * @param status 状态（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public BaseResponse<Page<BenchRecordDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String poolId,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(benchService.page(page, size, poolId, status));
    }

    /**
     * 查询累计闲置成本
     *
     * @return 统一响应结果，包含累计闲置成本
     */
    @Operation(summary = "累计闲置成本")
    @GetMapping("/totalIdleCost")
    public BaseResponse<BigDecimal> totalIdleCost() {
        return BaseResponse.ok(benchService.totalIdleCost());
    }

    /**
     * Bench 仪表盘汇总
     *
     * @return 统一响应结果，包含仪表盘汇总数据
     */
    @Operation(summary = "Bench 仪表盘汇总")
    @GetMapping("/dashboard")
    public BaseResponse<Map<String, Object>> dashboard() {
        if (benchService instanceof BenchServiceImpl impl) {
            return BaseResponse.ok(impl.dashboard());
        }
        Map<String, Object> out = new HashMap<>();
        out.put("activePools", benchService.aggregateByPool());
        out.put("totalIdleCost", benchService.totalIdleCost());
        return BaseResponse.ok(out);
    }
}

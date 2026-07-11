package com.njydsz.pmis.project.controller.finance;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.finance.ProfitSnapshotDTO;
import com.njydsz.pmis.project.entity.finance.ProfitSnapshotDO;
import com.njydsz.pmis.project.service.finance.ProfitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
 * 利润核算 Controller
 *
 * <p>负责项目月度利润快照生成、查询、趋势分析及健康度评分。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "利润核算")
@RestController
@RequestMapping("/finance/profit")
@RequiredArgsConstructor
@Validated
public class ProfitController {

    /** 利润服务 */
    private final ProfitService service;

    /**
     * 生成/更新项目月度利润快照
     *
     * @param dto 利润快照参数
     * @return 快照 ID
     */
    @Operation(summary = "生成/更新项目月度利润快照")
    @PrePermission("execution:profit:snapshot")
    @Idempotent(key = "profit:snapshot", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/snapshot")
    public Result<String> snapshot(@Valid @RequestBody ProfitSnapshotDTO dto) {
        return Result.ok(service.generateSnapshot(dto));
    }

    /**
     * 查询项目某月快照
     *
     * @param initiationId 项目立项 ID
     * @param period       所属期间（YYYY-MM）
     * @return 利润快照实体
     */
    @Operation(summary = "查询项目某月快照")
    @PrePermission("execution:profit:list")
    @GetMapping("/snapshot")
    public Result<ProfitSnapshotDO> get(@RequestParam String initiationId, @RequestParam String period) {
        return Result.ok(service.getByInitiationAndPeriod(initiationId, period));
    }

    /**
     * 查询项目所有快照
     *
     * @param initiationId 项目立项 ID
     * @return 快照列表
     */
    @Operation(summary = "项目所有快照")
    @PrePermission("execution:profit:list")
    @GetMapping("/snapshots/{initiationId}")
    public Result<List<ProfitSnapshotDO>> list(@PathVariable String initiationId) {
        return Result.ok(service.listByInitiation(initiationId));
    }

    /**
     * 查询项目利润趋势
     *
     * @param initiationId 项目立项 ID
     * @return 趋势数据列表
     */
    @Operation(summary = "趋势")
    @PrePermission("execution:profit:list")
    @GetMapping("/trend/{initiationId}")
    public Result<List<Map<String, Object>>> trend(@PathVariable String initiationId) {
        return Result.ok(service.trendByPeriod(initiationId));
    }

    /**
     * 查询项目健康度评分
     *
     * @param initiationId 项目立项 ID
     * @param period       所属期间（YYYY-MM）
     * @return 健康度评分
     */
    @Operation(summary = "项目健康度评分")
    @PrePermission("execution:profit:list")
    @GetMapping("/healthScore")
    public Result<Integer> healthScore(@RequestParam String initiationId, @RequestParam String period) {
        return Result.ok(service.healthScore(initiationId, period));
    }
}

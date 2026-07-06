package com.njydsz.pmis.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.entity.FlowDmnTableDO;
import com.njydsz.pmis.workflow.service.FlowDmnTableService;
import com.njydsz.pmis.workflow.dto.DmnExecuteDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * DMN 决策表 HTTP API
 *
 * <p>P0-4: DMN 决策表引擎（对标 Camunda/Flowable DMN）。
 * 提供决策表的增删改查、发布与执行能力。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Tag(name = "DMN 决策表")
@RestController
@RequestMapping("/api/v1/workflow/dmn")
@RequiredArgsConstructor
@Validated
public class FlowDmnController {

    private final FlowDmnTableService dmnTableService;

    /**
     * 分页查询决策表
     *
     * @param pageNum   页码（从 1 开始，默认 1）
     * @param pageSize  每页大小（默认 20）
     * @param tableName 决策表名称模糊过滤（可空）
     * @return 分页结果
     */
    @Operation(summary = "分页查询决策表")
    @PostMapping("/page")
    public Result<Page<FlowDmnTableDO>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
                                             @RequestParam(required = false) String tableName) {
        return Result.ok(dmnTableService.page(pageNum, pageSize, tableName));
    }

    /**
     * 按 ID 获取决策表详情
     *
     * @param id 主键 ID
     * @return 决策表定义
     */
    @Operation(summary = "按 ID 获取决策表详情")
    @GetMapping("/{id}")
    public Result<FlowDmnTableDO> getById(@PathVariable @Min(1) Long id) {
        return Result.ok(dmnTableService.getById(id));
    }

    /**
     * 按 tableKey 获取决策表
     *
     * @param tableKey 决策表唯一标识
     * @return 决策表定义
     */
    @Operation(summary = "按 tableKey 获取决策表")
    @GetMapping("/key/{tableKey}")
    public Result<FlowDmnTableDO> getByKey(@PathVariable String tableKey) {
        return Result.ok(dmnTableService.getByKey(tableKey));
    }

    /**
     * 新建/更新决策表
     *
     * <p>body 中包含 id 则更新，否则新建。
     *
     * @param table 决策表定义
     * @return 新建返回主键 ID，更新返回 null
     */
    @Operation(summary = "新建/更新决策表")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody FlowDmnTableDO table) {
        if (table.getId() != null) {
            dmnTableService.update(table);
            return Result.ok();
        }
        return Result.ok(dmnTableService.save(table));
    }

    /**
     * 发布决策表
     *
     * @param id 主键 ID
     * @return 操作结果
     */
    @Operation(summary = "发布决策表")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable @Min(1) Long id) {
        dmnTableService.publish(id);
        return Result.ok();
    }

    /**
     * 执行决策
     *
     * <p>请求体示例：
     * <pre>{@code
     * {
     *   "tableKey": "risk_level",
     *   "context": { "amount": 15000, "level": "紧急" }
     * }
     * }</pre>
     *
     * @param body 请求体，包含 tableKey 与 context
     * @return 输出结果列表（每个匹配规则产生一组输出）
     */
    @Operation(summary = "执行决策")
    @PostMapping("/execute")
    public Result<List<Map<String, Object>>> execute(@Valid @RequestBody DmnExecuteDTO dto) {
        String tableKey = dto.getTableKey();
        Map<String, Object> context = dto.getContext();
        return Result.ok(dmnTableService.execute(tableKey, context));
    }
}

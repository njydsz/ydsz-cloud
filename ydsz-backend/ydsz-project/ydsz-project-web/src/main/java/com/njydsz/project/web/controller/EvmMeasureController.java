package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.evm.EvmMeasure;
import com.njydsz.project.server.service.EvmMeasureService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.EvmMeasureVO;
import com.njydsz.project.domain.dto.post.EvmMeasurePostDTO;
import com.njydsz.project.domain.dto.put.EvmMeasurePutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * EVM 挣值测量 Controller。
 *
 * <p>提供挣值管理（EVM）测量记录的 REST API，是「项目管理 / 挣值管理 / 项目绩效度量」业务域的核心 Controller。
 * 对标大厂 PMIS / 项目管理系统（如 Primavera P6 / Microsoft Project）中的「EVM（Earned Value Management）/
 * 挣值分析 / 绩效度量 / 趋势预测」管理界面。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>三大基础值：PV（计划值 Planned Value）/ EV（挣值 Earned Value）/ AC（实际成本 Actual Cost）</li>
 *   <li>四大绩效指标：SV / CV（偏差）/ SPI / CPI（绩效指数）</li>
 *   <li>趋势预测：EAC（完工估算）/ ETC（完工尚需估算）/ VAC（完工偏差）</li>
 *   <li>多周期快照：按周 / 月维度滚动计算，提供趋势对比</li>
 *   <li>挣值曲线：S 曲线（{@code EarnedValueCurve}）展示 PV / EV / AC 走势</li>
 * </ul>
 *
 * <h3>关键计算公式</h3>
 * <pre>
 *   SV = EV - PV          （进度偏差；SV &gt; 0 提前，SV &lt; 0 滞后）
 *   CV = EV - AC          （成本偏差；CV &gt; 0 节约，CV &lt; 0 超支）
 *   SPI = EV / PV         （进度绩效指数；SPI ≥ 1.0 正常）
 *   CPI = EV / AC         （成本绩效指数；CPI ≥ 1.0 正常）
 *   EAC = BAC / CPI       （完工估算；基于当前 CPI 推算总成本）
 *   ETC = EAC - AC        （完工尚需估算）
 *   VAC = BAC - EAC       （完工偏差；正数表示预算节约）
 * </pre>
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>已锁定（{@code status=LOCKED}）的 EVM 测量记录<b>严禁</b>直接修改</li>
 *   <li>PV / EV / AC 三值<b>必须</b>由系统计算，禁止手工录入（防止数据失真）</li>
 *   <li>同一项目同一周期<b>只能存在一条</b>有效测量记录（唯一索引保证）</li>
 *   <li>EVM 数据是项目绩效评估和趋势预测的依据，禁止越权篡改</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计，写操作落 {@code ydsz_operation_log}</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制（PMO / PM / 销售总监可见）</li>
 *   <li>EVM 计算为高耗时操作，加分布式锁（{@code ydsz:project:evm:calculate:lock}）防并发</li>
 *   <li>趋势预测结果存缓存（{@code ydsz:project:evm:trend:cache:projectId}），TTL 1h</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   GET    /api/v1/project/evm/measure/{id}   - 按 ID 查询
 *   GET    /api/v1/project/evm/measure/page   - 分页查询
 *   POST   /api/v1/project/evm/measure        - 创建测量记录
 *   PUT    /api/v1/project/evm/measure        - 更新测量记录
 *   DELETE /api/v1/project/evm/measure/{id}   - 删除测量记录
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-project-web (本 Controller)
 *                                          ↓
 *                              ydsz-project-server.EvmMeasureService
 *                              ydsz-project-server.EvmCalculateJob (周维度滚动)
 *                              ydsz-project-server.EvmTrendService (趋势预测)
 *                                          ↓
 *                              ydsz-project-infra.EvmMeasureMapper
 *                              ydsz-project-infra.EvmCurveCache (Redis 缓存)
 *                                          ↓
 *                              ydsz_evm_measure
 *                              ydsz_execution_wbs_task (WBS 进度源)
 *                              ydsz_cost_allocation (实际成本源)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.EvmMeasureService EVM Service
 * @see com.njydsz.project.domain.entity.evm.EvmMeasure EVM 测量实体
 * @see ExecutionWbsTaskController WBS 任务 Controller（PV / EV 数据源）
 * @see CostAllocationController 成本归集 Controller（AC 数据源）
 */
@RestController
@RequestMapping("/api/v1/project/evm/measure")
@RequiredArgsConstructor
public class EvmMeasureController {

    private final EvmMeasureService service;

    /**
     * 按 ID 查询挣值测量。
     *
     * <p>返回测量实体 + 富化的项目名称 / 期间 / 责任人等外键字段 + 自动计算的偏差指标。
     *
     * @param id 测量记录主键 ID
     * @return 测量记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<EvmMeasureVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询挣值测量列表。
     *
     * <p>支持按项目、期间、状态等条件筛选；按期间倒序返回。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页测量记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<EvmMeasureVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<EvmMeasure> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.evmMeasureListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建挣值测量（手动触发）。
     *
     * <p>通常由定时任务（{@code EvmCalculateJob}，每周一凌晨）自动调用；本接口用于「数据修正」场景下手动触发。
     * 创建时自动：
     * <ol>
     *   <li>按项目 ID 聚合 WBS 进度 → 计算 EV</li>
     *   <li>按项目 ID 聚合已审批计划工作量 → 计算 PV</li>
     *   <li>按项目 ID 聚合成本归集 → 计算 AC</li>
     *   <li>计算 SV / CV / SPI / CPI / EAC / ETC / VAC 等衍生指标</li>
     * </ol>
     *
     * @param dto 测量记录创建入参（项目 ID、测量周期、BAC 预算等）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:EvmMeasureController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create EvmMeasure")
    public BaseResponse<Boolean> save(@RequestBody EvmMeasurePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新挣值测量。
     *
     * <p>已锁定（{@code status=LOCKED}）的测量记录<b>严禁</b>修改；错录处理：通过「作废 + 重生成」流程实现。
     *
     * @param dto 测量记录更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:EvmMeasureController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update EvmMeasure")
    public BaseResponse<Boolean> update(@RequestBody EvmMeasurePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除挣值测量。
     *
     * <p>采用<b>逻辑删除</b>；已锁定或被趋势预测引用的测量记录<b>严禁</b>删除。
     *
     * @param id 测量记录主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:EvmMeasureController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete EvmMeasure")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

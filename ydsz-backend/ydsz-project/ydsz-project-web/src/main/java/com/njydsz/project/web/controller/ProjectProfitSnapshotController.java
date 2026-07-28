package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;
import com.njydsz.project.server.service.ProjectProfitSnapshotService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectProfitSnapshotVO;
import com.njydsz.project.domain.dto.put.ProjectProfitSnapshotPutDTO;
import com.njydsz.project.domain.dto.post.ProjectProfitSnapshotPostDTO;

/**
 * 项目利润快照 Controller。
 *
 * <p>提供项目利润快照的 REST API，是「项目管理 / 经营分析 / 利润管理」业务域的核心 Controller。
 * 对标大厂 PMIS / 经营管理系统中的「项目利润 / 利润率分析 / 利润趋势 / 经营驾驶舱」管理界面。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>利润快照：定时任务（{@code ProfitSnapshotJob}，每日凌晨）按项目维度生成「毛利 / 净利 / 利润率」快照</li>
 *   <li>多维度分析：按项目类型、客户、行业、PM、合同类型等维度聚合利润</li>
 *   <li>趋势对比：本期 vs 上期、本期 vs 同期（去年同月），计算利润同比 / 环比</li>
 *   <li>数据修正：财务月末结账后支持手动重算指定月份的利润快照</li>
 * </ul>
 *
 * <h3>利润计算公式</h3>
 * <pre>
 *   毛利润 = 累计回款 - 累计成本（人力 + 采购 + 费用 + 外包 + 其他）
 *   净利润 = 毛利润 - 项目分摊管理费 - 税金
 *   毛利率 = 毛利润 / 累计收入 × 100%
 * </pre>
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>快照生成后<b>严禁</b>直接修改（{@code status=LOCKED}），如需修正只能「作废 + 重生成」</li>
 *   <li>同一项目同一日期<b>只能存在一条</b>有效快照，由 Service 层加唯一索引保证</li>
 *   <li>快照依赖的收入 / 成本 / 回款数据若发生回溯调整，需触发「重算链路」</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计，写操作落 {@code ydsz_operation_log}</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制（销售总监 / 财务可见全量）</li>
 *   <li>快照重算为高耗时操作，加分布式锁（{@code ydsz:project:profit:snapshot:lock}）防并发</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   GET    /api/v1/project/project/profit/snapshot/{id}      - 按 ID 查询
 *   GET    /api/v1/project/project/profit/snapshot/page      - 分页查询
 *   POST   /api/v1/project/project/profit/snapshot           - 创建快照（手动）
 *   PUT    /api/v1/project/project/profit/snapshot           - 更新快照（仅限未锁定）
 *   DELETE /api/v1/project/project/profit/snapshot/{id}      - 删除快照
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-project-web (本 Controller)
 *                                          ↓
 *                              ydsz-project-server.ProjectProfitSnapshotService
 *                              ydsz-project-server.ProfitSnapshotJob (定时任务)
 *                                          ↓
 *                              ydsz-project-infra.ProjectProfitSnapshotMapper
 *                                          ↓
 *                              ydsz_profit_snapshot
 *                              ydsz_project_revenue (收入聚合源)
 *                              ydsz_cost_allocation (成本聚合源)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectProfitSnapshotService 利润快照 Service
 * @see com.njydsz.project.domain.entity.project.ProjectProfitSnapshot 利润快照实体
 */
@RestController
@RequestMapping("/api/v1/project/project/profit/snapshot")
@RequiredArgsConstructor
public class ProjectProfitSnapshotController {

    private final ProjectProfitSnapshotService service;

    /**
     * 按 ID 查询利润快照。
     *
     * <p>返回快照实体 + 富化的项目名称 / 客户名称 / PM 等外键字段。
     *
     * @param id 利润快照主键 ID
     * @return 利润快照视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectProfitSnapshotVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询利润快照列表。
     *
     * <p>支持按项目、客户、快照月份、利润率区间等条件筛选；按快照日期降序返回。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页利润快照视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectProfitSnapshotVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectProfitSnapshot> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectProfitSnapshotListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建利润快照（手动触发）。
     *
     * <p>通常由 {@code ProfitSnapshotJob} 定时任务自动调用；本接口用于「数据修正」场景下手动重算。
     * 创建时自动：
     * <ol>
     *   <li>按项目 ID 聚合当日 / 当月收入、成本、回款</li>
     *   <li>计算毛利润 / 净利润 / 毛利率</li>
     *   <li>检查唯一约束（项目 ID + 快照日期）</li>
     * </ol>
     *
     * @param dto 利润快照创建入参（项目 ID、快照日期、收入、成本等）
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectProfitSnapshot")
    public BaseResponse<Boolean> save(@RequestBody ProjectProfitSnapshotPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新利润快照。
     *
     * <p>仅允许更新「未锁定（{@code status=ACTIVE}）」状态的快照；已锁定的快照<b>严禁</b>修改。
     * 错票处理：通过「作废 + 重生成」流程实现。
     *
     * @param dto 利润快照更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectProfitSnapshot")
    public BaseResponse<Boolean> update(@RequestBody ProjectProfitSnapshotPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除利润快照。
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除「未锁定」状态的快照；已锁定的快照<b>严禁</b>删除。
     *
     * @param id 利润快照主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectProfitSnapshot")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

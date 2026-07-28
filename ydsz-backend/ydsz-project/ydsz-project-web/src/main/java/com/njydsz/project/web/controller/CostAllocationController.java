package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.cost.CostAllocation;
import com.njydsz.project.server.service.CostAllocationService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.CostAllocationVO;
import com.njydsz.project.domain.dto.post.CostAllocationPostDTO;
import com.njydsz.project.domain.dto.put.CostAllocationPutDTO;

/**
 * 成本分摊 Controller
 *
 * <p>提供成本分摊记录的 REST API，是「项目管理 / 财务成本归集」业务域的 Controller。
 * 对标大厂 PMIS / ERP 系统中的「项目成本归集 / 成本中心核算 / 期间成本结转」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>分摊维度：</b>按 {@code period × initiationId × costCategory} 维度归集项目成本。
 *
 * <p><b>成本类别：</b>LABOR 人力 / PURCHASE 采购 / EXPENSE 费用 / OUTSOURCE 外包 / OTHER 其他。
 *
 * <p><b>典型调用方：</b>定时任务（每月 1 号凌晨滚动归集上月成本）。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>作为 {@code ydsz_project_profit_snapshot} 利润快照的输入数据，禁止越权篡改</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.CostAllocationService 成本归集 Service
 * @see com.njydsz.project.domain.entity.cost.CostAllocation 成本归集体
 */
@RestController
@RequestMapping("/api/v1/project/cost/allocation")
@RequiredArgsConstructor
public class CostAllocationController {

    private final CostAllocationService service;

    /**
     * 按 ID 查询成本分摊
     *
     * @param id 分摊记录主键 ID
     * @return 分摊记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<CostAllocationVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询成本分摊列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页分摊记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<CostAllocationVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<CostAllocation> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.costAllocationListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建成本分摊
     *
     * @param dto 分摊记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create CostAllocation")
    public BaseResponse<Boolean> save(@RequestBody CostAllocationPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新成本分摊
     *
     * @param dto 分摊记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update CostAllocation")
    public BaseResponse<Boolean> update(@RequestBody CostAllocationPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除成本分摊
     *
     * @param id 分摊记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete CostAllocation")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

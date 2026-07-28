package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectBudgetItem;
import com.njydsz.project.server.service.ProjectBudgetItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectBudgetItemVO;
import com.njydsz.project.domain.dto.put.ProjectBudgetItemPutDTO;
import com.njydsz.project.domain.dto.post.ProjectBudgetItemPostDTO;

/**
 * 立项预算明细 Controller
 *
 * <p>提供项目立项预算明细的 REST API，是「项目管理 / 预算管控」业务域的 Controller。
 * 对标大厂 PMIS / ERP 系统的「项目预算 / 成本预算 / 预算控制」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>预算分类：</b>按 5 大类别（LABOR 人力 / PURCHASE 采购 / EXPENSE 费用 /
 * OUTSOURCE 外包 / OTHER 其他）拆解预算。
 *
 * <p><b>预算占用预警：</b>与实际成本表联动，触发「预算占用率 80% 黄灯 / 95% 红灯」预警规则。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>预算变更需走项目变更流程</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectBudgetItemService 预算 Service
 * @see com.njydsz.project.domain.entity.project.ProjectBudgetItem 预算明细实体
 */
@RestController
@RequestMapping("/api/v1/project/project/budget/item")
@RequiredArgsConstructor
public class ProjectBudgetItemController {

    private final ProjectBudgetItemService service;

    /**
     * 按 ID 查询预算明细
     *
     * @param id 预算明细主键 ID
     * @return 预算明细视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectBudgetItemVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询预算明细列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页预算明细视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectBudgetItemVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectBudgetItem> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectBudgetItemListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建预算明细
     *
     * @param dto 预算明细创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectBudgetItem")
    public BaseResponse<Boolean> save(@RequestBody ProjectBudgetItemPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新预算明细
     *
     * @param dto 预算明细更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectBudgetItem")
    public BaseResponse<Boolean> update(@RequestBody ProjectBudgetItemPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除预算明细
     *
     * @param id 预算明细主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectBudgetItem")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

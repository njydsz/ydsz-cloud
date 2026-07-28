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
 * <p>提供项目立项预算明细的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
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

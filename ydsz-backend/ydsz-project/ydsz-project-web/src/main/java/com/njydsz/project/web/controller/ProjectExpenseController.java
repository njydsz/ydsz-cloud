package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectExpense;
import com.njydsz.project.server.service.ProjectExpenseService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectExpenseVO;
import com.njydsz.project.domain.dto.put.ProjectExpensePutDTO;
import com.njydsz.project.domain.dto.post.ProjectExpensePostDTO;

/**
 * 项目费用 Controller
 *
 * <p>提供项目费用的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/expense")
@RequiredArgsConstructor
public class ProjectExpenseController {

    private final ProjectExpenseService service;

    /**
     * 按 ID 查询费用详情
     *
     * @param id 费用主键 ID
     * @return 费用视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectExpenseVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询费用列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页费用视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectExpenseVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectExpense> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectExpenseListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建费用
     *
     * @param dto 费用创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectExpense")
    public BaseResponse<Boolean> save(@RequestBody ProjectExpensePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新费用
     *
     * @param dto 费用更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectExpense")
    public BaseResponse<Boolean> update(@RequestBody ProjectExpensePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除费用
     *
     * @param id 费用主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectExpense")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

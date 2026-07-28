package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContract;
import com.njydsz.project.server.service.ProjectContractService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractVO;
import com.njydsz.project.domain.dto.post.ProjectContractPostDTO;
import com.njydsz.project.domain.dto.put.ProjectContractPutDTO;

/**
 * 项目合同 Controller
 *
 * <p>提供项目合同的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/contract")
@RequiredArgsConstructor
public class ProjectContractController {

    private final ProjectContractService service;

    /**
     * 按 ID 查询合同详情
     *
     * @param id 合同主键 ID
     * @return 合同视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectContractVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询合同列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页合同视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectContractVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContract> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建合同
     *
     * @param dto 合同创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContract")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新合同
     *
     * @param dto 合同更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContract")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除合同
     *
     * @param id 合同主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContract")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

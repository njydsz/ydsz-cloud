package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContractTemplate;
import com.njydsz.project.server.service.ProjectContractTemplateService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractTemplateVO;
import com.njydsz.project.domain.dto.post.ProjectContractTemplatePostDTO;
import com.njydsz.project.domain.dto.put.ProjectContractTemplatePutDTO;

/**
 * 合同模板 Controller
 *
 * <p>提供合同模板的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/contract/template")
@RequiredArgsConstructor
public class ProjectContractTemplateController {

    private final ProjectContractTemplateService service;

    /**
     * 按 ID 查询合同模板
     *
     * @param id 合同模板主键 ID
     * @return 合同模板视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectContractTemplateVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询合同模板列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页合同模板视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectContractTemplateVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContractTemplate> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractTemplateListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建合同模板
     *
     * @param dto 合同模板创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContractTemplate")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractTemplatePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新合同模板
     *
     * @param dto 合同模板更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContractTemplate")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractTemplatePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除合同模板
     *
     * @param id 合同模板主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContractTemplate")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContractTemplate;
import com.njydsz.project.server.service.ProjectContractTemplateService;

import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractTemplateVO;
import com.njydsz.project.domain.dto.post.ProjectContractTemplatePostDTO;
import com.njydsz.project.domain.dto.put.ProjectContractTemplatePutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 合同模板 Controller
 *
 * <p>提供项目合同模板的 REST API，是「项目管理 / 合同模板管理」业务域的 Controller。
 * 对标大厂 PMIS / 法务系统中的「合同模板 / 合同范本 / 标准合同」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>模板分类：</b>按行业 / 客户类型 / 业务场景分类的标准合同模板。
 *
 * <p><b>模板版本：</b>同一类合同模板支持多版本（如 V1.0 / V1.1 / V2.0）。
 *
 * <p><b>模板审批：</b>新模板上线前需经法务 / 业务部门审批。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>模板审批通过后才可启用，由 {@code ydsz-workflow} 流程引擎驱动</li>
 *   <li>已签合同使用的模板<b>严禁</b>删除</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectContractTemplateService 合同模板 Service
 * @see com.njydsz.project.domain.entity.project.ProjectContractTemplate 合同模板实体
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
    public PageResponse<List<ProjectContractTemplateVO>> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContractTemplate> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractTemplateListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建合同模板
     *
     * @param dto 合同模板创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectContractTemplateController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContractTemplate")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractTemplatePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新合同模板
     *
     * @param dto 合同模板更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectContractTemplateController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContractTemplate")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractTemplatePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除合同模板
     *
     * @param id 合同模板主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectContractTemplateController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContractTemplate")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

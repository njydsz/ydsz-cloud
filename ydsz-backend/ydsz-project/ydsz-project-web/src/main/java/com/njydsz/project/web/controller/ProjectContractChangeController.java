package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContractChange;
import com.njydsz.project.server.service.ProjectContractChangeService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractChangeVO;
import com.njydsz.project.domain.dto.put.ProjectContractChangePutDTO;
import com.njydsz.project.domain.dto.post.ProjectContractChangePostDTO;

/**
 * 合同变更记录 Controller
 *
 * <p>提供项目合同变更的 REST API，是「项目管理 / 合同变更」业务域的 Controller。
 * 对标大厂 PMIS / 法务系统中的「合同变更 / 合同修改」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>变更范围：</b>已签订合同的关键字段（金额 / 范围 / 工期）变更必须走变更流程。
 *
 * <p><b>审批集成：</b>每条变更记录对应一个 {@code ydsz-workflow} 流程实例，
 * 审批通过后自动同步到原合同。
 *
 * <p><b>审计链：</b>保留完整变更历史（变更前 / 变更后 / 变更原因 / 变更人 / 变更时间）。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>合同变更审批需法务 / 业务 / 财务联签</li>
 *   <li>变更记录是合同审计的法定依据，禁止越权篡改</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectContractChangeService 合同变更 Service
 * @see com.njydsz.project.domain.entity.project.ProjectContractChange 合同变更实体
 * @see ProjectContractController 主合同 Controller
 */
@RestController
@RequestMapping("/api/v1/project/project/contract/change")
@RequiredArgsConstructor
public class ProjectContractChangeController {

    private final ProjectContractChangeService service;

    /**
     * 按 ID 查询合同变更记录
     *
     * @param id 变更记录主键 ID
     * @return 变更记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectContractChangeVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询合同变更记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页变更记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectContractChangeVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContractChange> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractChangeListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建合同变更记录
     *
     * @param dto 变更记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContractChange")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractChangePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新合同变更记录
     *
     * @param dto 变更记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContractChange")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractChangePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除合同变更记录
     *
     * @param id 变更记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContractChange")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

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
 * <p>提供项目合同的 REST API，是「项目管理 / 合同环节」的核心 Controller。
 * 对标大厂 PMIS / 法务系统中的「销售合同 / 服务合同 / 采购合同」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>合同多版本：</b>通过 {@link ProjectContractChangeController} 维护合同变更，
 * 保留完整审计链。
 *
 * <p><b>合同附件：</b>通过 {@link ProjectContractSupplementController} 维护合同附件和补充协议。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>已签订合同（{@code status=SIGNED}）的关键字段（金额 / 工期 / 范围）<b>严禁</b>直接修改，
 *       必须通过 {@link ProjectContractChangeController} 走「合同变更」流程</li>
 *   <li>采用<b>逻辑删除</b>，合同一旦签订<b>严禁</b>物理删除</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectContractService 合同 Service
 * @see com.njydsz.project.domain.entity.project.ProjectContract 合同实体
 * @see ProjectContractChangeController 合同变更 Controller
 * @see ProjectContractSupplementController 合同附件 Controller
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

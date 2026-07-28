package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContractSupplement;
import com.njydsz.project.server.service.ProjectContractSupplementService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractSupplementVO;
import com.njydsz.project.domain.dto.post.ProjectContractSupplementPostDTO;
import com.njydsz.project.domain.dto.put.ProjectContractSupplementPutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 合同补充协议 Controller
 *
 * <p>提供项目合同附件 / 补充协议的 REST API，是「项目管理 / 合同附件管理」业务域的 Controller。
 * 对标大厂 PMIS / 法务系统中的「合同附件 / 补充协议 / 备忘录」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>附件类型：</b>合同正本扫描件 / 附件 / 补充协议 / 备忘录 / 技术规格书。
 *
 * <p><b>文件存储：</b>附件文件统一上传到 {@code ydsz-common-file}，本表只存储元数据。
 *
 * <p><b>版本追踪：</b>同一类附件可上传多个版本，通过 {@code version} 字段管理。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>附件是合同的法律文件，下载 / 查看需权限控制</li>
 *   <li>附件版本变更不可删除历史版本</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectContractSupplementService 合同附件 Service
 * @see com.njydsz.project.domain.entity.project.ProjectContractSupplement 合同附件实体
 * @see ProjectContractController 主合同 Controller
 */
@RestController
@RequestMapping("/api/v1/project/project/contract/supplement")
@RequiredArgsConstructor
public class ProjectContractSupplementController {

    private final ProjectContractSupplementService service;

    /**
     * 按 ID 查询补充协议
     *
     * @param id 补充协议主键 ID
     * @return 补充协议视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectContractSupplementVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询补充协议列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页补充协议视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectContractSupplementVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContractSupplement> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractSupplementListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建补充协议
     *
     * @param dto 补充协议创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectContractSupplementController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContractSupplement")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractSupplementPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新补充协议
     *
     * @param dto 补充协议更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectContractSupplementController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContractSupplement")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractSupplementPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除补充协议
     *
     * @param id 补充协议主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectContractSupplementController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContractSupplement")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

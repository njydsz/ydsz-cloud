package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;
import com.njydsz.project.server.service.ExecutionDeliveryStandardService;

import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionDeliveryStandardVO;
import com.njydsz.project.domain.dto.post.ExecutionDeliveryStandardPostDTO;
import com.njydsz.project.domain.dto.put.ExecutionDeliveryStandardPutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 交付标准 Controller
 *
 * <p>提供项目交付物标准 / 模板的 REST API，是「项目管理 / 交付物标准管理」业务域的 Controller。
 * 对标大厂 PMIS / 项目管理系统中的「交付物标准 / 交付物模板 / 交付物规范」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>模板分类：</b>按项目类型 / 级别 / 行业分类的标准交付物清单。
 *
 * <p><b>必交付控制：</b>{@code required} 字段控制每个交付物是否必交付；
 * {@code triggerTr} 字段控制交付物是否触发技术评审（TR）。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>新模板上线前需经法务 / 业务部门审批，由 {@code ydsz-workflow} 流程引擎驱动</li>
 *   <li>模板是组织级知识资产，禁止越权篡改</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ExecutionDeliveryStandardService 交付标准 Service
 * @see com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard 交付标准实体
 */
@RestController
@RequestMapping("/api/v1/project/execution/delivery/standard")
@RequiredArgsConstructor
public class ExecutionDeliveryStandardController {

    private final ExecutionDeliveryStandardService service;

    /**
     * 按 ID 查询交付标准
     *
     * @param id 交付标准主键 ID
     * @return 交付标准视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ExecutionDeliveryStandardVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询交付标准列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页交付标准视图对象
     */
    @GetMapping("/page")
    public PageResponse<List<ExecutionDeliveryStandardVO>> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionDeliveryStandard> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionDeliveryStandardListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建交付标准
     *
     * @param dto 交付标准创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ExecutionDeliveryStandardController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionDeliveryStandard")
    public BaseResponse<Boolean> save(@RequestBody ExecutionDeliveryStandardPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新交付标准
     *
     * @param dto 交付标准更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ExecutionDeliveryStandardController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionDeliveryStandard")
    public BaseResponse<Boolean> update(@RequestBody ExecutionDeliveryStandardPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除交付标准
     *
     * @param id 交付标准主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ExecutionDeliveryStandardController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionDeliveryStandard")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

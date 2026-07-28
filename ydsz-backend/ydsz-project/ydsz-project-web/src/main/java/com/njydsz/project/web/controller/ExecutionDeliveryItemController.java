package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;
import com.njydsz.project.server.service.ExecutionDeliveryItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionDeliveryItemVO;
import com.njydsz.project.domain.dto.put.ExecutionDeliveryItemPutDTO;
import com.njydsz.project.domain.dto.post.ExecutionDeliveryItemPostDTO;

/**
 * 交付项 Controller
 *
 * <p>提供项目交付物实例的 REST API，是「项目管理 / 交付物管理」业务域的 Controller。
 * 对标大厂 PMIS / 项目管理系统中的「项目交付物 / 交付物清单 / 交付物验收」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>交付物实例化：</b>从 {@code ydsz_execution_delivery_standard} 标准模板生成交付物清单，
 * 跟踪计划提交日期 / 实际提交日期 / 验收日期。
 *
 * <p><b>典型调用方：</b>立项初始化（从标准模板批量生成）。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>交付物是合同履约的依据，状态变更需走审批</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ExecutionDeliveryItemService 交付物 Service
 * @see com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem 交付物实体
 */
@RestController
@RequestMapping("/api/v1/project/execution/delivery/item")
@RequiredArgsConstructor
public class ExecutionDeliveryItemController {

    private final ExecutionDeliveryItemService service;

    /**
     * 按 ID 查询交付项
     *
     * @param id 交付项主键 ID
     * @return 交付项视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ExecutionDeliveryItemVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询交付项列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页交付项视图对象
     */
    @GetMapping("/page")
    public PageResponse<ExecutionDeliveryItemVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionDeliveryItem> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionDeliveryItemListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建交付项
     *
     * @param dto 交付项创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionDeliveryItem")
    public BaseResponse<Boolean> save(@RequestBody ExecutionDeliveryItemPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新交付项
     *
     * @param dto 交付项更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionDeliveryItem")
    public BaseResponse<Boolean> update(@RequestBody ExecutionDeliveryItemPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除交付项
     *
     * @param id 交付项主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionDeliveryItem")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.ops.OpsTicket;
import com.njydsz.project.server.service.OpsTicketService;

import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.OpsTicketVO;
import com.njydsz.project.domain.dto.post.OpsTicketPostDTO;
import com.njydsz.project.domain.dto.put.OpsTicketPutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 运维工单 Controller
 *
 * <p>提供运维工单的 REST API，是「项目管理 / 运维服务」业务域的 Controller。
 * 对标大厂 PMIS / 运维服务台系统中的「运维工单 / 客户服务工单 / 故障处理」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>工单类型：</b>INCIDENT 故障 / SERVICE_REQUEST 服务请求 / PROBLEM 问题。
 *
 * <p><b>SLA 跟踪：</b>首次响应 / 解决时间由 {@code ydsz_warranty} 质保期 SLA 约束。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>客户仅可创建 / 查看自己的工单，工程师仅可查看分派给自己的工单</li>
 *   <li>SLA 临近超时会自动触发告警派发</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.OpsTicketService 工单 Service
 * @see com.njydsz.project.domain.entity.ops.OpsTicket 工单实体
 */
@RestController
@RequestMapping("/api/v1/project/ops/ticket")
@RequiredArgsConstructor
public class OpsTicketController {

    private final OpsTicketService service;

    /**
     * 按 ID 查询运维工单
     *
     * @param id 工单主键 ID
     * @return 工单视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<OpsTicketVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询运维工单列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页工单视图对象
     */
    @GetMapping("/page")
    public PageResponse<List<OpsTicketVO>> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<OpsTicket> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.opsTicketListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建运维工单
     *
     * @param dto 工单创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:OpsTicketController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create OpsTicket")
    public BaseResponse<Boolean> save(@RequestBody OpsTicketPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新运维工单
     *
     * @param dto 工单更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:OpsTicketController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update OpsTicket")
    public BaseResponse<Boolean> update(@RequestBody OpsTicketPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除运维工单
     *
     * @param id 工单主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:OpsTicketController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete OpsTicket")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

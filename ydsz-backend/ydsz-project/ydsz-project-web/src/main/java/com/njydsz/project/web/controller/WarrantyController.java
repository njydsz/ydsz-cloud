package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.warranty.Warranty;
import com.njydsz.project.server.service.WarrantyService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.WarrantyVO;
import com.njydsz.project.domain.dto.post.WarrantyPostDTO;
import com.njydsz.project.domain.dto.put.WarrantyPutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 质保（Warranty）Controller
 *
 * <p>提供项目质保期管理的 REST API，是「项目管理 / 售后服务 / 客户支持」业务域的 Controller。
 * 对标大厂 PMIS / 客户支持系统中的「项目质保 / 缺陷修复 / 质保期台账」管理界面。
 *
 * <p><b>质保期阶段：</b>
 * <ul>
 *   <li><b>WARRANTY_ACTIVE</b>：质保期内（响应时间通常 4h / 24h / 72h）</li>
 *   <li><b>WARRANTY_EXPIRING</b>：即将到期（剩余 ≤ 30 天，触发续签提醒）</li>
 *   <li><b>WARRANTY_EXPIRED</b>：已过期（按合同约定可付费支持）</li>
 *   <li><b>EXTENDED</b>：已续签（独立记录）</li>
 * </ul>
 *
 * <p><b>业务能力：</b>
 * <ul>
 *   <li><b>质保工单</b>：客户提报缺陷 → 自动关联到 Warranty</li>
 *   <li><b>SLA 监控</b>：响应时间 / 解决时间 / 客户满意度</li>
 *   <li><b>质保金释放</b>：质保期满后释放合同保留金（{@code ProjectPayment.releaseRetention}）</li>
 *   <li><b>续签商机</b>：质保到期前 30 天自动生成续签商机（{@code ProjectOpportunityFollow}）</li>
 * </ul>
 *
 * <p><b>典型链路：</b>
 * <ol>
 *   <li>项目验收完成后自动创建质保记录（{@code ExecutionClosureService}）</li>
 *   <li>客户通过客服系统提报缺陷 → {@code OpsTicketService} 自动关联到 Warranty</li>
 *   <li>工程师处理缺陷 → 记录响应时间 / 解决时间</li>
 *   <li>质保期满 → 自动释放质保金 + 推送续签商机</li>
 * </ol>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>质保期 <b>不得</b> 短于合同约定的最低质保期</li>
 *   <li>已结案质保记录 <b>严禁</b> 物理删除（保留审计链）</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：项目 PM / 客户支持 / 客服 可见</li>
 *   <li>创建 / 更新：项目 PM / 客户支持</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/warranty")
@RequiredArgsConstructor
public class WarrantyController {

    private final WarrantyService service;

    /**
     * 按 ID 查询质保记录
     *
     * <p>返回质保记录 + 富化的项目名称 / 客户名称 / 关联合同编号等外键字段。
     *
     * @param id 质保记录主键 ID
     * @return 质保记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<WarrantyVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询质保记录
     *
     * <p>支持按项目、客户、质保期阶段、到期日期范围等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页质保记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<WarrantyVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<Warranty> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.warrantyListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建质保记录
     *
     * <p>通常由 {@code ExecutionClosureService} 在项目验收完成时自动创建，
     * 手工创建场景较少（合同补充协议 / 特殊定制项目）。
     *
     * @param dto 质保记录创建入参（项目 ID / 合同 ID / 质保期起止日期 / 响应 SLA）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:WarrantyController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create Warranty")
    public BaseResponse<Boolean> save(@RequestBody WarrantyPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新质保记录
     *
     * <p>仅允许在「WARRANTY_ACTIVE」状态下更新；到期或已续签的质保 <b>严禁</b> 修改。
     *
     * @param dto 质保记录更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:WarrantyController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update Warranty")
    public BaseResponse<Boolean> update(@RequestBody WarrantyPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除质保记录
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除「WARRANTY_ACTIVE」状态下未关联工单的质保记录；
     * 有关联工单 / 已结案的质保 <b>严禁</b> 删除。
     *
     * @param id 质保记录主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:WarrantyController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete Warranty")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

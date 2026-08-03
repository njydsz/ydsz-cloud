package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectOpportunity;
import com.njydsz.project.server.service.ProjectOpportunityService;

import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectOpportunityVO;
import com.njydsz.project.domain.dto.put.ProjectOpportunityPutDTO;
import com.njydsz.project.domain.dto.post.ProjectOpportunityPostDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 项目商机（销售机会）Controller
 *
 * <p>提供项目商机的 REST API，是「销售管理 / 项目立项」业务域的<b>入口</b> Controller 之一。
 * 对标大厂 PMIS / CRM（Salesforce / 销售易 / 纷享销客）系统中的「销售机会 / 项目商机 / 商机漏斗」管理界面。
 *
 * <p><b>商机阶段：</b>对应销售漏斗的典型阶段，按赢单概率从低到高排列。
 * <ul>
 *   <li><b>LEAD</b>：线索 / 初步接洽（赢率 5%）</li>
 *   <li><b>QUALIFIED</b>：需求确认 / 方案沟通（赢率 20%）</li>
 *   <li><b>PROPOSAL</b>：方案报价 / 商务谈判（赢率 40%）</li>
 *   <li><b>NEGOTIATION</b>：合同谈判 / 法务审核（赢率 60%）</li>
 *   <li><b>CLOSED_WON</b>：赢单 → 自动触发 {@code ProjectInitiationService} 创建预立项</li>
 *   <li><b>CLOSED_LOST</b>：输单 → 归档丢单原因分析</li>
 * </ul>
 *
 * <p><b>典型链路：</b>
 * <ol>
 *   <li>销售录入商机 → 调用 {@link #save} 创建商机记录</li>
 *   <li>商机阶段推进 → 各阶段触发跟进记录（{@link ProjectOpportunityFollowController}）</li>
 *   <li>商机赢单 → 调用 {@code ProjectInitiationService.createFromOpportunity} 转为预立项</li>
 *   <li>商机归档 → 关联项目台账，进入合同 / 执行阶段</li>
 * </ol>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>商机预计金额 / 预计毛利 <b>必须</b> 在「PROPOSAL」阶段前完成填写</li>
 *   <li>赢单后 <b>严禁</b> 修改商机核心字段（金额 / 客户 / 决策人）</li>
 *   <li>输单必须填写丢单原因，支持后续「丢单分析」报表</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：销售本人 / 销售总监 / PMO 可见</li>
 *   <li>创建 / 更新：销售本人可操作</li>
 *   <li>归档：销售总监 / 部门负责人</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/opportunity")
@RequiredArgsConstructor
public class ProjectOpportunityController {

    private final ProjectOpportunityService service;

    /**
     * 按 ID 查询商机详情
     *
     * <p>返回商机实体 + 富化的客户名称 / 决策人 / 跟进记录数 / 关联项目 ID 等字段。
     *
     * @param id 商机主键 ID
     * @return 商机视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectOpportunityVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询商机列表
     *
     * <p>支持按客户、销售、阶段、预计签约日期、金额范围等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页商机视图对象
     */
    @GetMapping("/page")
    public PageResponse<List<ProjectOpportunityVO>> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectOpportunity> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectOpportunityListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建商机
     *
     * <p>保存后自动：
     * <ol>
     *   <li>校验客户存在性（{@code OrgQueryClient.getCustomer}）</li>
     *   <li>触发商机分配规则（自动分配销售 / 销售支持）</li>
     *   <li>记录初始跟进记录</li>
     * </ol>
     *
     * @param dto 商机创建入参（客户 ID、预计金额、预计签约日期、阶段等）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectOpportunityController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectOpportunity")
    public BaseResponse<Boolean> save(@RequestBody ProjectOpportunityPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新商机
     *
     * <p>赢单 / 输单归档后 <b>严禁</b> 修改核心字段（由 Service 层校验）。
     *
     * @param dto 商机更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectOpportunityController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectOpportunity")
    public BaseResponse<Boolean> update(@RequestBody ProjectOpportunityPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除商机
     *
     * <p>采用<b>逻辑删除</b>；已赢单 / 已关联项目的商机 <b>严禁</b> 删除。
     *
     * @param id 商机主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectOpportunityController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectOpportunity")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

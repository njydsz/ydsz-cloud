package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectOpportunityFollow;
import com.njydsz.project.server.service.ProjectOpportunityFollowService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectOpportunityFollowVO;
import com.njydsz.project.domain.dto.put.ProjectOpportunityFollowPutDTO;
import com.njydsz.project.domain.dto.post.ProjectOpportunityFollowPostDTO;

/**
 * 商机跟进记录 Controller
 *
 * <p>提供项目商机跟进记录的 REST API，是「销售管理 / 商机运营」业务域的配套 Controller。
 * 对标大厂 PMIS / CRM 系统中的「商机跟进 / 客户拜访 / 沟通记录」管理界面。
 *
 * <p><b>跟进类型：</b>
 * <ul>
 *   <li><b>PHONE</b>：电话沟通（记录通话时长、关键议题、客户反馈）</li>
 *   <li><b>VISIT</b>：客户拜访（面对面，含签到照片 / 会议纪要）</li>
 *   <li><b>EMAIL</b>：邮件往来（关联邮件 ID）</li>
 *   <li><b>WECHAT</b>：微信沟通（截图归档）</li>
 *   <li><b>MEETING</b>：正式会议（议题 / 决议 / 参会人）</li>
 *   <li><b>OTHER</b>：其他形式</li>
 * </ul>
 *
 * <p><b>典型链路：</b>
 * <ol>
 *   <li>销售完成客户接触 → 调用 {@link #save} 记录跟进</li>
 *   <li>系统自动推送跟进待办（基于 {@code OpportunityFollowSchedule}）</li>
 *   <li>跟进频次不足时触发预警（{@code NotificationClient.sendFollowReminder}）</li>
 *   <li>支持按商机 / 时间范围 / 跟进人筛选</li>
 * </ol>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>每条跟进记录 <b>必须</b> 关联具体商机（{@code opportunityId}）</li>
 *   <li>跟进内容 <b>必须</b> ≥ 10 个字符，禁止「无内容占位」</li>
 *   <li>输单商机（{@code CLOSED_LOST}）的跟进记录保留为「输单复盘」依据</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：销售本人 / 销售总监 / PMO 可见</li>
 *   <li>创建 / 更新：销售本人可操作</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/opportunity/follow")
@RequiredArgsConstructor
public class ProjectOpportunityFollowController {

    private final ProjectOpportunityFollowService service;

    /**
     * 按 ID 查询跟进记录
     *
     * <p>返回跟进记录 + 富化的商机名称 / 跟进人姓名 / 客户名称等外键字段。
     *
     * @param id 跟进记录主键 ID
     * @return 跟进记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectOpportunityFollowVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询跟进记录
     *
     * <p>支持按商机、跟进人、跟进类型、跟进日期范围等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页跟进记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectOpportunityFollowVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectOpportunityFollow> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectOpportunityFollowListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建跟进记录
     *
     * <p>保存后自动：
     * <ol>
     *   <li>更新商机最后跟进时间（{@code opportunity.lastFollowAt}）</li>
     *   <li>触发跟进频次计算，决定下次跟进待办时间</li>
     *   <li>若连续 N 天无跟进，向销售推送提醒（{@code NotificationClient}）</li>
     * </ol>
     *
     * @param dto 跟进记录创建入参（商机 ID、跟进类型、内容、跟进时间等）
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectOpportunityFollow")
    public BaseResponse<Boolean> save(@RequestBody ProjectOpportunityFollowPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新跟进记录
     *
     * <p>仅允许更新本人创建的跟进；超过 24 小时的跟进记录 <b>严禁</b> 修改（审计要求）。
     *
     * @param dto 跟进记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectOpportunityFollow")
    public BaseResponse<Boolean> update(@RequestBody ProjectOpportunityFollowPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除跟进记录
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除本人 24 小时内创建的跟进；超期跟进 <b>严禁</b> 删除。
     *
     * @param id 跟进记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectOpportunityFollow")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

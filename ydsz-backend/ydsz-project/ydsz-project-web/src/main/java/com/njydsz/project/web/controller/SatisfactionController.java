package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.satisfaction.Satisfaction;
import com.njydsz.project.server.service.SatisfactionService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.SatisfactionVO;
import com.njydsz.project.domain.dto.put.SatisfactionPutDTO;
import com.njydsz.project.domain.dto.post.SatisfactionPostDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 客户满意度评价 Controller
 *
 * <p>提供项目客户满意度评价的 REST API，是「项目管理 / 客户满意度 / 项目复盘」业务域的 Controller。
 * 对标大厂 PMIS / 客户成功（CS）/ NPS 调研系统中的「客户满意度 / 项目验收评价 / NPS 打分」管理界面。
 *
 * <p><b>评价维度：</b>
 * <ul>
 *   <li><b>QUALITY</b>：交付质量（代码 / 文档 / 设计）</li>
 *   <li><b>TIMELINE</b>：交付时效（是否按期 / 提前 / 延期）</li>
 *   <li><b>COMMUNICATION</b>：沟通响应（PM / 团队响应速度）</li>
 *   <li><b>PROFESSIONAL</b>：专业能力（技术水平 / 问题解决能力）</li>
 *   <li><b>OVERALL</b>：综合满意度（1-5 星 / NPS 0-10）</li>
 * </ul>
 *
 * <p><b>评价时机：</b>
 * <ul>
 *   <li><b>MIDDLE</b>：项目中期评价（项目执行至 50% 时）</li>
 *   <li><b>ACCEPTANCE</b>：项目验收评价（项目验收完成后）</li>
 *   <li><b>WARRANTY</b>：质保期评价（质保期结束前 30 天）</li>
 *   <li><b>ADHOC</b>：临时评价（客户主动反馈）</li>
 * </ul>
 *
 * <p><b>典型链路：</b>
 * <ol>
 *   <li>系统自动触发评价 → 推送评价问卷（{@code NotificationClient.sendSurvey}）</li>
 *   <li>客户在线填写 → 调用 {@link #save} 保存评价</li>
 *   <li>低分评价（≤ 3 分）触发预警 → {@code AlertDispatchService.dispatch} 推送告警</li>
 *   <li>汇总分析 → 支撑 PM / 部门绩效考核</li>
 * </ol>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>每项目每次评价时机 <b>只能有一条</b> 评价记录（强制覆盖或拒绝）</li>
 *   <li>客户匿名评价支持（{@code isAnonymous} 字段）</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：项目 PM / 部门负责人 / PMO 可见</li>
 *   <li>创建：客户本人 / 客户对接人</li>
 *   <li>汇总：PMO / 部门负责人</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/satisfaction")
@RequiredArgsConstructor
public class SatisfactionController {

    private final SatisfactionService service;

    /**
     * 按 ID 查询满意度评价
     *
     * <p>返回评价实体 + 富化的项目名称 / 客户名称 / 评价人姓名等外键字段。
     *
     * @param id 评价主键 ID
     * @return 评价视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<SatisfactionVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询满意度评价
     *
     * <p>支持按项目、客户、评价时机、评分范围、评价日期等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页评价视图对象
     */
    @GetMapping("/page")
    public PageResponse<SatisfactionVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<Satisfaction> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.satisfactionListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建满意度评价
     *
     * <p>保存后自动：
     * <ol>
     *   <li>校验项目状态 = 验收中 / 质保中</li>
     *   <li>若评分 ≤ 3 分，自动触发告警（{@code AlertDispatchService}）</li>
     *   <li>计算项目综合得分，更新 PM 绩效池</li>
     * </ol>
     *
     * @param dto 评价创建入参（项目 ID / 评价时机 / 各维度评分 / 评语 / 是否匿名）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:SatisfactionController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create Satisfaction")
    public BaseResponse<Boolean> save(@RequestBody SatisfactionPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新满意度评价
     *
     * <p>提交后 24 小时内可修改，逾期需联系 PMO 走申诉流程。
     *
     * @param dto 评价更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:SatisfactionController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update Satisfaction")
    public BaseResponse<Boolean> update(@RequestBody SatisfactionPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除满意度评价
     *
     * <p>采用<b>逻辑删除</b>；仅允许 PMO 角色删除误填 / 重复评价；删除后 <b>必须</b> 同步清理绩效记录。
     *
     * @param id 评价主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:SatisfactionController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete Satisfaction")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

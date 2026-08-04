package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectGateReview;
import com.njydsz.project.server.service.ProjectGateReviewService;

import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectGateReviewVO;
import com.njydsz.project.domain.dto.put.ProjectGateReviewPutDTO;
import com.njydsz.project.domain.dto.post.ProjectGateReviewPostDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 门径评审（Gate Review）Controller
 *
 * <p>提供项目阶段门径评审的 REST API，是「项目管理 / 阶段门控」业务域的核心 Controller。
 * 对标大厂 PMIS / IPD（Integrated Product Development）流程管理体系中的「阶段门评审 / Stage-Gate」管理界面。
 *
 * <p><b>门径阶段：</b>项目生命周期中设置的若干关键评审节点，每个节点必须通过评审才能进入下一阶段。
 * <ul>
 *   <li><b>CDCP-0</b>：立项决策评审（Contract Decision Checkpoint 0），决定是否承接项目</li>
 *   <li><b>CDCP-1</b>：合同签订前的解决方案评审，验证交付方案的可行性</li>
 *   <li><b>PDCP</b>：计划决策评审（Plan Decision Checkpoint），确认 WBS / 资源 / 进度基线</li>
 *   <li><b>ADCP</b>：架构 / 设计决策评审（Architecture Decision Checkpoint），技术方案定型</li>
 *   <li><b>TDCP</b>：测试决策评审（Test Decision Checkpoint），验收测试通过性确认</li>
 *   <li><b>LDCP</b>：生命周期终止评审（Lifecycle Decision Checkpoint），决定项目关闭 / 暂停 / 终止</li>
 * </ul>
 *
 * <p><b>评审角色：</b>决策人（DM）、推荐人（RE）、执行人（PT）、被通知人（IN），参见 {@code GateReviewRoleEnum}。
 *
 * <p><b>典型链路：</b>
 * <ol>
 *   <li>PM 提交评审材料 → 调用 {@link #save} 发起评审</li>
 *   <li>评审材料分发 → 各评审角色在线查阅 + 投票</li>
 *   <li>评审结果汇总 → 通过 / 有条件通过 / 不通过</li>
 *   <li>评审通过 → 调用 {@code ProjectInitiationService.advanceStage} 推进项目阶段</li>
 * </ol>
 *
 * <p><b>安全约束：</b>
 * <ul>
 *   <li>评审结果一经归档 <b>严禁</b> 修改，保证审计链完整</li>
 *   <li>评审材料（PPT / 报告）必须上传到 {@link ProjectFileController} 关联存储</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/gate/review")
@RequiredArgsConstructor
public class ProjectGateReviewController {

    private final ProjectGateReviewService service;

    /**
     * 按 ID 查询门径评审记录
     *
     * <p>返回评审记录 + 评审人名单 / 投票结果 / 关联文件清单等富化字段。
     *
     * @param id 评审记录主键 ID
     * @return 评审记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectGateReviewVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询评审记录
     *
     * <p>支持按项目、评审阶段（CDCP-0/1/PDCP/ADCP/TDCP/LDCP）、评审结果筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页评审记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<List<ProjectGateReviewVO>> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectGateReview> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectGateReviewListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建门径评审
     *
     * <p>保存后自动通知评审人（{@code NotificationClient.sendGateReviewNotice}），
     * 并启动评审工作流。
     *
     * @param dto 评审记录创建入参（项目 ID、阶段、评审人清单、材料文件 ID 列表等）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectGateReviewController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectGateReview")
    public BaseResponse<Boolean> save(@RequestBody ProjectGateReviewPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新评审记录
     *
     * <p>仅允许在评审归档前更新结论 / 备注；归档后 <b>严禁</b> 修改（由 Service 层校验）。
     *
     * @param dto 评审记录更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectGateReviewController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectGateReview")
    public BaseResponse<Boolean> update(@RequestBody ProjectGateReviewPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除评审记录
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除「草稿 / 已驳回」状态的评审；归档后的评审 <b>严禁</b> 删除。
     *
     * @param id 评审记录主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectGateReviewController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectGateReview")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}

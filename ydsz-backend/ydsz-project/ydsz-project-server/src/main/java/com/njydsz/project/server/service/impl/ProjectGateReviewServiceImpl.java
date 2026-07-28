package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectGateReview;
import com.njydsz.project.domain.repository.project.IProjectGateReviewRepository;
import com.njydsz.project.server.service.ProjectGateReviewService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目门径评审（CDCP） Service 实现
 *
 * <p>对 {@link ProjectGateReviewService} 接口的完整实现，是「项目管理 / 阶段门管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_gate_review} 门径评审记录表，
 * 对标大厂 PMIS / 项目管理系统的「Stage-Gate（门径管理）/ CDCP（Cooperate Defined Check Point）/ 阶段评审」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>5 大门径评审</b>：CD1 启动评审 / CD2 设计评审 / CD3 建设评审 / CD4 UAT 评审 /
 *       CD5 上线评审，对应项目阶段门</li>
 *   <li><b>评审通过率</b>：每个门径的评审结论（{@code PASS / CONDITIONAL_PASS / REJECTED}），
 *       决定项目能否进入下一阶段</li>
 *   <li><b>门径阻断</b>：门径未通过时阻断项目阶段推进，联动 {@link com.njydsz.project.server.service.impl.ProjectInitiationServiceImpl}
 *       的 {@code stage} 状态机</li>
 *   <li><b>复盘归档</b>：评审记录包含会议纪要 / 改进项 / 决议，作为项目复盘依据</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>门径通过后联动立项阶段推进需与立项 Service 共享同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>门径序列</b>：{@code CD1 → CD2 → CD3 → CD4 → CD5} 严格顺序，
 *       上一门未通过无法进入下一门</li>
 *   <li><b>门径委员</b>：每个门径由 PMO / 业务 / 技术 / 质量 等多角色委员组成，
 *       通过 {@code ydsz-workflow} 流程引擎发起</li>
 *   <li><b>改进项跟踪</b>：评审中识别的改进项（{@code actionItems}）转交责任人跟踪</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       评审记录是合规审计的依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 记录 CD2 设计评审通过
 * ProjectGateReview review = new ProjectGateReview();
 * review.setInitiationId("project_123");
 * review.setGateCode("CD2");
 * review.setReviewDate(LocalDate.now());
 * review.setReviewResult("CONDITIONAL_PASS");
 * review.setReviewers("user_pm,user_qa,user_tech");
 * review.setActionItems("[\"补充高可用设计\", \"加强安全评审\"]");
 * review.setReviewMinutes("详见附件");
 * projectGateReviewService.save(review);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectGateReviewService 门径评审 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectGateReview 门径评审实体
 * @see com.njydsz.project.server.service.impl.ProjectInitiationServiceImpl 立项 Service（门径联动）
 */
@Service
@RequiredArgsConstructor
public class ProjectGateReviewServiceImpl implements ProjectGateReviewService {

    /** 门径评审仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectGateReviewRepository repository;

    /**
     * 根据主键查询门径评审
     *
     * @param id 门径评审主键
     * @return 门径评审实体，不存在返回 null
     */
    @Override
    public ProjectGateReview getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询门径评审
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code gateCode}、{@code reviewResult} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectGateReview> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增门径评审
     *
     * <p>新增后应触发 {@code GateReviewCompletedEvent} 领域事件，
     * 联动立项阶段门通过 / 阻断。
     *
     * @param review 门径评审实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectGateReview review) {
        return repository.save(review);
    }

    /**
     * 更新门径评审
     *
     * <p><b>注意：</b>已完成的门径评审（{@code status=COMPLETED}）的关键字段（结论 / 改进项）
     * <b>严禁</b>修改，错误应通过「补充纪要」纠正。
     *
     * @param review 门径评审实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectGateReview review) {
        return repository.updateById(review);
    }

    /**
     * 逻辑删除门径评审
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>评审记录是合规审计的依据，<b>严禁</b>物理删除。
     *
     * @param id 门径评审主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}

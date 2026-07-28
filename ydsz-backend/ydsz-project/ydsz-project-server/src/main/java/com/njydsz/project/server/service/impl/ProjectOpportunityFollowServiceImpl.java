package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectOpportunityFollow;
import com.njydsz.project.domain.repository.project.IProjectOpportunityFollowRepository;
import com.njydsz.project.server.service.ProjectOpportunityFollowService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商机跟进记录 Service 实现
 *
 * <p>对 {@link ProjectOpportunityFollowService} 接口的完整实现，是「项目管理 / 销售管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_opportunity_follow} 商机跟进记录表，
 * 对标大厂 PMIS / CRM 系统的「销售跟进 / 拜访记录 / 电话记录 / 报价记录 / 谈判记录」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>跟进痕迹</b>：记录销售跟进的全过程（拜访 / 电话 / 报价 / 谈判 / 方案演示），
 *       形成可追溯的时间线</li>
 *   <li><b>跟进方式</b>：{@code followType} 区分跟进方式（{@code VISIT} 拜访 / {@code CALL} 电话 /
 *       {@code EMAIL} 邮件 / {@code QUOTATION} 报价 / {@code NEGOTIATION} 谈判 /
 *       {@code DEMO} 方案演示）</li>
 *   <li><b>赢率更新</b>：跟进后更新商机的赢率（{@code winRate}），
 *       支撑销售漏斗管理</li>
 *   <li><b>转化立项</b>：商机成熟后由销售提交「转化立项」动作，
 *       自动生成 {@code ydsz_project_initiation} 立项记录</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>商机转化立项时联动立项 Service 需在同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>时间线回溯</b>：按 {@code followTime} 倒序展示，形成完整跟进时间线</li>
 *   <li><b>附件管理</b>：跟进附件（合同草案 / 报价单 / 会议纪要）通过 {@code ydsz-common-file} 存储</li>
 *   <li><b>下一步行动</b>：必填 {@code nextAction} 字段，支撑销售管理「下一步行动」机制</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       跟进记录是销售管理的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 销售记录客户拜访
 * ProjectOpportunityFollow follow = new ProjectOpportunityFollow();
 * follow.setOpportunityId("opp_123");
 * follow.setFollowType("VISIT");
 * follow.setFollowTime(LocalDateTime.now());
 * follow.setContent("拜访客户 CIO，介绍方案 v2.0，客户对架构表示认可");
 * follow.setNextAction("下次发送详细报价单，预计下周三前");
 * follow.setWinRate(new BigDecimal("0.65"));
 * follow.setFollowerId("user_sales_001");
 * projectOpportunityFollowService.save(follow);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectOpportunityFollowService 商机跟进 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectOpportunityFollow 商机跟进实体
 * @see com.njydsz.project.server.service.impl.ProjectOpportunityServiceImpl 商机主表 Service
 * @see com.njydsz.project.server.service.impl.ProjectInitiationServiceImpl 立项 Service（商机转化）
 */
@Service
@RequiredArgsConstructor
public class ProjectOpportunityFollowServiceImpl implements ProjectOpportunityFollowService {

    /** 商机跟进仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectOpportunityFollowRepository repository;

    /**
     * 根据主键查询商机跟进
     *
     * @param id 商机跟进主键
     * @return 商机跟进实体，不存在返回 null
     */
    @Override
    public ProjectOpportunityFollow getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询商机跟进
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code opportunityId}、
     * {@code followType}、{@code followerId} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectOpportunityFollow> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增商机跟进
     *
     * <p>新增后应触发 {@code OpportunityFollowCreatedEvent} 领域事件，
     * 联动商机赢率更新和下一步行动提醒。
     *
     * @param follow 商机跟进实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectOpportunityFollow follow) {
        return repository.save(follow);
    }

    /**
     * 更新商机跟进
     *
     * <p>典型场景：补充跟进细节、更新下一步行动时间。
     *
     * @param follow 商机跟进实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectOpportunityFollow follow) {
        return repository.updateById(follow);
    }

    /**
     * 逻辑删除商机跟进
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>跟进记录是销售管理的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 商机跟进主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}

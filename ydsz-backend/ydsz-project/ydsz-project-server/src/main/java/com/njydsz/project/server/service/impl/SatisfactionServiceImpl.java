package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.satisfaction.Satisfaction;
import com.njydsz.project.domain.repository.satisfaction.ISatisfactionRepository;
import com.njydsz.project.server.service.SatisfactionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户满意度 Service 实现
 *
 * <p>对 {@link SatisfactionService} 接口的完整实现，是「项目管理 / 客户体验」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_satisfaction} 客户满意度表，
 * 对标大厂 PMIS / 客户体验系统中的「客户满意度调研 / NPS / CSAT / 客户回访」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>满意度调研</b>：项目交付后 / 运维阶段定期回访客户，收集满意度评分</li>
 *   <li><b>多维评分</b>：维护质量 / 进度 / 服务 / 沟通 4 维评分 + 总体满意度</li>
 *   <li><b>NPS 指标</b>：计算净推荐值 NPS（推荐者 - 贬损者），
 *       支撑客户分级</li>
 *   <li><b>问题闭环</b>：低分（&lt;3）满意度自动触发问题工单</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>低分满意度触发工单需与工单服务共享同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>调研时机</b>：UAT 验收后 / 项目关闭后 / 季度回访 三种场景</li>
 *   <li><b>评分方式</b>：5 分制（1 非常不满意 ~ 5 非常满意），
 *       联动推荐意愿（0-10）计算 NPS</li>
 *   <li><b>匿名调研</b>：支持匿名 / 实名两种模式，匿名模式仅记录分数不记录客户</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       满意度数据是客户分析和质量改进的依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 项目关闭后回访客户
 * Satisfaction sat = new Satisfaction();
 * sat.setInitiationId("project_123");
 * sat.setCustomerId("cust_456");
 * sat.setSurveyType("PROJECT_CLOSURE");
 * sat.setQualityScore(5);
 * sat.setProgressScore(4);
 * sat.setServiceScore(5);
 * sat.setCommunicationScore(5);
 * sat.setOverallScore(5);
 * sat.setRecommendScore(9);
 * sat.setFeedback("整体很满意，希望后续合作");
 * sat.setSurveyDate(LocalDate.now());
 * satisfactionService.save(sat);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see SatisfactionService 满意度 Service 接口
 * @see com.njydsz.project.domain.entity.satisfaction.Satisfaction 满意度实体
 * @see com.njydsz.project.server.service.impl.OpsTicketServiceImpl 运维工单（低分触发）
 * @see com.njydsz.project.server.service.impl.ExecutionClosureServiceImpl 项目收尾（关闭触发）
 */
@Service
@RequiredArgsConstructor
public class SatisfactionServiceImpl implements SatisfactionService {

    /** 客户满意度仓储（聚合 Mapper + 缓存 + 事件） */
    private final ISatisfactionRepository repository;

    /**
     * 根据主键查询满意度
     *
     * @param id 满意度主键
     * @return 满意度实体，不存在返回 null
     */
    @Override
    public Satisfaction getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询满意度
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code customerId}、{@code surveyType} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<Satisfaction> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增满意度
     *
     * <p>新增后低分（&lt;3）会自动触发问题工单创建。
     *
     * @param satisfaction 满意度实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Satisfaction satisfaction) {
        return repository.save(satisfaction);
    }

    /**
     * 更新满意度
     *
     * <p>典型场景：补充反馈内容、修正录入错误。
     *
     * @param satisfaction 满意度实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Satisfaction satisfaction) {
        return repository.updateById(satisfaction);
    }

    /**
     * 逻辑删除满意度
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>满意度数据是客户分析和质量改进的依据，<b>严禁</b>物理删除。
     *
     * @param id 满意度主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}

package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.alert.AlertDispatch;
import com.njydsz.project.domain.repository.alert.IAlertDispatchRepository;
import com.njydsz.project.server.service.AlertDispatchService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 告警派发 Service 实现
 *
 * <p>对 {@link AlertDispatchService} 接口的完整实现，是「项目管理 / 告警中心」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_alert_dispatch} 告警派发表，
 * 对标大厂 PMIS / 监控告警系统中的「告警分发 / 告警路由 / 告警推送」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>告警派发</b>：业务异常 / 定时任务失败 / 预算超阈值 / 收入延迟等场景触发的告警
 *       通过本 Service 写入，并按 {@code targetRole / targetUserIds} 路由到对应收件人</li>
 *   <li><b>多通道推送</b>：通过 {@code pushChannels} 字段配置推送通道（{@code INAPP} 站内信 /
 *       {@code EMAIL} 邮件 / {@code SMS} 短信 / {@code IM} 企业微信 / {@code WEBHOOK} Webhook），
 *       由 {@code ydsz-message} 通知中心统一调度</li>
 *   <li><b>告警等级</b>：{@code alertLevel} 区分告警等级（{@code P0} 紧急 / {@code P1} 重要 /
 *       {@code P2} 次要 / {@code P3} 提示），与通知策略和升级策略联动</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>告警派发应与业务事务隔离，<b>推荐</b>通过 {@code ApplicationEventPublisher} 异步发布，
 *       业务失败不应回滚告警</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>告警去重</b>：通过 {@code alertCode} 唯一约束避免重复派发相同告警</li>
 *   <li><b>告警升级</b>：P0 / P1 告警超时未读应自动升级到上级领导，由独立 {@code AlertEscalator}
 *       定时任务调度</li>
 *   <li><b>告警归档</b>：已读告警可定期归档到 {@code ydsz_alert_archive} 表，保留历史可追溯</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       告警记录是合规审计的依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 业务异常触发告警
 * AlertDispatch alert = new AlertDispatch();
 * alert.setAlertCode("BUDGET_OVERRUN_PRJ_123_2026_07");
 * alert.setAlertType("BUDGET");
 * alert.setAlertLevel("P0");
 * alert.setSourceType("PROJECT");
 * alert.setSourceId("project_123");
 * alert.setTitle("项目 P-123 预算超阈值 95%");
 * alert.setContent("本月预算占用率达 96%，请立即处理");
 * alert.setTargetRole("PM");
 * alert.setPushChannels("INAPP,IM,EMAIL");
 * alertDispatchService.save(alert);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see AlertDispatchService 告警派发 Service 接口
 * @see com.njydsz.project.domain.entity.alert.AlertDispatch 告警派发实体
 * @see com.njydsz.message.server.service.NotificationService 通知中心 Service（实际推送）
 */
@Service
@RequiredArgsConstructor
public class AlertDispatchServiceImpl implements AlertDispatchService {

    /** 告警派发仓储（聚合 Mapper + 缓存 + 事件） */
    private final IAlertDispatchRepository repository;

    /**
     * 根据主键查询告警
     *
     * @param id 告警主键
     * @return 告警实体，不存在返回 null
     */
    @Override
    public AlertDispatch getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询告警
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code alertType}、
     * {@code alertLevel}、{@code sourceType} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<AlertDispatch> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增告警
     *
     * <p>新增后应通过 {@code ydsz-message} 通知中心异步推送到目标通道。
     *
     * @param alert 告警实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(AlertDispatch alert) {
        return repository.save(alert);
    }

    /**
     * 更新告警
     *
     * <p>典型场景：标记已读、关闭告警、补充处理说明。
     *
     * @param alert 告警实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AlertDispatch alert) {
        return repository.updateById(alert);
    }

    /**
     * 逻辑删除告警
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>告警记录原则上<b>不建议</b>删除，关闭告警应通过 {@code status=CLOSED} 标记。
     *
     * @param id 告警主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}

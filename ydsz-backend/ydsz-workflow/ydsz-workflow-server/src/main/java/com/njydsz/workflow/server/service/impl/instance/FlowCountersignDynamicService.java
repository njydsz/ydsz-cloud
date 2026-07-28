package com.njydsz.workflow.server.service.impl.instance;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-6: 会签动态完成条件服务
 *
 * <p>对标 Camunda <b>multiInstance completionCondition</b> 特性，
 * 支持在审批运行时<b>动态修改</b>会签通过阈值（VOTE / WEIGHTED_VOTE 模式），
 * 是大厂 B 端工作流「灵活调整会签规则」的核心能力。
 *
 * <p><b>业务场景：</b>
 * <ul>
 *   <li><b>规则调整</b>：会签发起后，业务方需要根据实际进展调整通过率阈值
 *       （如「5 人会签原本要求 60% 通过，现调整为 80%」）</li>
 *   <li><b>人数补强</b>：会签过程中需要新增或减少通过人数要求</li>
 *   <li><b>紧急应对</b>：发现配置不合理时，<b>运行时</b>立即调整而非终止重新发起</li>
 * </ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>动态通过率</b>：{@link #updateCompletionCondition} — 修改 VOTE / WEIGHTED_VOTE 模式的通过率阈值</li>
 *   <li><b>动态通过人数</b>：{@link #updateApproveCount} — 修改会签所需通过人数</li>
 *   <li><b>参数校验</b>：通过率范围 [0, 1]、通过人数 ≥ 1、任务必须存在</li>
 *   <li><b>模式校验</b>：仅 VOTE / WEIGHTED_VOTE 模式允许动态修改（其他模式不允许）</li>
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}，
 * 「参数校验 + 模式校验 + 任务更新 + 审计日志」原子性。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>模式白名单</b>：仅 VOTE / WEIGHTED_VOTE 模式允许动态调整；
 *       SEQ / ALL / FIRST 模式<b>不支持</b>动态修改（语义上无意义）</li>
 *   <li><b>范围校验</b>：{@code votePassRate} 必须在 {@code [0, 1]} 区间内，避免越界</li>
 *   <li><b>操作审计</b>：所有修改操作记录「旧值 → 新值 + operator」日志，便于追溯</li>
 *   <li><b>幂等性</b>：相同参数的多次调用结果一致（更新为相同值）</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 场景：5 人会签，原本要求 60% 通过（3/5），
 * //      因业务变化调整为 80% 通过（4/5）
 * countersignDynamicService.updateCompletionCondition(taskId, new BigDecimal("0.8"), "admin");
 * }</pre>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>动态调整后，<b>尚未投票</b>的任务会按新阈值判断</li>
 *   <li>动态调整后，<b>已经投票</b>的结果不受影响（按投票时点的阈值）</li>
 *   <li>会签已通过 / 失败后，修改阈值<b>不再生效</b>（已结束）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowRunTask 运行时任务实体（持有 votePassRate / approveCount 字段）
 * @see CountersignStrategy 会签策略接口
 * @see SysException 业务异常
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCountersignDynamicService {

    // ============================== 依赖注入 ==============================

    /** 运行时任务 Mapper，负责 {@code ydsz_flow_run_task} 表的查询与更新 */
    private final FlowRunTaskMapper taskMapper;

    // ============================== 公共方法 ==============================

    /**
     * 动态更新会签任务的通过阈值（仅 VOTE / WEIGHTED_VOTE 模式）
     *
     * <p>在会签进行中调整通过率阈值（如「60% → 80%」），
     * 调整后<b>尚未投票</b>的任务会按新阈值判断。
     *
     * <p><b>事务边界：</b>开启 {@code @Transactional(rollbackFor = Exception.class)}，
     * 「参数校验 + 模式校验 + 任务更新」原子性。
     *
     * @param taskId       任务 ID（雪花算法生成的字符串）
     * @param votePassRate 新的通过率阈值（{@link BigDecimal}，范围 {@code [0, 1]}）
     * @param operatorId   操作人 ID（用于审计日志）
     * @throws SysException 当参数非法、任务不存在、模式不支持时抛出
     *                      （错误码 {@code BAD_REQUEST} 或 {@code NOT_FOUND}）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCompletionCondition(String taskId, BigDecimal votePassRate, String operatorId) {
        if (!StringUtils.hasText(taskId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_a7b8c9d0");
        }
        if (votePassRate == null || votePassRate.compareTo(BigDecimal.ZERO) < 0
                || votePassRate.compareTo(BigDecimal.ONE) > 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_b8c9d0e1");
        }

        FlowRunTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_c9d0e1f2", taskId);
        }

        // 仅 VOTE / WEIGHTED_VOTE 模式允许动态修改
        String performType = task.getPerformType();
        if (!"VOTE".equals(performType) && !"WEIGHTED_VOTE".equals(performType)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_d0e1f2a3");
        }

        BigDecimal oldRate = task.getVotePassRate();
        task.setVotePassRate(votePassRate);
        taskMapper.updateById(task);

        log.info("[FlowCountersign] P2-6 动态修改完成条件: taskId={} oldRate={} → newRate={} operator={}",
                taskId, oldRate, votePassRate, operatorId);
    }

    /**
     * 动态更新会签所需通过人数
     *
     * <p>与会签模式的会签模式相比，本方法不限制 {@code performType}（任何模式都允许修改）。
     * 修改后<b>未达成</b>的会签按新人数阈值判断。
     *
     * <p><b>事务边界：</b>开启 {@code @Transactional(rollbackFor = Exception.class)}，
     * 「参数校验 + 任务更新」原子性。
     *
     * @param taskId       任务 ID
     * @param approveCount 新的所需通过人数（{@link Integer}，必须 ≥ 1）
     * @param operatorId   操作人 ID（用于审计日志）
     * @throws SysException 当参数非法或任务不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateApproveCount(String taskId, Integer approveCount, String operatorId) {
        if (!StringUtils.hasText(taskId) || approveCount == null || approveCount < 1) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_e1f2a3b4");
        }

        FlowRunTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_c9d0e1f2", taskId);
        }

        Integer oldCount = task.getApproveCount();
        task.setApproveCount(approveCount);
        taskMapper.updateById(task);

        log.info("[FlowCountersign] P2-6 动态修改通过人数: taskId={} oldCount={} → newCount={} operator={}",
                taskId, oldCount, approveCount, operatorId);
    }
}

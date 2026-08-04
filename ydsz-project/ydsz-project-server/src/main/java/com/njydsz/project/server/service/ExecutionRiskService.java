package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.execution.ExecutionRisk;
/**
 * 项目风险 Service
 *
 * <p>管理项目风险（{@code ydsz_execution_risk}）的识别、跟踪、关闭。</p>
 * <p>风险是项目执行中可能影响进度/质量/成本的不确定事件，按识别 → 评估 → 应对 → 跟踪 → 关闭流程管理。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>风险登记：识别风险并登记</b></li>
 *   <li><b>风险评估：按概率×影响评估风险等级</b></li>
 *   <li><b>应对措施：每条风险指定应对人和应对措施</b></li>
 *   <li><b>状态跟踪：OPEN / MITIGATING / CLOSED</b></li>
 * </ul>
 *
 * <p><b>风险等级：</b>HIGH / MEDIUM / LOW。
 * <p><b>风险类型：</b>技术 / 资源 / 进度 / 范围 / 质量 / 外部依赖。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.execution.ExecutionRisk 风险实体
 * @see AlertDispatchService 告警分发 Service(高风险触发告警)
 */
public interface ExecutionRiskService {
    ExecutionRisk getById(String id);
    IPage<ExecutionRisk> page(int pageNum, int pageSize);
    boolean save(ExecutionRisk entity);
    boolean updateById(ExecutionRisk entity);
    boolean removeById(String id);
}

package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.execution.ExecutionClosure;
/**
 * 项目终验 Service
 *
 * <p>管理项目终验（{@code ydsz_execution_closure}）的申请、评审、归档。</p>
 * <p>项目终验是项目生命周期的收官阶段：交付物验收、文档归档、团队释放、客户签收、</p>
 * <p>满意度触发，是项目关闭前的最后一道关卡。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>终验申请：项目经理发起终验申请</b></li>
 *   <li><b>交付物核对：所有交付物已验收</b></li>
 *   <li><b>文档归档：项目文档归入知识库</b></li>
 *   <li><b>满意度触发：终验通过触发客户填写满意度</b></li>
 * </ul>
 *
 * <p><b>终验阶段：</b>申请 → 资料准备 → 验收会 → 客户签收 → 项目关闭。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.execution.ExecutionClosure 终验实体
 * @see ExecutionDeliveryItemService 交付物 Service(终验前确认)
 * @see SatisfactionService 满意度 Service(终验后触发)
 * @see ProjectInitiationService 立项 Service(终验后项目关闭)
 */
public interface ExecutionClosureService {
    ExecutionClosure getById(String id);
    IPage<ExecutionClosure> page(int pageNum, int pageSize);
    boolean save(ExecutionClosure entity);
    boolean updateById(ExecutionClosure entity);
    boolean removeById(String id);
}

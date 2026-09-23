package com.njydsz.agent.infra.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.agent.domain.entity.AgentApproval;
import com.njydsz.agent.domain.entity.DagWorkflow;
import com.njydsz.agent.domain.entity.PromptVersion;
import com.njydsz.agent.domain.entity.TokenUsageRecord;
import com.njydsz.agent.infra.entity.AgentApprovalPO;
import com.njydsz.agent.infra.entity.DagWorkflowPO;
import com.njydsz.agent.infra.entity.PromptVersionPO;
import com.njydsz.agent.infra.entity.TokenUsageRecordPO;

/**
 * Domain 实体 ↔ 持久化对象（PO）双向 MapStruct 转换器。
 *
 * <p>位于基础设施层（infra），负责 domain 纯净 POJO 与 infra PO（含 MyBatis-Plus 注解）之间的转换。
 * 确保 domain 层不反向依赖 MP 框架注解：domain → PO 由 Repository 实现在写入前调用，
 * PO → domain 由 Repository 实现在读取后将结果转换为 domain 后由 AgentConverter 转换为 VO。
 *
 * <p><b>DDD 合规</b>：本类位于 infra 层，依赖 domain 层实体，符合「infra → domain」单向依赖。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Mapper
public interface AgentPoConverter {

  /** MapStruct 实例 */
  AgentPoConverter INSTANT = Mappers.getMapper(AgentPoConverter.class);

  // ===== AgentApproval ↔ AgentApprovalPO =====

  /** Domain → PO（写入 DB 前调用） */
  @Mapping(target = "id", source = "id")
  AgentApprovalPO domainToPo(AgentApproval domain);

  /** PO → Domain（读取 DB 后调用） */
  @Mapping(target = "id", source = "id")
  AgentApproval poToDomain(AgentApprovalPO po);

  // ===== PromptVersion ↔ PromptVersionPO =====

  /** Domain → PO */
  PromptVersionPO domainToPo(PromptVersion domain);

  /** PO → Domain */
  PromptVersion poToDomain(PromptVersionPO po);

  // ===== TokenUsageRecord ↔ TokenUsageRecordPO =====

  /** Domain → PO */
  TokenUsageRecordPO domainToPo(TokenUsageRecord domain);

  /** PO → Domain */
  TokenUsageRecord poToDomain(TokenUsageRecordPO po);

  // ===== DagWorkflow ↔ DagWorkflowPO =====

  /** Domain → PO */
  DagWorkflowPO domainToPo(DagWorkflow domain);

  /** PO → Domain */
  DagWorkflow poToDomain(DagWorkflowPO po);
}

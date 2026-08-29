package com.njydsz.workflow.server.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.dto.FlowSaveDraftDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.repository.FlowDefinitionRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.statemachine.FlowInstanceStateMachine;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;

/**
 * P0-5: 草稿实例服务
 *
 * <p>借鉴 Flowlong 的「暂存待审」概念，提供流程草稿的保存、查询、提交、取消能力。
 * 草稿不触发流程流转，仅为 DRAFT 状态的流程实例，保存用户已填写的表单数据。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>保存草稿</b>：创建 DRAFT 状态的流程实例，保存表单数据到 variables
 *   <li><b>更新草稿</b>：更新已有草稿的表单数据
 *   <li><b>提交草稿</b>：将 DRAFT → RUNNING，触发正常流程流转
 *   <li><b>取消草稿</b>：将 DRAFT → TERMINATED，释放资源
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Service
public class DraftInstanceService {

  /** 流程实例仓储 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程定义仓储 */
  private final FlowDefinitionRepository definitionRepository;

  /** 流程实例状态机 */
  private final FlowInstanceStateMachine stateMachine;

  /** 流程推进引擎 */
  private final DefaultFlowAdvancer advancer;

  /**
   * 构造器注入依赖。
   *
   * @param instanceRepository 流程实例仓储
   * @param definitionRepository 流程定义仓储
   * @param stateMachine 流程实例状态机
   * @param advancer 流程推进引擎
   */
  public DraftInstanceService(FlowInstanceRepository instanceRepository,
      FlowDefinitionRepository definitionRepository, FlowInstanceStateMachine stateMachine,
      DefaultFlowAdvancer advancer) {
    this.instanceRepository = instanceRepository;
    this.definitionRepository = definitionRepository;
    this.stateMachine = stateMachine;
    this.advancer = advancer;
    log.info("[Flow-Draft] 草稿实例服务已初始化");
  }

  /**
   * 保存流程草稿。
   *
   * <p>创建 DRAFT 状态的流程实例，保存用户已填写的表单数据。
   * 如果同一业务单据已有草稿，则更新而非新建。
   *
   * @param dto 草稿保存参数
   * @return 草稿实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String saveDraft(FlowSaveDraftDTO dto) {
    // 校验流程定义（仅最新已发布版本可用于发起）
    FlowDefinitionVO definition =
        definitionRepository.findLatestPublished(dto.getFlowCode(), dto.getTenantId()).orElse(null);
    if (definition == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.definition.not.found")
          .params(dto.getFlowCode())
          .build();
    }

    // 检查是否已有草稿（同一业务单据）
    FlowInstanceVO existingDraft = instanceRepository
        .findByBusinessAndStatus(dto.getBusinessType(), dto.getBusinessId(),
            FlowInstanceStatus.DRAFT.name())
        .orElse(null);

    if (existingDraft != null) {
      // 更新已有草稿
      return updateDraft(existingDraft.getId(), dto);
    }

    // 创建新草稿实例
    String instanceId = UUID.randomUUID().toString();
    FlowInstanceVO draft = new FlowInstanceVO();
    draft.setId(instanceId);
    draft.setDefinitionId(definition.getId());
    draft.setFlowCode(dto.getFlowCode());
    draft.setFlowName(definition.getFlowName());
    draft.setFlowVersion(definition.getVersion());
    draft.setFlowStatus(FlowInstanceStatus.DRAFT.name());
    draft.setBusinessType(dto.getBusinessType());
    draft.setBusinessId(dto.getBusinessId());
    draft.setBusinessNo(dto.getBusinessNo());
    draft.setTitle(dto.getTitle() != null ? dto.getTitle() : definition.getFlowName() + " - 草稿");
    draft.setInitiatorId(dto.getInitiatorId());
    draft.setInitiatorName(dto.getInitiatorName());
    draft.setTenantId(dto.getTenantId());
    draft.setProviderTraceId(dto.getProviderTraceId());
    draft.setCurrentNodeCode("");
    draft.setCurrentNodeName("");
    draft.setCreatedAt(LocalDateTime.now());
    draft.setUpdatedAt(LocalDateTime.now());

    // 保存草稿数据到 variables
    Map<String, Object> variables = dto.getDraftData();
    if (variables == null) {
      variables = new HashMap<>();
    }
    variables.put("_draft", true);
    variables.put("_draftSavedAt", LocalDateTime.now().toString());
    draft.setVariable(YdszJson.toJson(variables));

    instanceRepository.save(draft);
    log.info("[Flow-Draft] 保存草稿成功: instanceId={}, flowCode={}, businessId={}", instanceId,
        dto.getFlowCode(), dto.getBusinessId());

    return instanceId;
  }

  /**
   * 更新已有草稿。
   *
   * @param instanceId 草稿实例 ID
   * @param dto 草稿参数
   * @return 草稿实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String updateDraft(String instanceId, FlowSaveDraftDTO dto) {
    FlowInstanceVO draft = instanceRepository.findById(instanceId).orElse(null);
    if (draft == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.draft.not.found")
          .params(instanceId)
          .build();
    }

    // 校验状态
    if (!FlowInstanceStatus.DRAFT.name().equals(draft.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BIZ_ERROR)
          .key("error.workflow.instance.status.invalid")
          .params(draft.getFlowStatus(), FlowInstanceStatus.DRAFT.name())
          .build();
    }

    // 更新草稿数据（variable 字段为 JSON 字符串，经 YdszJson 反序列化后合并）
    Map<String, Object> variables = dto.getDraftData();
    if (variables == null) {
      variables = new HashMap<>();
    }
    if (draft.getVariable() != null && !draft.getVariable().isBlank()) {
      variables.putAll(YdszJson.parseMap(draft.getVariable()));
    }
    variables.put("_draft", true);
    variables.put("_draftSavedAt", LocalDateTime.now().toString());
    Object prevCount = variables.get("_draftUpdatedCount");
    int updatedCount = prevCount instanceof Number number ? number.intValue() + 1 : 1;
    variables.put("_draftUpdatedCount", updatedCount);
    draft.setVariable(YdszJson.toJson(variables));

    // 更新可选字段
    if (dto.getTitle() != null) {
      draft.setTitle(dto.getTitle());
    }
    if (dto.getBusinessNo() != null) {
      draft.setBusinessNo(dto.getBusinessNo());
    }
    draft.setUpdatedAt(LocalDateTime.now());

    instanceRepository.update(draft);
    log.info("[Flow-Draft] 更新草稿成功: instanceId={}", instanceId);

    return instanceId;
  }

  /**
   * 提交草稿（正式发起审批）。
   *
   * <p>将 DRAFT → RUNNING，触发正常流程流转。复用引擎推进逻辑。
   *
   * @param instanceId 草稿实例 ID
   * @param draftData 更新后的表单数据（可选）
   * @param operatorId 操作人 ID
   * @return 流程实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String submitDraft(String instanceId, Map<String, Object> draftData, String operatorId) {
    FlowInstanceVO draft = instanceRepository.findById(instanceId).orElse(null);
    if (draft == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.draft.not.found")
          .params(instanceId)
          .build();
    }

    // 校验状态流转
    stateMachine.requireTransition(FlowInstanceStatus.DRAFT, FlowInstanceStatus.RUNNING);

    // 更新草稿数据（如有）
    if (draftData != null && !draftData.isEmpty()) {
      Map<String, Object> variables =
          draft.getVariable() != null && !draft.getVariable().isBlank()
              ? YdszJson.parseMap(draft.getVariable())
              : new HashMap<>();
      variables.putAll(draftData);
      variables.put("_draft", false);
      variables.put("_draftSubmittedAt", LocalDateTime.now().toString());
      draft.setVariable(YdszJson.toJson(variables));
    }

    // 更新状态为 RUNNING
    draft.setFlowStatus(FlowInstanceStatus.RUNNING.name());
    draft.setStartAt(LocalDateTime.now());
    draft.setUpdatedAt(LocalDateTime.now());
    instanceRepository.update(draft);

    log.info("[Flow-Draft] 提交草稿成功: instanceId={}, flowCode={}", instanceId, draft.getFlowCode());

    // 使用引擎推进：从开始节点触发流程流转
    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      log.error("[Flow-Draft] 草稿提交后流程推进失败: instanceId={} err={}", instanceId, e.getMessage(),
          e);
      throw e;
    }

    return instanceId;
  }

  /**
   * 取消草稿。
   *
   * <p>将 DRAFT → TERMINATED，释放资源。
   *
   * @param instanceId 草稿实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void cancelDraft(String instanceId) {
    FlowInstanceVO draft = instanceRepository.findById(instanceId).orElse(null);
    if (draft == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.draft.not.found")
          .params(instanceId)
          .build();
    }

    // 校验状态流转
    stateMachine.requireTransition(FlowInstanceStatus.DRAFT, FlowInstanceStatus.TERMINATED);

    // 更新状态为 TERMINATED
    draft.setFlowStatus(FlowInstanceStatus.TERMINATED.name());
    draft.setUpdatedAt(LocalDateTime.now());
    draft.setEndAt(LocalDateTime.now());
    instanceRepository.update(draft);

    log.info("[Flow-Draft] 取消草稿成功: instanceId={}", instanceId);
  }
}

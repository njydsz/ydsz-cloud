package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.feign.assembler.NameType;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.security.DataScopeHelper;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;

/**
 * 流程实例查询服务
 *
 * <p>负责流程实例的所有<b>只读查询</b>能力（带 {@code @Transactional(readOnly = true)}）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>按 ID 查询</b>：{@link #getById} — 含 initiatorName 兜底富化
 *   <li><b>按业务关联查询</b>：{@link #getByBusiness} — 查活跃实例
 *   <li><b>分页查询</b>：{@link #page} — 多维度过滤 + 数据权限
 *   <li><b>我发起的</b>：{@link #listByInitiator} — 按发起人查询
 *   <li><b>可撤回节点</b>：{@link #listRecallableNodes} — 查询可撤回的历史节点列表
 *   <li><b>表单渲染数据</b>：{@link #getFormRenderData} — 节点字段权限配置
 *   <li><b>视图转换</b>：{@link #toView} — DO 转 ViewDTO
 * </ul>
 *
 * <p><b>事务边界：</b>所有方法为只读事务，不修改任何数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowInstanceQueryService {

  /** 流程实例仓储，负责 ydsz_flow_instance 的领域持久化 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程节点仓储，负责 ydsz_flow_node 的领域持久化 */
  private final FlowNodeRepository nodeRepository;

  /** 运行时任务仓储，负责 ydsz_flow_run_task 的领域持久化 */
  private final FlowRunTaskRepository taskRepository;

  /** 历史任务仓储，负责 ydsz_flow_his_task 的领域持久化 */
  private final FlowHisTaskRepository hisTaskRepository;

  /** MapStruct 转换器，用于 VO ↔ DO 转换 */
  private final WorkflowConverter converter;

  /** 流程变量管理器，负责变量读写与解析 */
  private final FlowInstanceVariableManager variableManager;

  /**
   * P0-4: 跨服务名称解析门面，用于在 getById / page 读路径兜底富化 initiatorName。
   */
  private final NameAssembler nameAssembler;

  /**
   * 按 ID 查询流程实例
   *
   * <p>读路径兜底富化 initiatorName：当 FlowInstanceDO.initiatorName 在写时未持久化（历史数据或某些路径遗漏）时， 通过 NameAssembler 调用
   * ydsz-userinfo 服务的 batch-names 端点， 用 initiatorId 实时解析 realName 并回填到返回对象。
   *
   * @param id 实例 ID
   * @return 流程实例 DO，不存在返回 null
   */
  @Transactional(readOnly = true)
  public FlowInstanceDO getById(String id) {
    FlowInstanceVO instance = instanceRepository.findById(id).orElse(null);
    if (instance == null) {
      return null;
    }
    // P0-4: 读路径兜底富化 initiatorName，避免历史数据或写入遗漏导致前端显示空白
    if (!StringUtils.hasText(instance.getInitiatorName())
        && StringUtils.hasText(instance.getInitiatorId())) {
      nameAssembler.enrichOne(
          instance, FlowInstanceVO::getInitiatorId, FlowInstanceVO::setInitiatorName, NameType.USER);
    }
    return converter.entityToDO(instance);
  }

  /**
   * 业务关联查询（通过业务类型 + 业务 ID 查实例）
   *
   * <p>幂等启动的核心反查接口：重复发起前先调用此方法判断是否已存在实例。
   *
   * @param businessType 业务类型（如 {@code project_initiation}）
   * @param businessId 业务 ID
   * @return 流程实例 DO，未发起时返回 null
   */
  @Transactional(readOnly = true)
  public FlowInstanceDO getByBusiness(String businessType, String businessId) {
    // P1-2: 增加 tenantId 过滤，防止跨租户串号；仅返回活跃实例（RUNNING/SUSPENDED）
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return instanceRepository.findByBusiness(tenantId, businessType, businessId)
        .map(converter::entityToDO)
        .orElse(null);
  }

  /**
   * 发起人维度查询（我的发起）
   *
   * @param initiatorId 发起人 ID
   * @param flowStatus 流程状态过滤（RUNNING / COMPLETED / REJECTED / TERMINATED，null 表示全部）
   * @return 该发起人指定状态的实例列表
   */
  @Transactional(readOnly = true)
  public List<FlowInstanceDO> listByInitiator(String initiatorId, String flowStatus) {
    return instanceRepository.findByInitiatorId(initiatorId).stream()
        .filter(vo -> flowStatus == null || flowStatus.equals(vo.getFlowStatus()))
        .map(converter::entityToDO)
        .toList();
  }

  /**
   * P1-1: 查询可撤回的历史节点列表。
   *
   * <p>返回当前实例已办过的历史节点（排除当前待办节点），供前端展示"撤回到"选择列表。
   *
   * <p>校验规则：仅发起人可查询，仅运行中实例可查询。
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @return 节点列表，每个 Map 包含 nodeCode / nodeName / firstFinishAt / visitCount
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listRecallableNodes(String instanceId, String initiatorId) {
    FlowInstanceDO instance = getByIdOrThrow(instanceId);
    // 校验：仅发起人可查询
    if (!instance.getInitiatorId().equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.msg_cc712a3a")
          .build();
    }
    // 校验：仅运行中可查询
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_3095a676")
          .build();
    }
    // 查历史已办节点
    List<Map<String, Object>> passedNodes = hisTaskRepository.listPassedNodes(instanceId);
    if (passedNodes == null || passedNodes.isEmpty()) {
      return Collections.emptyList();
    }
    // 排除当前待办节点（撤回到当前节点无意义）
    String currentNodeCode = instance.getCurrentNodeCode();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> n : passedNodes) {
      Object code = n.get("nodeCode");
      if (code != null && !code.toString().equals(currentNodeCode)) {
        result.add(n);
      }
    }
    return result;
  }

  /**
   * P2-23: 实例多维分页查询
   *
   * @param businessType 业务类型（可选）
   * @param initiatorId 发起人 ID（可选）
   * @param flowStatus 流程状态（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @param tenantId 租户 ID（可选）
   * @param pageNo 页码（从 1 开始）
   * @param pageSize 每页大小
   * @return 分页结果
   */
  @Transactional(readOnly = true)
  @DataScope(deptAlias = "", userAlias = "", userColumn = "initiator_id")
  public PageResponse<List<FlowInstanceDO>> page(
      String businessType,
      String initiatorId,
      String flowStatus,
      java.time.LocalDateTime startTime,
      java.time.LocalDateTime endTime,
      String tenantId,
      int pageNo,
      int pageSize) {
    // P2-23: 真分页（SQL LIMIT/OFFSET），支持多维度过滤
    int safePage = Math.max(1, pageNo);
    int safeSize = pageSize > 0 ? pageSize : 20;
    int offset = (safePage - 1) * safeSize;
    // P1-3: 数据权限 SQL 片段（由 DataScopeAspect ThreadLocal 传递，DataScopeHelper 构造）
    String dataScopeFilter = "";
    try {
      dataScopeFilter = DataScopeHelper.buildSqlFragment("", "", "dept_id", "initiator_id");
    } catch (Exception e) {
      log.debug("[Flow] 数据权限片段构建失败（无登录用户上下文）: {}", e.getMessage());
    }
    List<FlowInstanceDO> list = instanceRepository.findPage(
        businessType, initiatorId, flowStatus, startTime, endTime, tenantId, dataScopeFilter, offset, safeSize)
        .stream().map(converter::entityToDO).toList();
    long total = instanceRepository.countPage(
        businessType, initiatorId, flowStatus, startTime, endTime, tenantId, dataScopeFilter);
    return PageResponse.success(total, (long) safePage, (long) safeSize, list);
  }

  /**
   * 转化为视图对象（含当前节点任务列表）
   *
   * <p>用于「流程详情」页组装，拼接富化字段（发起人姓名、当前节点名称、审批人姓名）。
   *
   * @param instance 流程实例 DO
   * @param currentTasks 当前节点的待办任务列表
   * @return 流程实例视图 VO
   */
  public FlowInstanceViewDTO toView(
      FlowInstanceDO instance, List<FlowInstanceViewDTO.FlowTaskViewDTO> currentTasks) {
    if (instance == null) {
      return null;
    }
    return FlowInstanceViewDTO.builder()
        .id(instance.getId())
        .flowCode(instance.getFlowCode())
        .flowName(instance.getFlowName())
        .version(instance.getFlowVersion())
        .businessType(instance.getBusinessType())
        .businessId(instance.getBusinessId())
        .businessNo(instance.getBusinessNo())
        .title(instance.getTitle())
        .initiatorId(instance.getInitiatorId())
        .initiatorName(instance.getInitiatorName())
        .currentNodeCode(instance.getCurrentNodeCode())
        .currentNodeName(instance.getCurrentNodeName())
        .flowStatus(instance.getFlowStatus())
        .activityStatus(instance.getActivityStatus())
        .startAt(instance.getStartAt())
        .endAt(instance.getEndAt())
        .durationMs(instance.getDurationMs())
        .variable(instance.getVariable())
        .currentTasks(currentTasks)
        .build();
  }

  /**
   * GAP-V2-02: 获取表单渲染数据 — 根据当前任务所在节点返回字段权限配置
   *
   * <p>审批人打开待办时，前端调用本接口获取当前节点的表单字段权限（EDIT/READONLY/HIDDEN）， 结合业务表单实现运行时表单渲染。
   *
   * @param instanceId 流程实例 ID
   * @param taskId 当前任务 ID（可空，为空则取实例当前节点的配置）
   * @return Map 包含 nodeCode / nodeName / formFieldsConfig / variables
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getFormRenderData(String instanceId, String taskId) {
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_fc4b1c16")
          .params(instanceId)
          .build();
    }
    String nodeCode;
    String nodeName;
    String formFieldsConfig = null;
    Map<String, Object> fieldPermissions = null;
    Map<String, Object> commentConfig = null;
    if (taskId != null) {
      // 优先从任务获取节点信息
      FlowRunTaskVO task = taskRepository.findById(taskId).orElse(null);
      if (task == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.msg_6541ab08")
            .params(taskId)
            .build();
      }
      nodeCode = task.getNodeCode();
      nodeName = task.getNodeName();
    } else {
      // 回退到实例当前节点
      nodeCode = instance.getCurrentNodeCode();
      nodeName = instance.getCurrentNodeName();
    }
    // 查节点表获取 formFieldsConfig 和 ext 中的字段权限
    if (nodeCode != null) {
      FlowNodeVO node = nodeRepository.findByCode(instance.getDefinitionId(), nodeCode).orElse(null);
      if (node != null) {
        formFieldsConfig = node.getFormFieldsConfig();
        if (nodeName == null) {
          nodeName = node.getNodeName();
        }
        // P1-4: 从 ext JSON 解析字段权限和审批意见配置
        if (node.getExt() != null && !node.getExt().isBlank()) {
          try {
            Map<String, Object> ext = YdszJson.parseMap(node.getExt());
            if (ext != null) {
              Object fp = ext.get("formFieldPermissions");
              if (fp instanceof Map<?, ?> m) {
                fieldPermissions =
                    FlowInstanceVariableManager.castToStringObjectMap(m);
              }
              Object cc = ext.get("commentConfig");
              if (cc instanceof Map<?, ?> m2) {
                commentConfig =
                    FlowInstanceVariableManager.castToStringObjectMap(m2);
              }
            }
          } catch (Exception e) {
            log.debug("[Flow] 解析节点 ext 字段权限失败: node={} err={}", nodeCode, e.getMessage());
          }
        }
      }
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("instanceId", instanceId);
    result.put("taskId", taskId);
    result.put("nodeCode", nodeCode);
    result.put("nodeName", nodeName);
    result.put("formFieldsConfig", formFieldsConfig);
    // P1-4: 字段权限配置（READONLY/REQUIRED/HIDDEN/EDITABLE）
    result.put("fieldPermissions", fieldPermissions);
    // P1-4: 审批意见配置（required/minLength/placeholder）
    result.put("commentConfig", commentConfig);
    result.put("variables", variableManager.getVariables(instanceId));
    result.put("flowStatus", instance.getFlowStatus());
    result.put("title", instance.getTitle());
    return result;
  }

  // ============================== 私有辅助方法 ==============================

  private FlowInstanceDO getByIdOrThrow(String id) {
    FlowInstanceVO instance = instanceRepository.findById(id).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_67a10717")
          .params(id)
          .build();
    }
    return converter.entityToDO(instance);
  }
}

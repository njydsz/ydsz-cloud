package com.njydsz.workflow.domain.converter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.dto.FlowCategoryDTO;
import com.njydsz.workflow.domain.dto.FlowDefinitionDTO;
import com.njydsz.workflow.domain.dto.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.domain.dto.FlowDelegateAuthPutDTO;
import com.njydsz.workflow.domain.dto.FlowInstanceDTO;
import com.njydsz.workflow.domain.dto.FlowRunTaskDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowAdminRole;
import com.njydsz.workflow.domain.entity.FlowAttachment;
import com.njydsz.workflow.domain.entity.FlowAuditLog;
import com.njydsz.workflow.domain.entity.FlowAutoTrigger;
import com.njydsz.workflow.domain.entity.FlowCategory;
import com.njydsz.workflow.domain.entity.FlowCc;
import com.njydsz.workflow.domain.entity.FlowCcRule;
import com.njydsz.workflow.domain.entity.FlowComment;
import com.njydsz.workflow.domain.entity.FlowDefinition;
import com.njydsz.workflow.domain.entity.FlowDelegateAuth;
import com.njydsz.workflow.domain.entity.FlowEventSubscription;
import com.njydsz.workflow.domain.entity.FlowHisInstance;
import com.njydsz.workflow.domain.entity.FlowHisTask;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowQuickComment;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.entity.FlowSkip;
import com.njydsz.workflow.domain.entity.FlowTemplate;
import com.njydsz.workflow.domain.entity.FlowTimer;
import com.njydsz.workflow.domain.entity.FlowUser;
import com.njydsz.workflow.domain.vo.FlowAdminRoleVO;
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;
import com.njydsz.workflow.domain.vo.FlowCategoryTreeVO;
import com.njydsz.workflow.domain.vo.FlowCategoryVO;
import com.njydsz.workflow.domain.vo.FlowCcRuleVO;
import com.njydsz.workflow.domain.vo.FlowCcVO;
import com.njydsz.workflow.domain.vo.FlowCommentVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO;
import com.njydsz.workflow.domain.vo.FlowHisInstanceVO;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowQuickCommentVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.domain.vo.FlowTemplateVO;
import com.njydsz.workflow.domain.vo.FlowTimerVO;
import com.njydsz.workflow.domain.vo.FlowUserVO;
import com.njydsz.workflow.domain.vo.StringVO;

/**
 * workflow 模块统一 MapStruct 转换器（Infra 层）。
 *
 * <p>承担工作流模块所有 Entity ↔ VO、DTO → Entity 的类型转换，遵循单一转换器模式。
 * 覆盖流程定义、流程实例、任务、审批日志、委托授权等核心实体的转换。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射
 *   <li>通过 {@link #INSTANT} 单例访问，由 Spring 注入到 Service/Controller
 *   <li>同名字段自动映射；系统字段通过 @Mapping(ignore = true) 忽略
 *   <li>entityToVO 方向自动排除敏感字段（如密码、Token 等）
 * </ul>
 *
 * <p><b>分层定位：</b>位于基础设施层（避免 domain 层反向依赖 infra.entity），
 * infra 层实现依赖倒置提供 Converter。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WorkflowConverter {

  /** MapStruct 单例实例 */
  WorkflowConverter INSTANT = Mappers.getMapper(WorkflowConverter.class);

  // ===== FlowAdminRole =====
  FlowAdminRoleVO entityToVO(FlowAdminRole entity);

  List<FlowAdminRoleVO> flowAdminRoleListToVO(List<FlowAdminRole> entities);

  // ===== FlowAttachment =====
  FlowAttachmentVO entityToVO(FlowAttachment entity);

  List<FlowAttachmentVO> flowAttachmentListToVO(List<FlowAttachment> entities);

  // ===== FlowAuditLog =====
  FlowAuditLogVO entityToVO(FlowAuditLog entity);

  List<FlowAuditLogVO> flowAuditLogListToVO(List<FlowAuditLog> entities);

  // ===== FlowAutoTrigger =====
  FlowAutoTriggerVO entityToVO(FlowAutoTrigger entity);

  List<FlowAutoTriggerVO> flowAutoTriggerListToVO(List<FlowAutoTrigger> entities);

  // ===== FlowCategory =====
  FlowCategoryVO entityToVO(FlowCategory entity);

  List<FlowCategoryVO> flowCategoryListToVO(List<FlowCategory> entities);

  FlowCategoryTreeVO entityToTreeVO(FlowCategory entity);

  List<FlowCategoryTreeVO> flowCategoryListToTreeVO(List<FlowCategory> entities);

  // ===== FlowCc =====
  FlowCcVO entityToVO(FlowCc entity);

  List<FlowCcVO> flowCcListToVO(List<FlowCc> entities);

  // ===== FlowCcRule =====
  FlowCcRuleVO entityToVO(FlowCcRule entity);

  List<FlowCcRuleVO> flowCcRuleListToVO(List<FlowCcRule> entities);

  /**
   * 抄送规则 VO → 抄送规则。
   *
   * @param vo 抄送规则视图对象
   * @return 抄送规则实体
   */
  FlowCcRule entityToEntity(FlowCcRuleVO vo);

  // ===== FlowComment =====
  FlowCommentVO entityToVO(FlowComment entity);

  List<FlowCommentVO> flowCommentListToVO(List<FlowComment> entities);

  // ===== FlowDefinition =====
  FlowDefinitionVO entityToVO(FlowDefinition entity);

  List<FlowDefinitionVO> flowDefinitionListToVO(List<FlowDefinition> entities);

  // ===== FlowDelegateAuth =====
  FlowDelegateAuthVO entityToVO(FlowDelegateAuth entity);

  List<FlowDelegateAuthVO> flowDelegateAuthListToVO(List<FlowDelegateAuth> entities);

  // ===== FlowEventSubscription =====
  FlowEventSubscriptionVO entityToVO(FlowEventSubscription entity);

  List<FlowEventSubscriptionVO> flowEventSubscriptionListToVO(List<FlowEventSubscription> entities);

  // ===== FlowHisInstance =====
  FlowHisInstanceVO entityToVO(FlowHisInstance entity);

  List<FlowHisInstanceVO> flowHisInstanceListToVO(List<FlowHisInstance> entities);

  /**
   * 历史实例 VO → 历史实例。
   *
   * @param vo 历史实例视图对象
   * @return 历史实例实体
   */
  FlowHisInstance entityToEntity(FlowHisInstanceVO vo);

  // ===== FlowHisTask =====
  FlowHisTaskVO entityToVO(FlowHisTask entity);

  List<FlowHisTaskVO> flowHisTaskListToVO(List<FlowHisTask> entities);

  // ===== FlowInstance =====
  FlowInstanceVO entityToVO(FlowInstance entity);

  List<FlowInstanceVO> flowInstanceListToVO(List<FlowInstance> entities);

  // ===== FlowNode =====
  FlowNodeVO entityToVO(FlowNode entity);

  List<FlowNodeVO> flowNodeListToVO(List<FlowNode> entities);

  // ===== FlowQuickComment =====
  FlowQuickCommentVO entityToVO(FlowQuickComment entity);

  FlowQuickComment entityToEntity(FlowQuickCommentVO vo);

  List<FlowQuickCommentVO> flowQuickCommentListToVO(List<FlowQuickComment> entities);

  // ===== FlowRunTask =====
  FlowRunTaskVO entityToVO(FlowRunTask entity);

  List<FlowRunTaskVO> flowRunTaskListToVO(List<FlowRunTask> entities);

  // ===== FlowSkip =====
  FlowSkipVO entityToVO(FlowSkip entity);

  List<FlowSkipVO> flowSkipListToVO(List<FlowSkip> entities);

  // ===== FlowTemplate =====
  FlowTemplateVO entityToVO(FlowTemplate entity);

  List<FlowTemplateVO> flowTemplateListToVO(List<FlowTemplate> entities);

  // ===== FlowTimer =====
  FlowTimerVO entityToVO(FlowTimer entity);

  List<FlowTimerVO> flowTimerListToVO(List<FlowTimer> entities);

  // ===== FlowUser =====
  FlowUserVO entityToVO(FlowUser entity);

  List<FlowUserVO> flowUserListToVO(List<FlowUser> entities);

  /**
   * 委派授权 PostDTO → Entity（创建场景）。
   *
   * @param dto 创建委派授权的请求 DTO
   * @return 委托授权实体（系统字段由 DB 或 Service 层填充）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "authStatus", ignore = true)
  @Mapping(target = "providerTraceId", ignore = true)
  FlowDelegateAuth postDtoToEntity(FlowDelegateAuthPostDTO dto);

  /**
   * 委派授权 PutDTO → Entity（更新场景）。
   *
   * @param dto 更新委派授权的请求 DTO
   * @return 委托授权实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "authStatus", ignore = true)
  @Mapping(target = "providerTraceId", ignore = true)
  FlowDelegateAuth putDtoToEntity(FlowDelegateAuthPutDTO dto);

  // ===== VO → Entity (Repository 写入场景) =====

  /**
   * 流程实例 VO → 流程实例。
   *
   * @param vo 流程实例视图对象
   * @return 流程实例实体
   */
  FlowInstance entityToEntity(FlowInstanceVO vo);

/**
 * 运行时任务 VO → 运行时任务。
 *
 * @param vo 运行时任务视图对象
 * @return 运行时任务实体
 */
FlowRunTask entityToEntity(FlowRunTaskVO vo);

/**
 * 运行时任务 DTO → 运行时任务。
 *
 * @param dto 运行时任务请求 DTO
 * @return 运行时任务实体
 */
FlowRunTask dtoToEntity(FlowRunTaskDTO dto);

/**
 * 流程定义 DTO → 流程定义。
 *
 * @param dto 流程定义请求 DTO
 * @return 流程定义实体
 */
FlowDefinition dtoToEntity(FlowDefinitionDTO dto);

/**
 * 流程分类 DTO → 流程分类。
 *
 * @param dto 流程分类请求 DTO
 * @return 流程分类实体
 */
FlowCategory dtoToEntity(FlowCategoryDTO dto);

  /**
   * 流程节点 VO → 流程节点。
   *
   * @param vo 流程节点视图对象
   * @return 流程节点实体
   */
  @Mapping(target = "slaConfig", expression = "java(mapSlaConfig(vo.getSlaConfig()))")
  FlowNode entityToEntity(FlowNodeVO vo);

  /**
   * 将 SlaConfigVO 值对象序列化为 JSON 字符串。
   *
   * @param slaConfig SLA 配置值对象
   * @return JSON 字符串，null 时返回 null
   */
  default String mapSlaConfig(com.njydsz.workflow.domain.vo.SlaConfigVO slaConfig) {
    if (slaConfig == null) {
      return null;
    }
    return YdszJson.toJson(slaConfig);
  }

  /**
   * 流程定义 VO → 流程定义。
   *
   * @param vo 流程定义视图对象
   * @return 流程定义实体
   */
  FlowDefinition entityToEntity(FlowDefinitionVO vo);

  /**
   * 审计日志 VO → 审计日志。
   *
   * @param vo 审计日志视图对象
   * @return 审计日志实体
   */
  FlowAuditLog entityToEntity(FlowAuditLogVO vo);

  /**
   * 历史任务 VO → 历史任务。
   *
   * @param vo 历史任务视图对象
   * @return 历史任务实体
   */
  FlowHisTask entityToEntity(FlowHisTaskVO vo);

  /**
   * 定时器 VO → 定时器。
   *
   * @param vo 定时器视图对象
   * @return 定时器实体
   */
  FlowTimer entityToEntity(FlowTimerVO vo);

  /**
   * 事件订阅 VO → 事件订阅。
   *
   * @param vo 事件订阅视图对象
   * @return 事件订阅实体
   */
  FlowEventSubscription entityToEntity(FlowEventSubscriptionVO vo);

  /**
   * 自动触发 VO → 自动触发。
   *
   * @param vo 自动触发视图对象
   * @return 自动触发实体
   */
  FlowAutoTrigger entityToEntity(FlowAutoTriggerVO vo);

  /**
   * 附件 VO → 附件。
   *
   * @param vo 附件视图对象
   * @return 附件实体
   */
  FlowAttachment entityToEntity(FlowAttachmentVO vo);

  /**
   * 抄送 VO → 抄送。
   *
   * @param vo 抄送视图对象
   * @return 抄送实体
   */
  FlowCc entityToEntity(FlowCcVO vo);

  /**
   * 审批意见 VO → 审批意见。
   *
   * @param vo 审批意见视图对象
   * @return 审批意见实体
   */
  FlowComment entityToEntity(FlowCommentVO vo);

  /**
   * 流程分类 VO → 流程分类。
   *
   * @param vo 流程分类视图对象
   * @return 流程分类实体
   */
  FlowCategory entityToEntity(FlowCategoryVO vo);

  /**
   * 流程模板 VO → 流程模板。
   *
   * @param vo 流程模板视图对象
   * @return 流程模板实体
   */
  FlowTemplate entityToEntity(FlowTemplateVO vo);

  /**
   * 委托授权 VO → 委托授权。
   *
   * @param vo 委托授权视图对象
   * @return 委托授权实体
   */
  FlowDelegateAuth entityToEntity(FlowDelegateAuthVO vo);

  /**
   * 管理员角色 VO → 管理员角色。
   *
   * @param vo 管理员角色视图对象
   * @return 管理员角色实体
   */
  FlowAdminRole entityToEntity(FlowAdminRoleVO vo);

  /**
   * 节点跳转 VO → 节点跳转。
   *
   * @param vo 节点跳转视图对象
   * @return 节点跳转实体
   */
  FlowSkip entityToEntity(FlowSkipVO vo);

  /**
   * 流程用户 VO → 流程用户。
   *
   * @param vo 流程用户视图对象
   * @return 流程用户实体
   */
  FlowUser entityToEntity(FlowUserVO vo);

  // ===== entityToDO 别名方法（兼容旧代码中的 converter::entityToDO 方法引用） =====

  default FlowAdminRole entityToDO(FlowAdminRoleVO vo) {
    return entityToEntity(vo);
  }
  default FlowAttachment entityToDO(FlowAttachmentVO vo) {
    return entityToEntity(vo);
  }
  default FlowAuditLog entityToDO(FlowAuditLogVO vo) {
    return entityToEntity(vo);
  }
  default FlowAutoTrigger entityToDO(FlowAutoTriggerVO vo) {
    return entityToEntity(vo);
  }
  default FlowCategory entityToDO(FlowCategoryVO vo) {
    return entityToEntity(vo);
  }
  default FlowCc entityToDO(FlowCcVO vo) {
    return entityToEntity(vo);
  }
  default FlowCcRule entityToDO(FlowCcRuleVO vo) {
    return entityToEntity(vo);
  }
  default FlowComment entityToDO(FlowCommentVO vo) {
    return entityToEntity(vo);
  }
  default FlowDefinition entityToDO(FlowDefinitionVO vo) {
    return entityToEntity(vo);
  }
  default FlowDelegateAuth entityToDO(FlowDelegateAuthVO vo) {
    return entityToEntity(vo);
  }
  default FlowEventSubscription entityToDO(FlowEventSubscriptionVO vo) {
    return entityToEntity(vo);
  }
  default FlowHisInstance entityToDO(FlowHisInstanceVO vo) {
    return entityToEntity(vo);
  }
  default FlowHisTask entityToDO(FlowHisTaskVO vo) {
    return entityToEntity(vo);
  }
  default FlowInstance entityToDO(FlowInstanceVO vo) {
    return entityToEntity(vo);
  }
  default FlowNode entityToDO(FlowNodeVO vo) {
    return entityToEntity(vo);
  }
  default FlowQuickComment entityToDO(FlowQuickCommentVO vo) {
    return entityToEntity(vo);
  }
  default FlowRunTask entityToDO(FlowRunTaskVO vo) {
    return entityToEntity(vo);
  }
  default FlowSkip entityToDO(FlowSkipVO vo) {
    return entityToEntity(vo);
  }
  default FlowTemplate entityToDO(FlowTemplateVO vo) {
    return entityToEntity(vo);
  }
  default FlowTimer entityToDO(FlowTimerVO vo) {
    return entityToEntity(vo);
  }
  default FlowUser entityToDO(FlowUserVO vo) {
    return entityToEntity(vo);
  }

  // ===== Entity → DTO (updateById 场景) =====

  /**
   * 流程实例 → 流程实例 DTO（更新场景）。
   *
   * @param entity 流程实例实体
   * @return 流程实例 DTO
   */
  FlowInstanceDTO entityToDto(FlowInstance entity);

  /**
   * 流程实例 VO → 流程实例 DTO（更新场景，供 Repository.save 使用）。
   *
   * @param vo 流程实例视图对象
   * @return 流程实例 DTO
   */
  FlowInstanceDTO voToDto(FlowInstanceVO vo);

  /**
   * 运行时任务 → 运行时任务 DTO（更新场景，如有需要）。
   *
   * @param entity 运行时任务实体
   * @return 任务操作 DTO
   */
  FlowTaskOperateDTO runTaskToDto(FlowRunTask entity);

  // ===== String (通用字符串包装) =====
  /**
   * 字符串 → {@link StringVO}（如合并组 ID 包装）。
   *
   * @param value 原始字符串值
   * @return 包装后的 StringVO；null 时返回 null
   */
  default StringVO entityToVO(String value) {
    return value == null ? null : new StringVO(value);
  }

  /**
   * 字符串列表 → {@link StringVO} 列表（如已审批人 ID 列表包装）。
   *
   * @param values 原始字符串列表
   * @return 包装后的 StringVO 列表；null 时返回空列表
   */
  default List<StringVO> stringListToVO(List<String> values) {
    if (values == null) {
      return Collections.emptyList();
    }
    return values.stream().map(this::entityToVO).collect(Collectors.toList());
  }
}

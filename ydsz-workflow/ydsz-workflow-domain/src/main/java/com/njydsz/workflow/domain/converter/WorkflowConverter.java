package com.njydsz.workflow.domain.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.workflow.domain.dto.post.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.domain.dto.put.FlowDelegateAuthPutDTO;
import com.njydsz.workflow.infra.entity.FlowAdminRoleDO;
import com.njydsz.workflow.infra.entity.FlowAttachmentDO;
import com.njydsz.workflow.infra.entity.FlowAuditLogDO;
import com.njydsz.workflow.infra.entity.FlowAutoTriggerDO;
import com.njydsz.workflow.infra.entity.FlowCategoryDO;
import com.njydsz.workflow.infra.entity.FlowCcDO;
import com.njydsz.workflow.infra.entity.FlowCcRuleDO;
import com.njydsz.workflow.infra.entity.FlowCommentDO;
import com.njydsz.workflow.infra.entity.FlowDefinitionDO;
import com.njydsz.workflow.infra.entity.FlowDelegateAuthDO;
import com.njydsz.workflow.infra.entity.FlowEventSubscriptionDO;
import com.njydsz.workflow.infra.entity.FlowHisInstanceDO;
import com.njydsz.workflow.infra.entity.FlowHisTaskDO;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowQuickCommentDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.infra.entity.FlowSkipDO;
import com.njydsz.workflow.infra.entity.FlowTemplateDO;
import com.njydsz.workflow.infra.entity.FlowTimerDO;
import com.njydsz.workflow.infra.entity.FlowUserDO;
import com.njydsz.workflow.domain.vo.FlowAdminRoleVO;
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;
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
 * workflow 模块统一 MapStruct 转换器。
 *
 * <p>承担工作流模块所有 Entity ↔ VO、DTO → Entity 的类型转换，遵循单一转换器模式。 覆盖流程定义、流程实例、任务、审批日志、委托授权等核心实体的转换。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射
 *   <li>通过 {@link #INSTANT} 单例访问，零依赖注入
 *   <li>同名字段自动映射；系统字段通过 @Mapping(ignore = true) 忽略
 *   <li>entityToVO 方向自动排除敏感字段（如密码、Token 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface WorkflowConverter {

  WorkflowConverter INSTANT = Mappers.getMapper(WorkflowConverter.class);

  // ===== FlowAdminRoleDO =====
  FlowAdminRoleVO entityToVO(FlowAdminRoleDO entity);

  List<FlowAdminRoleVO> flowAdminRoleListToVO(List<FlowAdminRoleDO> entities);

  // ===== FlowAttachmentDO =====
  FlowAttachmentVO entityToVO(FlowAttachmentDO entity);

  List<FlowAttachmentVO> flowAttachmentListToVO(List<FlowAttachmentDO> entities);

  // ===== FlowAuditLogDO =====
  FlowAuditLogVO entityToVO(FlowAuditLogDO entity);

  List<FlowAuditLogVO> flowAuditLogListToVO(List<FlowAuditLogDO> entities);

  // ===== FlowAutoTriggerDO =====
  FlowAutoTriggerVO entityToVO(FlowAutoTriggerDO entity);

  List<FlowAutoTriggerVO> flowAutoTriggerListToVO(List<FlowAutoTriggerDO> entities);

  // ===== FlowCategoryDO =====
  FlowCategoryVO entityToVO(FlowCategoryDO entity);

  List<FlowCategoryVO> flowCategoryListToVO(List<FlowCategoryDO> entities);

  FlowCategoryTreeVO entityToTreeVO(FlowCategoryDO entity);

  List<FlowCategoryTreeVO> flowCategoryListToTreeVO(List<FlowCategoryDO> entities);

  // ===== FlowCcDO =====
  FlowCcVO entityToVO(FlowCcDO entity);

  List<FlowCcVO> flowCcListToVO(List<FlowCcDO> entities);

  // ===== FlowCcRuleDO =====
  FlowCcRuleVO entityToVO(FlowCcRuleDO entity);

  List<FlowCcRuleVO> flowCcRuleListToVO(List<FlowCcRuleDO> entities);

  // ===== FlowCommentDO =====
  FlowCommentVO entityToVO(FlowCommentDO entity);

  List<FlowCommentVO> flowCommentListToVO(List<FlowCommentDO> entities);

  // ===== FlowDefinitionDO =====
  FlowDefinitionVO entityToVO(FlowDefinitionDO entity);

  List<FlowDefinitionVO> flowDefinitionListToVO(List<FlowDefinitionDO> entities);

  // ===== FlowDelegateAuthDO =====
  FlowDelegateAuthVO entityToVO(FlowDelegateAuthDO entity);

  List<FlowDelegateAuthVO> flowDelegateAuthListToVO(List<FlowDelegateAuthDO> entities);

  // ===== FlowEventSubscriptionDO =====
  FlowEventSubscriptionVO entityToVO(FlowEventSubscriptionDO entity);

  List<FlowEventSubscriptionVO> flowEventSubscriptionListToVO(List<FlowEventSubscriptionDO> entities);

  // ===== FlowHisInstanceDO =====
  FlowHisInstanceVO entityToVO(FlowHisInstanceDO entity);

  List<FlowHisInstanceVO> flowHisInstanceListToVO(List<FlowHisInstanceDO> entities);

  // ===== FlowHisTaskDO =====
  FlowHisTaskVO entityToVO(FlowHisTaskDO entity);

  List<FlowHisTaskVO> flowHisTaskListToVO(List<FlowHisTaskDO> entities);

  // ===== FlowInstanceDO =====
  FlowInstanceVO entityToVO(FlowInstanceDO entity);

  List<FlowInstanceVO> flowInstanceListToVO(List<FlowInstanceDO> entities);

  // ===== FlowNodeDO =====
  FlowNodeVO entityToVO(FlowNodeDO entity);

  List<FlowNodeVO> flowNodeListToVO(List<FlowNodeDO> entities);

  // ===== FlowQuickCommentDO =====
  FlowQuickCommentVO entityToVO(FlowQuickCommentDO entity);

  List<FlowQuickCommentVO> flowQuickCommentListToVO(List<FlowQuickCommentDO> entities);

  // ===== FlowRunTaskDO =====
  FlowRunTaskVO entityToVO(FlowRunTaskDO entity);

  List<FlowRunTaskVO> flowRunTaskListToVO(List<FlowRunTaskDO> entities);

  // ===== FlowSkipDO =====
  FlowSkipVO entityToVO(FlowSkipDO entity);

  List<FlowSkipVO> flowSkipListToVO(List<FlowSkipDO> entities);

  // ===== FlowTemplateDO =====
  FlowTemplateVO entityToVO(FlowTemplateDO entity);

  List<FlowTemplateVO> flowTemplateListToVO(List<FlowTemplateDO> entities);

  // ===== FlowTimerDO =====
  FlowTimerVO entityToVO(FlowTimerDO entity);

  List<FlowTimerVO> flowTimerListToVO(List<FlowTimerDO> entities);

  // ===== FlowUserDO =====
  FlowUserVO entityToVO(FlowUserDO entity);

  List<FlowUserVO> flowUserListToVO(List<FlowUserDO> entities);

  /** 委派授权 PostDTO → Entity（创建场景）。 */
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
  FlowDelegateAuthDO postDtoToEntity(FlowDelegateAuthPostDTO dto);

  /** 委派授权 PutDTO → Entity（更新场景）。 */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "authStatus", ignore = true)
  @Mapping(target = "providerTraceId", ignore = true)
  FlowDelegateAuthDO putDtoToEntity(FlowDelegateAuthPutDTO dto);

  // ===== String (通用字符串包装) =====
  /** 字符串 → {@link StringVO}（如合并组 ID 包装）。 */
  default StringVO entityToVO(String value) {
    return value == null ? null : new StringVO(value);
  }

  /** 字符串列表 → {@link StringVO} 列表（如已审批人 ID 列表包装）。 */
  default List<StringVO> stringListToVO(List<String> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(this::entityToVO).collect(Collectors.toList());
  }
}

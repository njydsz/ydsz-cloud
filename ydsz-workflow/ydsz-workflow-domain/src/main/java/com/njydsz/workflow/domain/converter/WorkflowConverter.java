package com.njydsz.workflow.domain.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

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
import com.njydsz.workflow.domain.entity.FlowDmnDecision;
import com.njydsz.workflow.domain.entity.FlowDmnRule;
import com.njydsz.workflow.domain.entity.FlowEventSubscription;
import com.njydsz.workflow.domain.entity.FlowHisInstance;
import com.njydsz.workflow.domain.entity.FlowHisTask;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowQuickComment;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.entity.FlowSkip;
import com.njydsz.workflow.domain.entity.FlowTemplate;
import com.njydsz.workflow.domain.entity.FlowThirdPartyAccount;
import com.njydsz.workflow.domain.entity.FlowThirdPartyLog;
import com.njydsz.workflow.domain.entity.FlowTimer;
import com.njydsz.workflow.domain.entity.FlowUser;
import com.njydsz.workflow.domain.dto.post.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.domain.dto.put.FlowDelegateAuthPutDTO;
import com.njydsz.workflow.domain.vo.FlowAdminRoleVO;
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;
import com.njydsz.workflow.domain.vo.FlowCategoryVO;
import com.njydsz.workflow.domain.vo.FlowCcVO;
import com.njydsz.workflow.domain.vo.FlowCcRuleVO;
import com.njydsz.workflow.domain.vo.FlowCommentVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.domain.vo.FlowDmnDecisionVO;
import com.njydsz.workflow.domain.vo.FlowDmnRuleVO;
import com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO;
import com.njydsz.workflow.domain.vo.FlowHisInstanceVO;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowQuickCommentVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.domain.vo.FlowTemplateVO;
import com.njydsz.workflow.domain.vo.FlowThirdPartyAccountVO;
import com.njydsz.workflow.domain.vo.FlowThirdPartyLogVO;
import com.njydsz.workflow.domain.vo.FlowTimerVO;
import com.njydsz.workflow.domain.vo.FlowUserVO;
import com.njydsz.workflow.domain.vo.StringVO;

/**
 * workflow 模块统一 MapStruct 转换器。
 *
 * <p>承担工作流模块所有 Entity ↔ VO、DTO → Entity 的类型转换，遵循单一转换器模式。
 * 覆盖流程定义、流程实例、任务、审批日志、委托授权、DMN 决策等核心实体的转换。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射</li>
 *   <li>通过 {@link #INSTANT} 单例访问，零依赖注入</li>
 *   <li>同名字段自动映射；系统字段通过 @Mapping(ignore = true) 忽略</li>
 *   <li>entityToVO 方向自动排除敏感字段（如 FlowThirdPartyAccount 的密钥）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface WorkflowConverter {

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

    // ===== FlowCc =====
    FlowCcVO entityToVO(FlowCc entity);
    List<FlowCcVO> flowCcListToVO(List<FlowCc> entities);

    // ===== FlowCcRule =====
    FlowCcRuleVO entityToVO(FlowCcRule entity);
    List<FlowCcRuleVO> flowCcRuleListToVO(List<FlowCcRule> entities);

    // ===== FlowComment =====
    FlowCommentVO entityToVO(FlowComment entity);
    List<FlowCommentVO> flowCommentListToVO(List<FlowComment> entities);

    // ===== FlowDefinition =====
    FlowDefinitionVO entityToVO(FlowDefinition entity);
    List<FlowDefinitionVO> flowDefinitionListToVO(List<FlowDefinition> entities);

    // ===== FlowDelegateAuth =====
    FlowDelegateAuthVO entityToVO(FlowDelegateAuth entity);
    List<FlowDelegateAuthVO> flowDelegateAuthListToVO(List<FlowDelegateAuth> entities);

    // ===== FlowDmnDecision =====
    FlowDmnDecisionVO entityToVO(FlowDmnDecision entity);
    List<FlowDmnDecisionVO> flowDmnDecisionListToVO(List<FlowDmnDecision> entities);

    // ===== FlowDmnRule =====
    FlowDmnRuleVO entityToVO(FlowDmnRule entity);
    List<FlowDmnRuleVO> flowDmnRuleListToVO(List<FlowDmnRule> entities);

    // ===== FlowEventSubscription =====
    FlowEventSubscriptionVO entityToVO(FlowEventSubscription entity);
    List<FlowEventSubscriptionVO> flowEventSubscriptionListToVO(List<FlowEventSubscription> entities);

    // ===== FlowHisInstance =====
    FlowHisInstanceVO entityToVO(FlowHisInstance entity);
    List<FlowHisInstanceVO> flowHisInstanceListToVO(List<FlowHisInstance> entities);

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

    // ===== FlowThirdPartyAccount =====
    FlowThirdPartyAccountVO entityToVO(FlowThirdPartyAccount entity);
    List<FlowThirdPartyAccountVO> flowThirdPartyAccountListToVO(List<FlowThirdPartyAccount> entities);

    // ===== FlowThirdPartyLog =====
    FlowThirdPartyLogVO entityToVO(FlowThirdPartyLog entity);
    List<FlowThirdPartyLogVO> flowThirdPartyLogListToVO(List<FlowThirdPartyLog> entities);

    // ===== FlowTimer =====
    FlowTimerVO entityToVO(FlowTimer entity);
    List<FlowTimerVO> flowTimerListToVO(List<FlowTimer> entities);

    // ===== FlowUser =====
    FlowUserVO entityToVO(FlowUser entity);
    List<FlowUserVO> flowUserListToVO(List<FlowUser> entities);


    /**
     * 委派授权 PostDTO → Entity（创建场景）。
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
     */
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "authStatus", ignore = true)
    @Mapping(target = "providerTraceId", ignore = true)
    FlowDelegateAuth putDtoToEntity(FlowDelegateAuthPutDTO dto);

    // ===== String (通用字符串包装) =====
    /**
     * 字符串 → {@link StringVO}（如合并组 ID 包装）。
     */
    default StringVO entityToVO(String value) {
        return value == null ? null : new StringVO(value);
    }

    /**
     * 字符串列表 → {@link StringVO} 列表（如已审批人 ID 列表包装）。
     */
    default List<StringVO> stringListToVO(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(this::entityToVO).collect(Collectors.toList());
    }

}

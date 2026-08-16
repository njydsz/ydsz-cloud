package com.njydsz.workflow.domain.dto;

import com.njydsz.common.safe.annotation.Xss;
import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 自建工作流引擎 - 任务操作 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowTaskOperateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID（必填） */
  @NotNull(message = "{validation.workflow.msg_5a190a79}")
  private String taskId;

  /** 操作人 ID */
  @NotNull(message = "{validation.workflow.msg_f65f41e7}")
  private String userId;

  /** 操作人姓名 */
  @SensitiveData(SensitiveType.CHINESE_NAME)
  private String userName;

  /** 操作：PASS/REJECT/CLAIM/DELEGATE/TRANSFER/CC */
  private String action;

  /** 审批意见 */
  @Xss(message = "审批意见包含非法字符")
  private String comment;

  /** P2-42: 审批意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE（可选） */
  private String commentType;

  /** P1-6: 审批时提交的附件列表（图片/文档/视频等） */
  private List<FlowAttachmentDTO> attachments;

  /** 流程变量 */
  private Map<String, Object> variables;

  /**
   * 目标节点编码
   *
   * <p>多场景复用：
   *
   * <ul>
   *   <li>REJECT：单节点退回（向后兼容）
   *   <li>GAP-P2-9 自由流（JUMP）：运行时动态指定下一节点编码，目标节点必须存在于当前流程定义中， 且目标节点的 {@code
   *       ext.freeJump=true}（节点级白名单）才允许跳转
   * </ul>
   */
  private String targetNodeCode;

  /**
   * GAP-P0-2: 退回多节点同退目标节点编码列表（仅 REJECT 时使用）
   *
   * <p>对标飞书"退回多节点同退"：勾选多个前序节点均重新审批。 非空时优先于 {@link #targetNodeCode}；为空时降级到单节点退回（向后兼容）。
   */
  private List<String> targetNodeCodes;

  /**
   * P1-2: 退回到发起人快捷方式（仅 REJECT 时使用）
   *
   * <p>对标钉钉/飞书"退回到发起人"：将流程退回到开始节点后的第一个审批节点， 让发起人重新修改表单后再次提交。
   *
   * <p>为 true 时优先于 {@link #targetNodeCode} / {@link #targetNodeCodes}； 为 false 或 null
   * 时走原有退回逻辑（向后兼容）。
   */
  private Boolean rejectToInitiator;

  /**
   * GAP-P2-9: 自由流（JUMP）运行时指定目标节点办理人列表
   *
   * <p>对标钉钉/飞书"自由流"能力：跳转时可显式指定目标节点的办理人（用户 ID 字符串列表， 如 {@code ["1001","1002"]}）。非空时覆盖目标节点 {@code
   * permissionFlag} 解析出的默认办理人； 为空时回退到节点配置的办理人解析逻辑。
   *
   * <p>仅 {@code action=JUMP} 时生效，其他操作忽略该字段。
   */
  private List<String> targetAssignees;

  /** 转办/委派目标人 */
  private String targetUserId;

  /** 转办/委派目标人姓名 */
  @SensitiveData(SensitiveType.CHINESE_NAME)
  private String targetUserName;

  /** 租户 ID */
  private String tenantId;

  /** 链路追踪 ID */
  private String providerTraceId;
}

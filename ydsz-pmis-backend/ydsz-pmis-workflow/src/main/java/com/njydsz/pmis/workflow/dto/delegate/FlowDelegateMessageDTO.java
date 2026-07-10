package com.njydsz.pmis.workflow.dto.delegate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自建工作流引擎 - 委派沟通留言 DTO
 *
 * <p>P2-1 (GAP-08)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class FlowDelegateMessageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联任务 ID */
    @NotBlank(message = "关联任务 ID 不能为空")
    private String taskId;

    /** 关联实例 ID */
    private String instanceId;

    /** 关联节点编码 */
    private String nodeCode;

    /** 发送内容 */
    @NotBlank(message = "发送内容不能为空")
    private String content;

    /** 可选附件存储 key */
    private String attachmentKey;
}

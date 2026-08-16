package com.njydsz.workflow.domain.dto.put;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;

/**
 * FlowDelegateAuth 修改请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowDelegateAuthPutDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "授权人用户 ID（为空时从登录上下文兜底）")
    private String ownerUserId;

    @Schema(description = "授权人姓名")
    @SensitiveData(SensitiveType.CHINESE_NAME)
    private String ownerUserName;

    @NotNull(message = "被委托人用户 ID 不能为空")
    @Schema(description = "被委托人用户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String delegateUserId;

    @Schema(description = "被委托人姓名")
    @SensitiveData(SensitiveType.CHINESE_NAME)
    private String delegateUserName;

    @Schema(description = "授权范围: ALL/FLOW/NODE/ROLE")
    private String scopeType;

    @Schema(description = "流程编码（scopeType=FLOW 时指定）")
    private String flowCode;

    @Schema(description = "节点编码（scopeType=NODE 时指定）")
    private String nodeCode;

    @Schema(description = "角色编码（scopeType=ROLE 时指定）")
    private String roleCode;

    @NotNull(message = "开始时间不能为空")
    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;

    @Schema(description = "委派原因")
    private String reason;

}

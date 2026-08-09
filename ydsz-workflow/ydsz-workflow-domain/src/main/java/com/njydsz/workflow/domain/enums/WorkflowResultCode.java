package com.njydsz.workflow.domain.enums;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;
import com.njydsz.common.exception.registry.YdszResultCode;

/**
 * 工作流模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，通过 {@link ExceptionCodeRegistry} 全局注册，
 * 支持 i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 * <ul>
 *   <li>B70001-B70099 流程模板/定义</li>
 *   <li>B71001-B71099 流程实例</li>
 *   <li>B72001-B72099 任务</li>
 *   <li>B73001-B73099 委托授权</li>
 *   <li>B74001-B74099 分类/评论/附件</li>
 *   <li>B75001-B75099 SLA/催办
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszResultCode(module = "workflow", description = "工作流")
public enum WorkflowResultCode implements ExceptionCode {

    // ==================== B70001-B70099 流程模板/定义 ====================
    TEMPLATE_NOT_FOUND("B70001", "workflow.template.not.found", 404),
    TEMPLATE_CODE_DUPLICATE("B70002", "workflow.template.code.duplicate"),
    TEMPLATE_DEPLOYED_CANNOT_DELETE("B70003", "workflow.template.deployed.cannot.delete"),
    DEFINITION_NOT_FOUND("B70004", "workflow.definition.not.found", 404),
    BPMN_PARSE_ERROR("B70005", "workflow.bpmn.parse.error"),

    // ==================== B71001-B71099 流程实例 ====================
    INSTANCE_NOT_FOUND("B71001", "workflow.instance.not.found", 404),
    INSTANCE_STATUS_INVALID("B71002", "workflow.instance.status.invalid"),
    INSTANCE_ALREADY_FINISHED("B71003", "workflow.instance.already.finished"),

    // ==================== B72001-B72099 任务 ====================
    TASK_NOT_FOUND("B72001", "workflow.task.not.found", 404),
    TASK_NO_PERMISSION("B72002", "workflow.task.no.permission", 403),
    TASK_ALREADY_HANDLED("B72003", "workflow.task.already.handled"),
    TASK_APPROVER_DUPLICATE("B72004", "workflow.task.approver.duplicate"),

    // ==================== B73001-B73099 委托授权 ====================
    DELEGATE_AUTH_NOT_FOUND("B73001", "workflow.delegate.auth.not.found", 404),
    DELEGATE_AUTH_EXPIRED("B73002", "workflow.delegate.auth.expired"),

    // ==================== B74001-B74099 分类/评论/附件 ====================
    CATEGORY_NOT_FOUND("B74001", "workflow.category.not.found", 404),
    CATEGORY_CODE_DUPLICATE("B74002", "workflow.category.code.duplicate"),
    COMMENT_NOT_FOUND("B74003", "workflow.comment.not.found", 404),
    ATTACHMENT_NOT_FOUND("B74004", "workflow.attachment.not.found", 404),

    // ==================== B75001-B75099 SLA/催办 ====================
    SLA_NOT_FOUND("B75001", "workflow.sla.not.found", 404),
    SLA_OVERDUE("B75002", "workflow.sla.overdue"),
    URGE_TOO_FREQUENT("B75003", "workflow.urge.too.frequent", 429);

    /** 错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    WorkflowResultCode(String code, String key) {
        this(code, key, 400);
    }

    WorkflowResultCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    static {
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (WorkflowResultCode c : values()) {
            registryMap.put(c.getCode(), c);
        }
        ExceptionCodeRegistry.register(registryMap);
    }
}

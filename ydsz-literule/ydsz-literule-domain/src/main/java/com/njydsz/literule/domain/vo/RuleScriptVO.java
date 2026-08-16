package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 规则脚本视图对象（VO）。
 * <p>
 * 用于 Controller 层返回脚本规则的完整信息。脚本规则以自定义脚本语言
 * 编写逻辑，支持沙箱隔离执行，适用于复杂条件判断场景。
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleScriptVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 脚本规则唯一标识（主键） */
    private String id;
    /** 关联的规则编码 */
    private String ruleCode;
    /** 规则名称 */
    private String ruleName;
    /** 分类编码 */
    private String category;
    /** 描述 */
    private String description;
    /** 脚本内容（Groovy/GraalVM JS 等支持的脚本语言） */
    private String script;
    /** 默认严重级别（HIGH/MEDIUM/LOW/INFO） */
    private String defaultSeverity;
    /** 是否启用沙箱执行 */
    private Boolean sandboxEnabled;
    /** 优先级，数值越小优先级越高 */
    private Integer priority;
    /** 是否启用 */
    private Boolean enabled;
    /** 适用范围 */
    private String scope;
    /** 版本号 */
    private Integer version;
    /** 外部模型追踪 ID */
    private String providerTraceId;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}

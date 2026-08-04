package com.remisoft.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 规则脚本实体
 *
 * <p>脚本规则：script 字段为 Groovy 脚本源码，运行在沙箱中。
 * 通过 sandbox_enabled 控制是否启用沙箱安全限制。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "remi_rule_script", autoResultMap = true)
public class RuleScript extends MpBaseEntity<String> {

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则分类 */
    private String category;

    /** 规则描述 */
    private String description;

    /** Groovy 脚本源码 */
    private String script;

    /** 默认严重级别：INFO/WARN/ERROR/CRITICAL */
    private String defaultSeverity;

    /** 是否启用沙箱 */
    private Boolean sandboxEnabled;

    /** 优先级 */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 适用范围 */
    private String scope;

    /** 版本号 */
    private Integer version;

    /** 供应商侧追踪 ID */
    private String providerTraceId;
}

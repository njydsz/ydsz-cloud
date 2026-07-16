package com.njydsz.literule.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;

/**
 * 规则执行链路追踪实体
 *
 * @author ydsz
 * @since 2026-07-02
 */
@Data
@TableName(value = "ydsz_rule_execution_trace", autoResultMap = true)
public class RuleExecutionTraceDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 追踪 ID（同一批次评估共享） */
    private String traceId;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 业务场景 */
    private String scenario;

    /** 是否触发 */
    private Boolean triggered;

    /** 触发严重度 */
    private String severity;

    /** 条件表达式求值结果描述 */
    private String conditionResult;

    /** 执行耗时（毫秒） */
    private Long elapsedMs;

    /** 事实数据快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> factsSnapshot;

    /** 结果快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultSnapshot;

    /** 错误信息 */
    private String errorMessage;

    /** 创建人（VARCHAR(64) 支持工号/SSO 用户名，DEFAULT 'SYSTEM' 表示系统兜底） */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
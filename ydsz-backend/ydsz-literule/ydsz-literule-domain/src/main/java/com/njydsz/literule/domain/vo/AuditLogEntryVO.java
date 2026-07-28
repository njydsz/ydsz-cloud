package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

/**
 * AuditLogEntry 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AuditLogEntryVO {

    /** id */
    private String id;

    /** ruleCode */
    private String ruleCode;

    /** ruleName */
    private String ruleName;

    /** action */
    private String action;

    /** operator */
    private String operator;

    /** source */
    private String source;

    /** changeDesc */
    private String changeDesc;

    /** beforeSnapshot */
    private Map<String, Object> beforeSnapshot;

    /** afterSnapshot */
    private Map<String, Object> afterSnapshot;

    /** fieldDiffs */
    private Map<String, Object> fieldDiffs;

    /** result */
    private String result;

    /** errorMessage */
    private String errorMessage;

    /** createdAt */
    private LocalDateTime createdAt;

}

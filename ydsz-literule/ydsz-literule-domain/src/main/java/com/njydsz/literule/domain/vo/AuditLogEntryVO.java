package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 审计日志条目视图对象（VO）。
 *
 * <p>用于前端展示规则变更的审计轨迹，包含操作人、操作类型、 变更前/后快照及字段级差异，便于合规追溯与问题排查。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AuditLogEntryVO {

  /** 审计日志条目 ID（主键） */
  private String id;

  /** 关联规则编码 */
  private String ruleCode;

  /** 规则名称（快照，便于展示） */
  private String ruleName;

  /** 操作类型（如 CREATE/UPDATE/DELETE/TOGGLE） */
  private String action;

  /** 操作人（用户名） */
  private String operator;

  /** 操作来源（如 WEB/API，标识触发渠道） */
  private String source;

  /** 变更说明（人工填写或系统生成的描述） */
  private String changeDesc;

  /** 变更前快照（字段名 → 值），无变更前为空 */
  private Map<String, Object> beforeSnapshot;

  /** 变更后快照（字段名 → 值） */
  private Map<String, Object> afterSnapshot;

  /** 字段级差异（字段名 → 前后值对照） */
  private Map<String, Object> fieldDiffs;

  /** 操作结果（SUCCESS/FAIL） */
  private String result;

  /** 失败时的错误信息（result=FAIL 时有效） */
  private String errorMessage;

  /** 创建时间 */
  private LocalDateTime createdAt;
}

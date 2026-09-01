package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志视图对象（P1-14 操作审计视图）。
 *
 * <p>用于前端展示操作审计轨迹，包含操作人、操作行为、操作对象、时间、结果等关键信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class AuditLogVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 审计记录 ID */
  private String id;

  /** 审计类型编码 */
  private Integer auditType;

  /** 操作行为编码 */
  private Integer action;

  /** 模块名称 */
  private String module;

  /** 操作内容描述 */
  private String content;

  /** 业务流水号（通常为 jobKey 或 jobId） */
  private String businessNo;

  /** 操作人姓名 */
  private String operatorName;

  /** 操作时间 */
  private LocalDateTime operationTime;

  /** 请求 IP 地址 */
  private String ipAddress;

  /** 执行耗时（毫秒） */
  private Long costTime;

  /** 链路追踪 ID */
  private String traceId;

  // ==================== Getter / Setter ====================

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getAuditType() {
    return auditType;
  }

  public void setAuditType(Integer auditType) {
    this.auditType = auditType;
  }

  public Integer getAction() {
    return action;
  }

  public void setAction(Integer action) {
    this.action = action;
  }

  public String getModule() {
    return module;
  }

  public void setModule(String module) {
    this.module = module;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getBusinessNo() {
    return businessNo;
  }

  public void setBusinessNo(String businessNo) {
    this.businessNo = businessNo;
  }

  public String getOperatorName() {
    return operatorName;
  }

  public void setOperatorName(String operatorName) {
    this.operatorName = operatorName;
  }

  public LocalDateTime getOperationTime() {
    return operationTime;
  }

  public void setOperationTime(LocalDateTime operationTime) {
    this.operationTime = operationTime;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public Long getCostTime() {
    return costTime;
  }

  public void setCostTime(Long costTime) {
    this.costTime = costTime;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }
}

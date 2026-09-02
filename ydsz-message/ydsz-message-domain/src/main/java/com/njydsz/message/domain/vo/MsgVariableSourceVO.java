package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息变量数据源视图对象（VO）。
 *
 * <p>用于返回模板变量数据源的绑定信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgVariableSourceVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 变量数据源唯一标识（主键） */
  private String id;

  /** 模板编码 */
  private String templateCode;

  /** 变量名 */
  private String variableName;

  /** 数据源类型（BEAN/SQL/HTTP/STATIC） */
  private String sourceType;

  /** 数据源表达式 */
  private String sourceExpr;

  /** 缓存有效期（秒） */
  private Integer cacheTtl;

  /** 描述说明 */
  private String description;

  /** 租户 ID */
  private String tenantId;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}

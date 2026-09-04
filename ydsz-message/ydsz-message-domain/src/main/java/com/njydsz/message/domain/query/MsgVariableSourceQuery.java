package com.njydsz.message.domain.query;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息变量数据源分页查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgVariableSourceQuery extends PageQuery {

  /** 模板编码 */
  @Xss private String templateCode;

  /** 变量名 */
  @Xss private String variableName;

  /** 数据源类型（BEAN/SQL/HTTP/STATIC） */
  @Xss private String sourceType;

  /** 租户 ID */
  @Xss private String tenantId;
}

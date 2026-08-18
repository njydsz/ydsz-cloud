package com.njydsz.message.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息模板版本历史分页查询 Query。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgTemplateVersionQuery extends PageQuery {

  /** 模板编码 */
  @Xss private String templateCode;

  /** 审核状态（APPROVED/REJECTED） */
  @Xss private String auditStatus;
}

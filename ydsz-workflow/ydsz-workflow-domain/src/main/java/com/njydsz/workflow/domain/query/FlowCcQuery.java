package com.njydsz.workflow.domain.query;

import java.io.Serial;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 抄送查询参数。
 *
 * <p>P0-3: 抄送中心查询参数。 P1-7a: 继承 {@link PageQuery} 复用分页安全校验（@Min/@Max/@Pattern + safeOrderBy）。
 *
 * <p><b>命名合规说明（1.0.0 DDD 分层规范）：</b>查询请求参数置于 {@code query/} 包下、以 {@code Query} 结尾
 * （符合 §34.2.1 表格：query/ 查询请求参数 以 Query 结尾）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "抄送查询参数")
public class FlowCcQuery extends PageQuery {

  @Serial private static final long serialVersionUID = 1L;

  /** 已读状态：UNREAD / READ / null=全部 */
  private String readStatus;

  /** 流程编码过滤 */
  private String flowCode;
}

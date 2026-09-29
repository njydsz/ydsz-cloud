package com.njydsz.message.domain.vo;

/**
 * 消息日志视图层级（P1-B: 配合 @JsonView 实现字段分级输出）。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * // 列表接口 — 仅返回 Summary 字段（核心追踪信息）
 * @JsonView(MsgLogViews.Summary.class)
 * public YdszResponse<PageResponse<List<MsgLogVO>>> pageLog(Query q) { ... }
 *
 * // 详情接口 — 返回全部字段（含诊断/扩展信息）
 * @JsonView(MsgLogViews.Detail.class)
 * public YdszResponse<MsgLogVO> getLog(String id) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class MsgLogViews {

  private MsgLogViews() {}

  /** 列表视图：核心追踪信息（ID、通道、业务、状态、成本、创建时间为列表页必须字段）。 */
  public interface Summary {}

  /** 详情视图：继承 Summary，额外包含诊断信息（错误、重试、扩展参数等）。 */
  public interface Detail extends Summary {}
}

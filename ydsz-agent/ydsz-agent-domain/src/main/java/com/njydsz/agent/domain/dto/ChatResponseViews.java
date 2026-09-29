package com.njydsz.agent.domain.dto;

/**
 * 对话响应视图层级（P1-B: 配合 @JsonView 实现字段分级输出）。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * // 同步对话详情 — 返回全部字段（含 Token 用量、模型信息）
 * @JsonView(ChatResponseViews.Detail.class)
 * public YdszResponse<ChatResponseDTO> chat(@RequestBody ChatRequestDTO req) { ... }
 *
 * // 轻量对话摘要列表 — 仅返回核心字段（对话 ID + 内容 + 响应时间）
 * @JsonView(ChatResponseViews.Summary.class)
 * public YdszResponse<List<ChatResponseDTO>> listChats(String convId) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class ChatResponseViews {

  private ChatResponseViews() {}

  /** 摘要视图：核心响应字段（对话 ID + 内容 + 响应时间）。 */
  public interface Summary {}

  /** 详情视图：继承 Summary，额外包含模型信息和 Token 用量统计。 */
  public interface Detail extends Summary {}
}

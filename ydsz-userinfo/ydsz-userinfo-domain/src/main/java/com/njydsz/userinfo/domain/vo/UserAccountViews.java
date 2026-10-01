package com.njydsz.userinfo.domain.vo;

/**
 * 用户账号视图层级（P1-B: 配合 @JsonView 实现字段分级输出）。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * // 用户列表接口 — 仅返回公开信息（ID、用户名、姓名、头像、状态）
 * @JsonView(UserAccountViews.Summary.class)
 * public YdszResponse<PageResponse<List<UserAccountVO>>> pageUser(Query q) { ... }
 *
 * // 用户详情/管理接口 — 返回全部字段（含联系方式、组织归属、安全字段）
 * @JsonView(UserAccountViews.Detail.class)
 * public YdszResponse<UserAccountVO> getUser(String id) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public final class UserAccountViews {

  private UserAccountViews() {}

  /** 摘要视图：公开信息（ID、用户名、姓名、头像、状态）。 */
  public interface Summary {}

  /** 详情视图：继承 Summary，额外包含联系方式、组织归属、安全审计等内部字段。 */
  public interface Detail extends Summary {}
}

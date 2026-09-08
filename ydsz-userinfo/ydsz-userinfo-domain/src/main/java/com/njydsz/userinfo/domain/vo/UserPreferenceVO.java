package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 用户偏好 VO
 *
 * <p>供 {@code GET /api/user/preferences} 与 {@code POST /api/user/preferences/reset}
 * 返回，字段与 {@link com.njydsz.userinfo.domain.dto.UserPreferenceDTO} 对齐。
 * 重置场景返回全默认值的空 VO（前端按缺省字段回退本地默认）。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see com.njydsz.userinfo.server.service.UserPreferenceService 读写入口
 */
@Data
public class UserPreferenceVO {

  /** 默认首页路径 */
  private String defaultIndex;

  /** 语言设置（如 zh-CN / en-US） */
  private String language;

  /** 主题模式（light / dark / auto） */
  private String theme;

  /** 主题色 */
  private String themeColor;

  /** 菜单布局 */
  private String menuLayout;

  /** 菜单手风琴 */
  private Boolean accordionMenu;

  /** 表格密度 */
  private String tableSize;

  /** 字体大小 */
  private String fontSize;
}

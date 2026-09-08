package com.njydsz.userinfo.domain.dto;

import lombok.Data;

/**
 * 用户偏好保存 DTO
 *
 * <p>前端用户偏好（默认首页/语言/主题/布局等）的写入载体，
 * 对齐前端 {@code UserPreferenceDTO} 契约。字段全部可选，
 * 后端整体覆盖式保存（PUT 语义）。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see com.njydsz.userinfo.web.controller.UserPreferenceController 写入入口
 */
@Data
public class UserPreferenceDTO {

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

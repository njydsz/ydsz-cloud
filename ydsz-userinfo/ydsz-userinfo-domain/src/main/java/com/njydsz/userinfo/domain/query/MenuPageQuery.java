package com.njydsz.userinfo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 菜单分页查询参数，继承 {@link PageQuery} 提供分页基础字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MenuPageQuery extends PageQuery {

  /** 菜单编码，模糊查询 */
  private String menuCode;

  /** 菜单名称，模糊查询 */
  private String menuName;

  /** 菜单类型过滤：DIRECTORY/MENU/BUTTON */
  private String menuType;

  /** 状态过滤：ENABLE/DISABLE */
  private String status;
}

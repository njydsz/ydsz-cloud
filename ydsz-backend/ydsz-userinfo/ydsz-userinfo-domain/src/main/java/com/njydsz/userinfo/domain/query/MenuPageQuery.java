package com.njydsz.userinfo.domain.query;

import com.njydsz.common.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 菜单分页查询参数。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MenuPageQuery extends PageQuery {

    private String menuCode;
    private String menuName;
    private String menuType;
    private String status;
}
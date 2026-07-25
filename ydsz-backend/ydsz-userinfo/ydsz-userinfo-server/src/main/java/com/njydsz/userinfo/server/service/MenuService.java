package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.MenuDO;

/**
 * Menu service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MenuService {

    MenuDO getById(String id);
    List<MenuDO> list();
    String save(MenuDO entity);
    boolean updateById(MenuDO entity);
    boolean removeById(String id);
}

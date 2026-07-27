package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.MenuSaveDTO;
import com.njydsz.userinfo.domain.entity.Menu;
import com.njydsz.userinfo.domain.query.MenuPageQuery;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;

/**
 * 菜单 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MenuService extends BaseCrudService<Menu, MenuSaveDTO, MenuVO, MenuPageQuery, String> {

    /**
     * 查询全部菜单列表。
     *
     * @return 菜单视图对象列表
     */
    List<MenuVO> list();

    /**
     * 查询菜单树形结构。
     *
     * @return 菜单树形结构列表
     */
    List<MenuTreeVO> tree();
}
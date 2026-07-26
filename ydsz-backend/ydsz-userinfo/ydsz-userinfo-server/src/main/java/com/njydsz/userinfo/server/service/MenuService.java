package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.MenuSaveDTO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;

/**
 * 菜单 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MenuService {

    MenuVO getById(String id);
    List<MenuVO> list();
    String create(MenuSaveDTO dto);
    boolean update(MenuSaveDTO dto);
    boolean removeById(String id);
    List<MenuTreeVO> tree();
}

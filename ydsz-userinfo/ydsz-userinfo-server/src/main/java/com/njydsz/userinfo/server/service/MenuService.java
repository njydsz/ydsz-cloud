package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.create.MenuCreateDTO;
import com.njydsz.userinfo.domain.dto.update.MenuUpdateDTO;
import com.njydsz.userinfo.domain.entity.Menu;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;

/**
 * 菜单/权限 Service 接口
 *
 * <p>封装菜单的完整业务逻辑：CRUD、菜单树查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see Menu 菜单实体
 */
public interface MenuService {

  /**
   * 根据 ID 查询菜单详情。
   *
   * @param id 菜单 ID
   * @return 菜单 VO
   */
  MenuVO getById(String id);

  /**
   * 查询全部菜单列表（扁平结构）。
   *
   * @return 菜单 VO 列表
   */
  List<MenuVO> list();

  /**
   * 查询菜单树形结构。
   *
   * @return 菜单树形结构列表
   */
  List<MenuTreeVO> tree();

  /**
   * 创建菜单。
   *
   * @param dto 菜单创建 DTO
   * @return 新菜单 ID
   */
  String create(MenuCreateDTO dto);

  /**
   * 更新菜单。
   *
   * @param dto 菜单更新 DTO（含 ID）
   * @return true=成功
   */
  boolean update(MenuUpdateDTO dto);

  /**
   * 删除菜单（逻辑删除）。
   *
   * @param id 菜单 ID
   * @return true=成功
   */
  boolean removeById(String id);
}

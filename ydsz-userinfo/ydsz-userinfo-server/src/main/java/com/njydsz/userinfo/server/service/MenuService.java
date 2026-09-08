package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;

import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.vo.MenuRouteVO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;

/**
 * 菜单/权限 Service 接口
 *
 * <p>封装菜单的完整业务逻辑：CRUD、菜单树查询、按角色构建前端动态路由树。
 *
 * @author ydsz-team
 * @since 26.09.01
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
   * @param dto 菜单 DTO
   * @return 新菜单 ID
   */
  String create(MenuDTO dto);

  /**
   * 更新菜单。
   *
   * @param dto 菜单 DTO（含 ID）
   * @return true=成功
   */
  boolean update(MenuDTO dto);

  /**
   * 删除菜单（逻辑删除）。
   *
   * @param id 菜单 ID
   * @return true=成功
   */
  boolean removeById(String id);

  /**
   * 按角色编码集合构建当前可访问的前端动态路由树。
   *
   * <p>角色编码 → 菜单权限 ID 并集 → 过滤启用中的目录/菜单节点（排除按钮/接口权限点）→
   * 按 {@code parentId} 构建路由树并转换为前端路由形态。
   *
   * @param roleCodes 用户角色编码集合（来自认证上下文）
   * @return 前端动态路由列表（vben 路由形态）；无角色或无菜单权限时返回空列表
   */
  List<MenuRouteVO> routesForRoles(Collection<String> roleCodes);
}

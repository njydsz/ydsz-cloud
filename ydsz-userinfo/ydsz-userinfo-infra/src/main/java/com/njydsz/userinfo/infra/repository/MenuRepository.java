package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;

import com.njydsz.userinfo.domain.entity.Menu;

/**
 * 菜单/权限 Repository 接口
 *
 * <p>封装菜单表（{@code ydsz_menu}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MenuRepository {

  /**
   * 根据 ID 查询菜单。
   *
   * @param id 菜单 ID
   * @return 菜单实体，不存在时返回 null
   */
  Menu findById(String id);

  /**
   * 根据 ID 集合批量查询菜单。
   *
   * @param ids 菜单 ID 集合
   * @return 菜单列表
   */
  List<Menu> findByIds(Collection<String> ids);

  /**
   * 根据父级 ID 查询子菜单列表。
   *
   * @param parentId 父级菜单 ID
   * @return 子菜单列表
   */
  List<Menu> findByParentId(String parentId);

  /**
   * 条件查询菜单列表。
   *
   * @param wrapper 查询条件
   * @return 菜单列表
   */
  List<Menu> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Menu> wrapper);

  /**
   * 保存菜单（插入）。
   *
   * @param entity 菜单实体
   * @return 插入影响的行数
   */
  int insert(Menu entity);

  /**
   * 更新菜单。
   *
   * @param entity 菜单实体
   * @return 更新影响的行数
   */
  int updateById(Menu entity);

  /**
   * 删除菜单（逻辑删除）。
   *
   * @param id 菜单 ID
   * @return 删除影响的行数
   */
  int deleteById(String id);

  /**
   * 条件删除菜单。
   *
   * @param wrapper 删除条件
   * @return 删除影响的行数
   */
  int delete(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Menu> wrapper);

  /**
   * 统计符合条件的菜单数量。
   *
   * @param wrapper 查询条件
   * @return 菜单数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Menu> wrapper);
}

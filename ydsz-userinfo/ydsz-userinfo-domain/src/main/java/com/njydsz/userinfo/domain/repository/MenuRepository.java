package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.query.MenuPageQuery;
import com.njydsz.userinfo.domain.vo.MenuVO;

/**
 * 菜单/权限 Repository 接口
 *
 * <p>封装菜单表（{@code ydsz_rbac_menu}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>入参为 DTO / Query / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MenuRepository {

  /**
   * 根据 ID 查询菜单。
   *
   * @param id 菜单 ID
   * @return 菜单 VO
   */
  Optional<MenuVO> findById(String id);

  /**
   * 根据 ID 集合批量查询菜单。
   *
   * @param ids 菜单 ID 集合
   * @return 菜单列表
   */
  List<MenuVO> findByIds(Collection<String> ids);

  /**
   * 根据父级 ID 查询子菜单列表。
   *
   * @param parentId 父级菜单 ID
   * @return 子菜单列表
   */
  List<MenuVO> findByParentId(String parentId);

  /**
   * 分页查询菜单列表。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  PageResponse<List<MenuVO>> page(MenuPageQuery query);

  /**
   * 条件查询菜单列表。
   *
   * @param query 查询参数
   * @return 菜单列表
   */
  List<MenuVO> list(MenuPageQuery query);

  /**
   * 保存菜单（创建或更新）。
   *
   * <p>统一 DTO：创建时 {@code id} 可不传，更新时 {@code id} 必填。
   *
   * @param dto 菜单 DTO
   * @return 保存后的菜单 VO
   */
  MenuVO save(MenuDTO dto);

  /**
   * 根据 ID 删除菜单（逻辑删除）。
   *
   * @param id 菜单 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);

  /**
   * 统计符合条件的菜单数量。
   *
   * @param query 查询参数
   * @return 菜单数量
   */
  long countByQuery(MenuPageQuery query);
}

package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.event.PermissionChangeNotifier;
import com.njydsz.common.domain.tree.TreeBuilder;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.MenuPageQuery;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.domain.repository.MenuRepository;
import com.njydsz.userinfo.server.auth.DbRolePermissionLoader;
import com.njydsz.userinfo.server.service.MenuService;

/**
 * 菜单 Service 实现
 *
 * <p>实现 {@link MenuService} 接口，封装菜单的完整业务逻辑：CRUD、树形结构构建。 菜单（{@code ydsz_menu}）是 RBAC 模型中最细粒度的「权限点」，
 * 既可表示前端路由节点，也可表示后端接口权限码。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>菜单 CRUD（含 {@code parentId} 树形关联）
 *   <li>菜单全量列表查询（按 {@code sortOrder} 倒序，前端表格展示）
 *   <li>菜单树形结构查询（递归构建父子关系）
 *   <li>删除前置校验（有子菜单时禁止删除，避免悬挂引用）
 *   <li>变更后触发权限缓存失效
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById}） 开启 {@code @Transactional(rollbackFor =
 * Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MenuService Service 接口
 * @see com.njydsz.userinfo.web.controller.MenuController 菜单 Controller
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

  /** 菜单 Repository */
  private final MenuRepository menuRepository;

  /** 权限变更事件发布器（common-auth，通知 Gateway 等节点刷新权限缓存） */
  private final PermissionChangeNotifier permissionChangeNotifier;

  /** 角色权限 DB 结果缓存加载器（菜单变更影响全部角色，全量失效） */
  private final DbRolePermissionLoader permissionLoader;

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当菜单不存在或已删除时抛出
   */
  @Override
  public MenuVO getById(String id) {
    return menuRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.MENU_NOT_FOUND));
  }

  /**
   * {@inheritDoc}
   *
   * @return 全部未删除菜单列表（按 sortOrder 降序）
   */
  @Override
  public List<MenuVO> list() {
    MenuPageQuery query = new MenuPageQuery();
    return menuRepository.list(query);
  }

  /**
   * {@inheritDoc}
   *
   * <p>status 默认 ENABLED，parentId 为空时默认 "0"（根节点）。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(MenuDTO dto) {
    MenuVO vo = menuRepository.create(dto);
    log.info("Menu created: code={}, id={}", dto.getMenuCode(), vo.getId());
    invalidatePermissionCache();
    return vo.getId();
  }

  /**
   * {@inheritDoc}
   *
   * <p>使用 MapStruct 转换（更新操作暂保留 BeanUtils）
   *
   * @throws BusinessException 当菜单不存在或已删除时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(MenuDTO dto) {
    MenuVO existing = menuRepository.findById(dto.getId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.MENU_NOT_FOUND));
    BeanUpdateUtil.copyNonNull(dto, existing, "id");
    MenuVO vo = menuRepository.update(dto);
    if (vo != null) {
      invalidatePermissionCache();
    }
    return vo != null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>删除前检查：有子菜单不可删除。
   *
   * @throws BusinessException 当菜单不存在、或有子菜单时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    menuRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.MENU_NOT_FOUND));
    // 检查子菜单
    MenuPageQuery childQuery = new MenuPageQuery();
    childQuery.setParentId(id);
    if (menuRepository.countByQuery(childQuery) > 0) {
      throw new BusinessException(UserInfoExceptionCode.MENU_HAS_CHILDREN);
    }
    boolean result = menuRepository.deleteById(id);
    if (result) {
      invalidatePermissionCache();
    }
    return result;
  }

  /**
   * 菜单变更后失效权限缓存。
   *
   * <p>菜单是 RBAC 的权限点，任意菜单变更（增删改）都影响全部角色的权限集合， 因此全量失效 DB 结果缓存并广播权限变更事件（通知 Gateway 等节点）。
   */
  private void invalidatePermissionCache() {
    try {
      permissionLoader.invalidateAll();
      permissionChangeNotifier.notifyMenuChanged();
    } catch (Exception e) {
      log.warn("Failed to invalidate permission cache after Menu change: {}", e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>查询全部未删除菜单，通过 {@link TreeBuilder#buildSimple} 构建树形结构。
   *
   * @return 菜单树形结构列表，空数据返回空列表
   */
  @Override
  public List<MenuTreeVO> tree() {
    List<MenuVO> all = menuRepository.list(new MenuPageQuery());
    if (all.isEmpty()) {
      return List.of();
    }

    List<MenuTreeVO> voList =
        all.stream()
            .map(menuVO -> {
              MenuTreeVO vo = new MenuTreeVO();
              BeanUtils.copyProperties(menuVO, vo);
              return vo;
            })
            .collect(Collectors.toList());

    return TreeBuilder.buildSimple(
        voList,
        MenuTreeVO::getId,
        MenuTreeVO::getParentId,
        MenuTreeVO::setChildren,
        MenuTreeVO::getSortOrder);
  }
}

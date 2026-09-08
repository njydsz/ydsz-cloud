package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.event.PermissionChangeNotifier;
import com.njydsz.common.domain.tree.TreeBuilder;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.MenuPageQuery;
import com.njydsz.userinfo.domain.repository.MenuRepository;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.server.auth.DbRolePermissionLoader;
import com.njydsz.userinfo.server.service.MenuService;

/**
 * 菜单 Service 实现
 *
 * <p>实现 {@link MenuService} 接口，封装菜单的完整业务逻辑：CRUD、树形结构构建。 菜单（{@code ydsz_rbac_menu}）是 RBAC 模型中最细粒度的「权限点」，
 * 既可表示前端路由节点，也可表示后端接口权限码。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>菜单 CRUD（含 {@code parentId} 树形关联）
 *   <li>菜单全量列表查询（按 {@code sort} 倒序，前端表格展示）
 *   <li>菜单树形结构查询（递归构建父子关系）
 *   <li>按角色构建前端动态路由树（{@code /api/menu/routes} 数据源）
 *   <li>删除前置校验（有子菜单时禁止删除，避免悬挂引用）
 *   <li>变更后触发权限缓存失效
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById}） 开启 {@code @Transactional(rollbackFor =
 * Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see MenuService Service 接口
 * @see com.njydsz.userinfo.web.controller.MenuController 菜单 Controller
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

  /** 启用状态 */
  private static final String STATUS_ENABLED = "ENABLED";

  /** 权限点类型：按钮（不参与路由树） */
  private static final String MENU_TYPE_BUTTON = "BUTTON";

  /** 权限点类型：接口（不参与路由树） */
  private static final String MENU_TYPE_API = "API";

  /** 根节点父 ID 约定值 */
  private static final String ROOT_PARENT_ID = "0";

  /** Map 初始容量 */
  private static final int CAPACITY = 16;

  /** 菜单 Repository */
  private final MenuRepository menuRepository;

  /** 角色 Repository（角色编码 → 角色 ID 解析） */
  private final RoleRepository roleRepository;

  /** 角色-权限关联 Repository（角色 ID → 菜单权限 ID 列表） */
  private final RolePermissionRepository rolePermissionRepository;

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
   * @return 全部未删除菜单列表（按 sort 降序）
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
    if (dto.getStatus() == null || dto.getStatus().isBlank()) {
      dto.setStatus("ENABLED");
    }
    MenuVO vo = menuRepository.save(dto);
    log.info("Menu created: code={}, id={}", dto.getMenuCode(), vo.getId());
    invalidatePermissionCache();
    return vo.getId();
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当菜单不存在或已删除时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(MenuDTO dto) {
    menuRepository.findById(dto.getId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.MENU_NOT_FOUND));
    MenuVO vo = menuRepository.save(dto);
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
              vo.setId(menuVO.getId());
              vo.setParentId(menuVO.getParentId());
              vo.setMenuName(menuVO.getMenuName());
              vo.setMenuCode(menuVO.getMenuCode());
              vo.setMenuType(menuVO.getMenuType());
              vo.setPath(menuVO.getPath());
              vo.setComponent(menuVO.getComponent());
              vo.setIcon(menuVO.getIcon());
              vo.setSort(menuVO.getSort());
              vo.setPermissionCode(menuVO.getPermissionCode());
              vo.setVisible(menuVO.getVisible());
              vo.setStatus(menuVO.getStatus());
              return vo;
            })
            .collect(Collectors.toList());

    return TreeBuilder.buildSimple(
        voList,
        MenuTreeVO::getId,
        MenuTreeVO::getParentId,
        MenuTreeVO::setChildren,
        MenuTreeVO::getSort);
  }

  /**
   * {@inheritDoc}
   *
   * <p>处理流程：角色编码解析为角色 ID → 汇总各角色的菜单权限 ID 并集 →
   * 查询菜单详情并过滤（启用中、排除 BUTTON/API 权限点）→ 按 {@code parentId} 递归构建路由树。
   *
   * <p>角色编码不存在或未分配任何菜单时返回空列表，不抛异常（前端回退静态路由）。
   */
  @Override
  public List<MenuRouteVO> routesForRoles(Collection<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
      return List.of();
    }
    Set<String> permissionIds = collectPermissionIds(roleCodes);
    if (permissionIds.isEmpty()) {
      return List.of();
    }

    List<MenuVO> menus =
        menuRepository.findByIds(permissionIds).stream()
            .filter(menu -> STATUS_ENABLED.equals(menu.getStatus()))
            .filter(
                menu ->
                    !MENU_TYPE_BUTTON.equals(menu.getMenuType())
                        && !MENU_TYPE_API.equals(menu.getMenuType()))
            .collect(Collectors.toList());
    if (menus.isEmpty()) {
      return List.of();
    }
    return buildRouteTree(menus);
  }

  /**
   * 汇总角色编码集合对应的菜单权限 ID 并集。
   *
   * @param roleCodes 角色编码集合
   * @return 菜单权限 ID 并集（可能为空）
   */
  private Set<String> collectPermissionIds(Collection<String> roleCodes) {
    Set<String> permissionIds = new HashSet<>(CAPACITY);
    for (String roleCode : roleCodes) {
      if (roleCode == null || roleCode.isBlank()) {
        continue;
      }
      roleRepository
          .findByRoleCode(roleCode.trim())
          .ifPresent(
              role ->
                  permissionIds.addAll(
                      rolePermissionRepository.findPermissionIdsByRoleId(role.getId())));
    }
    return permissionIds;
  }

  /**
   * 将扁平菜单列表构建为路由树。
   *
   * <p>按 {@code parentId} 分组索引，从根节点（{@code parentId} 为空或 {@code "0"}）递归展开，
   * 同层节点按 {@code sort} 升序排列（空值排最后）。
   *
   * @param menus 已过滤的菜单列表（启用中的目录/菜单节点）
   * @return 路由树根节点列表
   */
  private List<MenuRouteVO> buildRouteTree(List<MenuVO> menus) {
    Map<String, List<MenuVO>> childrenIndex =
        menus.stream()
            .collect(
                Collectors.groupingBy(
                    menu -> normalizeParentId(menu.getParentId()), LinkedHashMap::new,
                    Collectors.toList()));
    List<MenuVO> roots =
        childrenIndex.getOrDefault(ROOT_PARENT_ID, List.of()).stream()
            .sorted(bySortAsc())
            .collect(Collectors.toList());
    List<MenuRouteVO> routeTree = new ArrayList<>(roots.size());
    for (MenuVO root : roots) {
      routeTree.add(toRoute(root, childrenIndex));
    }
    return routeTree;
  }

  /** 归一化父 ID：空值视为根节点约定值 {@code "0"}。 */
  private String normalizeParentId(String parentId) {
    return (parentId == null || parentId.isBlank()) ? ROOT_PARENT_ID : parentId;
  }

  /** 菜单按 {@code sort} 升序比较器（空值排最后）。 */
  private Comparator<MenuVO> bySortAsc() {
    return Comparator.comparing(
        MenuVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()));
  }

  /**
   * 递归转换菜单节点为路由 VO。
   *
   * @param menu 当前菜单
   * @param childrenIndex 按 {@code parentId} 分组的子菜单索引
   * @return 路由 VO（含递归子节点）
   */
  private MenuRouteVO toRoute(MenuVO menu, Map<String, List<MenuVO>> childrenIndex) {
    MenuRouteVO route = new MenuRouteVO();
    route.setName(
        (menu.getMenuCode() == null || menu.getMenuCode().isBlank())
            ? "menu-" + menu.getId()
            : menu.getMenuCode());
    route.setPath(menu.getPath());
    route.setComponent(menu.getComponent());

    MenuRouteVO.Meta meta = new MenuRouteVO.Meta();
    meta.setTitle(menu.getMenuName());
    meta.setIcon(menu.getIcon());
    meta.setOrder(menu.getSort());
    meta.setHideInMenu(menu.getVisible() != null && menu.getVisible() == 0);
    route.setMeta(meta);

    List<MenuVO> children =
        childrenIndex.getOrDefault(menu.getId(), List.of()).stream()
            .sorted(bySortAsc())
            .collect(Collectors.toList());
    if (!children.isEmpty()) {
      List<MenuRouteVO> childRoutes = new ArrayList<>(children.size());
      for (MenuVO child : children) {
        childRoutes.add(toRoute(child, childrenIndex));
      }
      route.setChildren(childRoutes);
    }
    return route;
  }
}

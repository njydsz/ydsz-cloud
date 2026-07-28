package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.MenuSaveDTO;
import com.njydsz.userinfo.domain.entity.Menu;
import com.njydsz.userinfo.domain.query.MenuPageQuery;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;

/**
 * 菜单/权限 Service 接口
 *
 * <p>封装菜单的完整业务逻辑：CRUD、菜单树查询。
 * 菜单（{@code ydsz_menu}）是 RBAC 模型中最细粒度的「权限点」，
 * 既可表示前端路由节点，也可表示后端接口权限码（如 {@code system:user:create}）。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>菜单 CRUD（继承自 {@link BaseCrudService}）</li>
 *   <li>菜单全量列表查询（{@code list}，按 {@code sortOrder} 升序）</li>
 *   <li>菜单树形结构查询（{@code tree}，递归构建父子关系）</li>
 *   <li>删除前置校验（被角色引用的菜单禁止删除，避免悬挂引用）</li>
 *   <li>变更后触发权限缓存失效（由 {@code PermissionCacheInvalidator} 处理）</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <ul>
 *   <li>前端管理系统调用 {@code /api/v1/menu/tree} 渲染菜单树</li>
 *   <li>用户登录后调用 {@code /api/v1/menu/current} 获取当前用户可见菜单</li>
 *   <li>角色-权限分配页面通过 {@code /api/v1/menu/list} 加载可选权限</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see Menu 菜单实体
 * @see com.njydsz.userinfo.web.controller.MenuController 菜单 Controller
 */
public interface MenuService extends BaseCrudService<Menu, MenuSaveDTO, MenuVO, MenuPageQuery, String> {

    /**
     * 查询全部菜单列表。
     *
     * <p>返回扁平列表（不嵌套 children），按 {@code sortOrder} 升序。
     * 适用于角色-权限分配页面加载可选权限列表。
     *
     * @return 菜单 VO 列表（按 {@code sortOrder} 升序）
     */
    List<MenuVO> list();

    /**
     * 查询菜单树形结构。
     *
     * <p>递归构建父子关系：根节点 {@code parentId = "0"} → 子节点 → 孙节点。
     * 适用于前端管理系统渲染菜单树。
     *
     * @return 菜单树形结构列表（根节点列表，每个根节点含 {@code children} 嵌套）
     */
    List<MenuTreeVO> tree();
}

package com.njydsz.pmis.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.iam.dto.PermissionFormDTO;
import com.njydsz.pmis.iam.entity.PermissionDO;
import com.njydsz.pmis.iam.mapper.PermissionMapper;
import com.njydsz.pmis.iam.service.PermissionService;
import com.njydsz.pmis.iam.vo.MenuTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限服务实现
 *
 * <p>P2-6 改进：对高频读路径启用 Spring Cache：
 * <ul>
 *   <li>{@link #listAllEnabled()} — 管理端菜单树构建基础数据</li>
 *   <li>{@link #listPermCodesByUserId(Long)} — 鉴权拦截器/前端菜单加载高频调用</li>
 *   <li>{@link #listMenuTreeByUserId(Long)} — 用户登录后菜单树</li>
 *   <li>{@link #listAllMenuTree()} — 管理端菜单树</li>
 * </ul>
 * 写操作（create/update/delete）触发 {@code @CacheEvict(allEntries=true)} 清空所有缓存条目，
 * 避免脏数据。缓存 TTL 由 Redisson Spring Cache 配置统一管理（默认 30 分钟）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    /** 全部启用权限缓存名称 */
    public static final String CACHE_ALL_ENABLED = "perm:all_enabled";
    /** 用户权限编码缓存名称 */
    public static final String CACHE_PERM_CODES = "perm:codes";
    /** 用户菜单树缓存名称 */
    public static final String CACHE_MENU_TREE = "perm:menu_tree";
    /** 全部菜单树缓存名称 */
    public static final String CACHE_ALL_MENU_TREE = "perm:all_menu_tree";

    private final PermissionMapper permissionMapper;

    @Override
    @Cacheable(value = CACHE_ALL_ENABLED, unless = "#result == null || #result.isEmpty()")
    public List<PermissionDO> listAllEnabled() {
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>()
                .eq(PermissionDO::getStatus, "ENABLED")
                .orderByAsc(PermissionDO::getSortOrder).orderByAsc(PermissionDO::getId));
    }

    @Override
    @Cacheable(value = CACHE_PERM_CODES, key = "#userId", unless = "#result == null || #result.isEmpty()")
    public List<String> listPermCodesByUserId(Long userId) {
        return permissionMapper.selectPermCodesByUserId(userId);
    }

    @Override
    @Cacheable(value = CACHE_MENU_TREE, key = "#userId", unless = "#result == null || #result.isEmpty()")
    public List<MenuTreeVO> listMenuTreeByUserId(Long userId) {
        // 1. 拉取该用户所有权限
        List<PermissionDO> perms = permissionMapper.selectByUserId(userId);
        if (perms == null || perms.isEmpty()) {
            return List.of();
        }
        return buildMenuTree(perms);
    }

    @Override
    @Cacheable(value = CACHE_ALL_MENU_TREE, unless = "#result == null || #result.isEmpty()")
    public List<MenuTreeVO> listAllMenuTree() {
        return buildMenuTree(listAllEnabled());
    }

    /**
     * 构建菜单树 (按 parentId 递归,只保留 permType=MENU/API 的节点)
     */
    private List<MenuTreeVO> buildMenuTree(List<PermissionDO> perms) {
        // 1. 转 VO
        Map<Long, MenuTreeVO> map = new HashMap<>();
        for (PermissionDO p : perms) {
            MenuTreeVO vo = new MenuTreeVO();
            BeanUtils.copyProperties(p, vo);
            map.put(p.getId(), vo);
        }

        // 2. 拼树
        List<MenuTreeVO> roots = new ArrayList<>();
        for (PermissionDO p : perms) {
            MenuTreeVO vo = map.get(p.getId());
            Long pid = p.getParentId() == null ? 0L : p.getParentId();
            if (pid == null || pid == 0L) {
                roots.add(vo);
            } else {
                MenuTreeVO parent = map.get(pid);
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // 父节点未授权,作为根处理
                    roots.add(vo);
                }
            }
        }

        // 3. 排序 + 过滤
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<MenuTreeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) return;
        nodes.sort(Comparator.comparing(MenuTreeVO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MenuTreeVO::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (MenuTreeVO n : nodes) {
            sortTree(n.getChildren());
        }
    }

    @Override
    public List<PermissionDO> listByRoleId(Long roleId) {
        return permissionMapper.selectByRoleId(roleId);
    }

    @Override
    public PermissionDO getById(Long id) {
        PermissionDO p = permissionMapper.selectById(id);
        if (p == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.user.msg_bf562d6f");
        }
        return p;
    }

    @Override
    @CacheEvict(value = {CACHE_ALL_ENABLED, CACHE_PERM_CODES, CACHE_MENU_TREE, CACHE_ALL_MENU_TREE}, allEntries = true)
    public Long create(PermissionFormDTO dto) {
        if (permissionMapper.selectByCode(dto.getPermCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.user.msg_7b343d30");
        }
        PermissionDO entity = new PermissionDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus("ENABLED");
        if (entity.getVisible() == null) entity.setVisible(1);
        if (entity.getParentId() == null) entity.setParentId(0L);
        permissionMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @CacheEvict(value = {CACHE_ALL_ENABLED, CACHE_PERM_CODES, CACHE_MENU_TREE, CACHE_ALL_MENU_TREE}, allEntries = true)
    public void update(PermissionFormDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_965c9a30");
        }
        PermissionDO exists = permissionMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.user.msg_bf562d6f");
        }
        PermissionDO entity = new PermissionDO();
        BeanUtils.copyProperties(dto, entity);
        permissionMapper.updateById(entity);
    }

    @Override
    @CacheEvict(value = {CACHE_ALL_ENABLED, CACHE_PERM_CODES, CACHE_MENU_TREE, CACHE_ALL_MENU_TREE}, allEntries = true)
    public void delete(Long id) {
        // 存在子权限则不允许删除
        Long childCount = permissionMapper.selectCount(new LambdaQueryWrapper<PermissionDO>()
                .eq(PermissionDO::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_72018d8a");
        }
        permissionMapper.deleteById(id);
    }
}
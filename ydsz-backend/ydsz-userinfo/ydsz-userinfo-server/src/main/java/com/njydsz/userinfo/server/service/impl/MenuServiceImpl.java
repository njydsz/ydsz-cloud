package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.MenuSaveDTO;
import com.njydsz.userinfo.domain.entity.Menu;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.infra.mapper.MenuMapper;
import com.njydsz.userinfo.server.service.MenuService;
import com.njydsz.common.domain.tree.TreeBuilder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;

/**
 * 菜单 Service 实现。
 *
 * <p>核心能力：菜单 CRUD、树形结构构建。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    /** 菜单 Mapper */
    private final MenuMapper mapper;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException 当菜单不存在或已删除时抛出
     */
    @Override
    public MenuVO getById(String id) {
        Menu entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.MENU_NOT_FOUND);
        }
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部未删除菜单列表（按 sortOrder 降序）
     */
    @Override
    public List<MenuVO> list() {
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Menu::getDeleted, 0);
        wrapper.orderByDesc(Menu::getSortOrder);
        return mapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>status 默认 ENABLED，parentId 为空时默认 "0"（根节点）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(MenuSaveDTO dto) {
        Menu entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        if (entity.getParentId() == null || entity.getParentId().isBlank()) {
            entity.setParentId("0");
        }
        mapper.insert(entity);
        log.info("Menu created: code={}, id={}", entity.getMenuCode(), entity.getId());
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>使用 MapStruct 转换（更新操作暂保留 BeanUtils）
     *
     * @throws BusinessException 当菜单不存在或已删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(MenuSaveDTO dto) {
        Menu entity = mapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.MENU_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return mapper.updateById(entity) > 0;
    }

    /**
     * {@inheritDoc}
     * <p>删除前检查：有子菜单不可删除。
     *
     * @throws BusinessException 当菜单不存在、或有子菜单时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Menu entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.MENU_NOT_FOUND);
        }
        // 检查子菜单
        LambdaQueryWrapper<Menu> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(Menu::getParentId, id);
        childWrapper.eq(Menu::getDeleted, 0);
        if (mapper.selectCount(childWrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.MENU_HAS_CHILDREN);
        }
        return mapper.deleteById(id) > 0;
    }

    /**
     * {@inheritDoc}
     * <p>查询全部未删除菜单，通过 {@link TreeBuilder#buildSimple} 构建树形结构。
     *
     * @return 菜单树形结构列表，空数据返回空列表
     */
    @Override
    public List<MenuTreeVO> tree() {
        List<Menu> all = mapper.selectList(
                new LambdaQueryWrapper<Menu>()
                        .eq(Menu::getDeleted, 0));
        if (all.isEmpty()) {
            return List.of();
        }

        List<MenuTreeVO> voList = all.stream().map(menu -> {
            MenuTreeVO vo = UserInfoConverter.INSTANT.entityToMenuTreeVO(menu);
            return vo;
        }).collect(Collectors.toList());

        return TreeBuilder.buildSimple(voList,
                MenuTreeVO::getId,
                MenuTreeVO::getParentId,
                MenuTreeVO::setChildren,
                MenuTreeVO::getSortOrder);
    }

    /**
     * 将 DO 转换为 VO，使用 MapStruct 转换器
     *
     * @param entity 数据库实体
     * @return 视图对象
     */
    private MenuVO toVO(Menu entity) {
        return UserInfoConverter.INSTANT.entityToVO(entity);
    }
}

package com.njydsz.userinfo.server.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.entity.MenuDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.infra.mapper.MenuMapper;
import com.njydsz.userinfo.server.service.MenuService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 菜单 Service 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuMapper mapper;

    @Override
    public MenuDO getById(String id) {
        MenuDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.MENU_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public List<MenuDO> list() {
        LambdaQueryWrapper<MenuDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MenuDO::getDeleted, 0);
        wrapper.orderByAsc(MenuDO::getSortOrder);
        return mapper.selectList(wrapper);
    }

    @Override
    public String save(MenuDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(MenuDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<MenuTreeVO> tree() {
        List<MenuDO> all = list();
        if (all.isEmpty()) {
            return List.of();
        }

        List<MenuTreeVO> voList = all.stream().map(menu -> {
            MenuTreeVO vo = new MenuTreeVO();
            BeanUtils.copyProperties(menu, vo);
            return vo;
        }).collect(Collectors.toList());

        Map<String, List<MenuTreeVO>> parentIdMap = voList.stream()
                .collect(Collectors.groupingBy(vo ->
                        vo.getParentId() == null ? "0" : vo.getParentId()));

        List<MenuTreeVO> roots = parentIdMap.getOrDefault("0", new ArrayList<>());
        for (MenuTreeVO vo : voList) {
            List<MenuTreeVO> children = parentIdMap.get(vo.getId());
            if (children != null) {
                vo.setChildren(children);
            }
        }
        return roots;
    }
}

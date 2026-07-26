package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.MenuSaveDTO;
import com.njydsz.userinfo.domain.entity.MenuDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.infra.mapper.MenuMapper;
import com.njydsz.userinfo.server.service.MenuService;
import com.njydsz.userinfo.server.util.TreeBuilder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    private final MenuMapper mapper;

    @Override
    public MenuVO getById(String id) {
        MenuDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.MENU_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public List<MenuVO> list() {
        LambdaQueryWrapper<MenuDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MenuDO::getDeleted, 0);
        wrapper.orderByDesc(MenuDO::getSortOrder);
        return mapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(MenuSaveDTO dto) {
        MenuDO entity = new MenuDO();
        BeanUtils.copyProperties(dto, entity);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(MenuSaveDTO dto) {
        MenuDO entity = mapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.MENU_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        MenuDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.MENU_NOT_FOUND);
        }
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<MenuTreeVO> tree() {
        List<MenuDO> all = mapper.selectList(
                new LambdaQueryWrapper<MenuDO>()
                        .eq(MenuDO::getDeleted, 0));
        if (all.isEmpty()) {
            return List.of();
        }

        List<MenuTreeVO> voList = all.stream().map(menu -> {
            MenuTreeVO vo = new MenuTreeVO();
            BeanUtils.copyProperties(menu, vo);
            return vo;
        }).collect(Collectors.toList());

        return TreeBuilder.build(voList,
                MenuTreeVO::getId,
                MenuTreeVO::getParentId,
                MenuTreeVO::setChildren,
                MenuTreeVO::getSortOrder);
    }

    private MenuVO toVO(MenuDO entity) {
        MenuVO vo = new MenuVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

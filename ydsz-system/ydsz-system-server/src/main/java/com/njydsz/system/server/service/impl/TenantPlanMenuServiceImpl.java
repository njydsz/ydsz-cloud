package com.njydsz.system.server.service.impl;

import java.util.ArrayList;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.TenantPlanMenuDTO;
import com.njydsz.system.domain.entity.TenantPlanMenu;
import com.njydsz.system.domain.vo.TenantPlanMenuVO;
import com.njydsz.system.infra.mapper.TenantPlanMenuMapper;
import com.njydsz.system.server.service.TenantPlanMenuService;

/**
 * 租户套餐-菜单关联 Service 实现
 *
 * <p>对 {@link TenantPlanMenuService} 接口的完整实现。
 * 提供套餐与菜单关联关系的配置能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>查询</b>：{@link #listByPlanId} — 查询指定套餐关联的菜单列表</li>
 *   <li><b>批量更新</b>：{@link #updatePlanMenus} — 为套餐批量配置菜单权限（先删后插，事务保证）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see TenantPlanMenuService 套餐-菜单关联 Service 接口
 * @see com.njydsz.system.domain.entity.TenantPlanMenu 套餐-菜单关联实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPlanMenuServiceImpl implements TenantPlanMenuService {

    /** 套餐-菜单关联 Mapper */
    private final TenantPlanMenuMapper mapper;

    /**
     * 查询指定套餐关联的菜单列表
     *
     * @param planId 套餐 ID
     * @return 套餐-菜单关联列表
     */
    @Override
    public List<TenantPlanMenuVO> listByPlanId(String planId) {
        LambdaQueryWrapper<TenantPlanMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantPlanMenu::getPlanId, planId);
        return mapper.selectList(wrapper).stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 为套餐批量配置菜单权限
     *
     * <p>执行逻辑：先删除该套餐的所有旧关联，再批量插入新的关联记录。
     * 整个操作在事务内完成。
     *
     * @param dto 套餐-菜单关联 DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlanMenus(TenantPlanMenuDTO dto) {
        // 1. 删除旧关联
        LambdaQueryWrapper<TenantPlanMenu> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(TenantPlanMenu::getPlanId, dto.getPlanId());
        mapper.delete(deleteWrapper);

        // 2. 批量插入新关联
        if (dto.getMenuIds() == null || dto.getMenuIds().isEmpty()) {
            log.info("套餐[{}]菜单配置已清空", dto.getPlanId());
            return;
        }
        List<TenantPlanMenu> entities = new ArrayList<>(dto.getMenuIds().size());
        for (String menuId : dto.getMenuIds()) {
            TenantPlanMenu entity = new TenantPlanMenu();
            entity.setPlanId(dto.getPlanId());
            entity.setMenuId(menuId);
            entities.add(entity);
        }
        // 使用 MP 批量插入
        entities.forEach(mapper::insert);
        log.info("套餐[{}]菜单配置已更新, 菜单数量={}", dto.getPlanId(), dto.getMenuIds().size());
    }
}

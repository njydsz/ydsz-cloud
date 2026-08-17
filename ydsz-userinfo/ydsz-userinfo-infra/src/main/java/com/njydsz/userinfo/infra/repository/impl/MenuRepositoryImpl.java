package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.MenuRepository;
import com.njydsz.userinfo.infra.entity.MenuDO;
import com.njydsz.userinfo.infra.mapper.MenuMapper;

/**
 * 菜单/权限 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link MenuMapper} 实现菜单/权限的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepository {

  private final MenuMapper menuMapper;

  @Override
  public MenuDO findById(String id) {
    return menuMapper.selectById(id);
  }

  @Override
  public List<MenuDO> findByIds(Collection<String> ids) {
    return menuMapper.selectBatchIds(ids);
  }

  @Override
  public List<MenuDO> findByParentId(String parentId) {
    LambdaQueryWrapper<MenuDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(MenuDO::getParentId, parentId);
    return menuMapper.selectList(wrapper);
  }

  @Override
  public List<MenuDO> list(LambdaQueryWrapper<MenuDO> wrapper) {
    return menuMapper.selectList(wrapper);
  }

  @Override
  public int insert(MenuDO entity) {
    return menuMapper.insert(entity);
  }

  @Override
  public int updateById(MenuDO entity) {
    return menuMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return menuMapper.deleteById(id);
  }

  @Override
  public int delete(LambdaQueryWrapper<MenuDO> wrapper) {
    return menuMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<MenuDO> wrapper) {
    return menuMapper.selectCount(wrapper);
  }
}

package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.Menu;
import com.njydsz.userinfo.infra.mapper.MenuMapper;
import com.njydsz.userinfo.infra.repository.MenuRepository;

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
  public Menu findById(String id) {
    return menuMapper.selectById(id);
  }

  @Override
  public List<Menu> findByIds(Collection<String> ids) {
    return menuMapper.selectBatchIds(ids);
  }

  @Override
  public List<Menu> findByParentId(String parentId) {
    LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Menu::getParentId, parentId);
    return menuMapper.selectList(wrapper);
  }

  @Override
  public List<Menu> list(LambdaQueryWrapper<Menu> wrapper) {
    return menuMapper.selectList(wrapper);
  }

  @Override
  public int insert(Menu entity) {
    return menuMapper.insert(entity);
  }

  @Override
  public int updateById(Menu entity) {
    return menuMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return menuMapper.deleteById(id);
  }

  @Override
  public int delete(LambdaQueryWrapper<Menu> wrapper) {
    return menuMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<Menu> wrapper) {
    return menuMapper.selectCount(wrapper);
  }
}

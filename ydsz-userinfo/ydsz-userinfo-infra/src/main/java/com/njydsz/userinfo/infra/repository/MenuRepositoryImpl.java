package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.query.MenuPageQuery;
import com.njydsz.userinfo.domain.repository.MenuRepository;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.infra.converter.UserInfoAuthConverter;
import com.njydsz.userinfo.infra.entity.Menu;
import com.njydsz.userinfo.infra.mapper.MenuMapper;

/**
 * 菜单/权限 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link MenuMapper} 实现菜单/权限的数据访问。
 * 所有返回值通过 {@link UserInfoAuthConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepository {

  private final MenuMapper menuMapper;
  private final UserInfoAuthConverter converter;

  @Override
  public Optional<MenuVO> findById(String id) {
    Menu entity = menuMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<MenuVO> findByIds(Collection<String> ids) {
    List<Menu> entities = menuMapper.selectBatchIds(ids);
    return converter.menuListToVO(entities);
  }

  @Override
  public List<MenuVO> findByParentId(String parentId) {
    LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Menu::getParentId, parentId);
    List<Menu> entities = menuMapper.selectList(wrapper);
    return converter.menuListToVO(entities);
  }

  @Override
  public PageResponse<List<MenuVO>> page(MenuPageQuery query) {
    Page<Menu> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<Menu> wrapper = buildWrapper(query);
    Page<Menu> result = menuMapper.selectPage(page, wrapper);
    List<MenuVO> vos = converter.menuListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<MenuVO> list(MenuPageQuery query) {
    LambdaQueryWrapper<Menu> wrapper = buildWrapper(query);
    List<Menu> entities = menuMapper.selectList(wrapper);
    return converter.menuListToVO(entities);
  }

  @Override
  public MenuVO save(MenuDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      Menu entity = converter.dtoToEntity(dto);
      menuMapper.insert(entity);
      return converter.entityToVO(entity);
    } else {
      Menu entity = converter.dtoToEntityWithId(dto);
      menuMapper.updateById(entity);
      return converter.entityToVO(entity);
    }
  }

  @Override
  public boolean deleteById(String id) {
    return menuMapper.deleteById(id) > 0;
  }

  @Override
  public long countByQuery(MenuPageQuery query) {
    LambdaQueryWrapper<Menu> wrapper = buildWrapper(query);
    return menuMapper.selectCount(wrapper);
  }

  private LambdaQueryWrapper<Menu> buildWrapper(MenuPageQuery query) {
    LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
    if (query.getMenuCode() != null && !query.getMenuCode().isBlank()) {
      wrapper.like(Menu::getMenuCode, query.getMenuCode());
    }
    if (query.getMenuName() != null && !query.getMenuName().isBlank()) {
      wrapper.like(Menu::getMenuName, query.getMenuName());
    }
    if (query.getMenuType() != null && !query.getMenuType().isBlank()) {
      wrapper.eq(Menu::getMenuType, query.getMenuType());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Menu::getStatus, query.getStatus());
    }
    return wrapper;
  }
}

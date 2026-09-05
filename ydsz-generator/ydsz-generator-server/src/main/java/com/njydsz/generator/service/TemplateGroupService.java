package com.njydsz.generator.service;

import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.repository.GenTemplateGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模板分组领域服务。
 *
 * <p>管理模板分组 CRUD 与激活切换。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateGroupService {

  private final GenTemplateGroupRepository groupRepository;

  /**
   * 查询全部分组（按排序序号）。
   *
   * @return 分组列表
   */
  public List<GenTemplateGroup> listAll() {
    return groupRepository.findAllByOrderBySortOrderAsc();
  }

  /**
   * 查询当前激活分组。
   *
   * @return Optional 分组
   */
  public GenTemplateGroup getActive() {
    return groupRepository.findByIsActiveTrue().orElse(null);
  }

  /**
   * 根据 ID 查询分组。
   *
   * @param id 分组 ID
   * @return Optional 分组
   */
  public GenTemplateGroup getById(Long id) {
    return groupRepository.findById(id).orElse(null);
  }

  /**
   * 创建分组。
   *
   * @param group 分组实体
   * @return 持久化后的实体
   */
  @Transactional(rollbackFor = Exception.class)
  public GenTemplateGroup create(GenTemplateGroup group) {
    group.setId(null);
    group.setSystem(false);
    if (group.getSortOrder() == null) {
      group.setSortOrder(0);
    }
    return groupRepository.save(group);
  }

  /**
   * 更新分组。
   *
   * @param group 分组实体
   * @return 持久化后的实体
   */
  @Transactional(rollbackFor = Exception.class)
  public GenTemplateGroup update(GenTemplateGroup group) {
    return groupRepository.save(group);
  }

  /**
   * 激活指定分组（同时取消其它分组激活状态）。
   *
   * @param id 分组 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void activate(Long id) {
    List<GenTemplateGroup> all = groupRepository.findAllByOrderBySortOrderAsc();
    for (GenTemplateGroup g : all) {
      g.setActive(g.getId().equals(id));
      groupRepository.save(g);
    }
    log.info("激活模板分组 id={}", id);
  }

  /**
   * 删除分组（系统分组不可删除）。
   *
   * @param id 分组 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteById(Long id) {
    GenTemplateGroup group = groupRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("分组不存在: " + id));
    if (Boolean.TRUE.equals(group.getSystem())) {
      throw new IllegalStateException("系统内置分组不可删除: " + group.getName());
    }
    groupRepository.deleteById(id);
    log.info("删除模板分组 id={} name={}", id, group.getName());
  }

  /**
   * 统计分组数量。
   *
   * @return 总数
   */
  public long count() {
    return groupRepository.count();
  }
}

package com.njydsz.generator.service;

import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.repository.GenTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模板领域服务。
 *
 * <p>管理模板 CRUD，提供按分组查询、内容更新等能力。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

  private final GenTemplateRepository templateRepository;

  /**
   * 按分组 ID 查询全部模板。
   *
   * @param groupId 分组 ID
   * @return 模板列表
   */
  public List<GenTemplate> listByGroup(Long groupId) {
    return templateRepository.findByGroupIdOrderByFileNameAsc(groupId);
  }

  /**
   * 查询分组全部模板文件名（不含内容，用于文件浏览器）。
   *
   * @param groupId 分组 ID
   * @return 文件名列表
   */
  public List<String> listFileNames(Long groupId) {
    return templateRepository.findByGroupIdOrderByFileNameAsc(groupId).stream()
        .map(GenTemplate::getFileName)
        .collect(java.util.stream.Collectors.toList());
  }

  /**
   * 根据 ID 查询模板。
   *
   * @param id 模板 ID
   * @return 模板实体
   */
  public GenTemplate getById(Long id) {
    return templateRepository.findById(id).orElse(null);
  }

  /**
   * 根据文件名查询模板。
   *
   * @param groupId  分组 ID
   * @param fileName 文件名
   * @return 模板实体
   */
  public GenTemplate getByFileName(Long groupId, String fileName) {
    return templateRepository.findByGroupIdAndFileName(groupId, fileName).orElse(null);
  }

  /**
   * 创建模板。
   *
   * @param template 模板实体
   * @return 持久化后的实体
   */
  @Transactional(rollbackFor = Exception.class)
  public GenTemplate create(GenTemplate template) {
    template.setId(null);
    template.setVersion(1);
    if (template.getActive() == null) {
      template.setActive(true);
    }
    return templateRepository.save(template);
  }

  /**
   * 更新模板内容（版本号自动递增）。
   *
   * @param template 模板实体（ID + content）
   * @return 持久化后的实体
   */
  @Transactional(rollbackFor = Exception.class)
  public GenTemplate updateContent(GenTemplate template) {
    GenTemplate existing = templateRepository.findById(template.getId())
        .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + template.getId()));
    existing.setContent(template.getContent());
    existing.setDescription(template.getDescription());
    if (template.getVersion() != null) {
      existing.setVersion(template.getVersion() + 1);
    } else {
      existing.setVersion((existing.getVersion() == null ? 1 : existing.getVersion()) + 1);
    }
    log.info("更新模板 id={} file={} version={}", existing.getId(), existing.getFileName(),
        existing.getVersion());
    return templateRepository.save(existing);
  }

  /**
   * 删除模板。
   *
   * @param id 模板 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteById(Long id) {
    templateRepository.deleteById(id);
    log.info("删除模板 id={}", id);
  }

  /**
   * 删除分组全部模板。
   *
   * @param groupId 分组 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteByGroup(Long groupId) {
    templateRepository.deleteByGroupId(groupId);
    log.info("删除模板全部分组 groupId={}", groupId);
  }

  /**
   * 按标题搜索模板。
   *
   * @param groupId 分组 ID
   * @param keyword 搜索关键词
   * @return 匹配模板列表
   */
  public List<GenTemplate> search(Long groupId, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return listByGroup(groupId);
    }
    return templateRepository.findByGroupIdOrderByFileNameAsc(groupId).stream()
        .filter(t -> t.getFileName().contains(keyword)
            || (t.getDescription() != null && t.getDescription().contains(keyword)))
        .collect(java.util.stream.Collectors.toList());
  }

  /**
   * 统计分组模板数量。
   *
   * @param groupId 分组 ID
   * @return 数量
   */
  public long countByGroup(Long groupId) {
    return templateRepository.countByGroupId(groupId);
  }
}

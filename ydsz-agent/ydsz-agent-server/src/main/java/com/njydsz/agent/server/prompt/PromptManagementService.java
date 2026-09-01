package com.njydsz.agent.server.prompt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.njydsz.agent.domain.dto.PromptTemplateDTO;
import com.njydsz.agent.domain.dto.PromptVersionDTO;
import com.njydsz.agent.domain.repository.PromptTemplateRepository;
import com.njydsz.agent.domain.repository.PromptVersionRepository;
import com.njydsz.agent.domain.vo.PromptTemplateVO;
import com.njydsz.agent.domain.vo.PromptVersionVO;

/**
 * Prompt 管理服务
 *
 * <p>提供 Prompt 模板的 CRUD、版本管理和变量替换能力。 底层使用数据库持久化，内存缓存加速热点读取。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>Prompt 模板 CRUD（数据库存储，重启不丢失）
 *   <li>版本管理（每次更新创建新版本，可回滚）
 *   <li>分类检索
 *   <li>变量替换（#{var} 占位符）
 * </ul>
 *
 * <h3>缓存策略</h3>
 *
 * <p>首次读取后缓存在内存中，写操作同步更新缓存与数据库， 确保单实例内读取一致性。多实例部署时依赖数据库保证最终一致性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class PromptManagementService {

  /** 模板编码 → PromptTemplate，用于 O(1) 热点读取 */
  private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

  /** Prompt 模板 Repository */
  private final PromptTemplateRepository templateRepository;

  /** Prompt 版本 Repository */
  private final PromptVersionRepository versionRepository;

  /** 是否已执行缓存预热 */
  private final AtomicBoolean cacheWarmed = new AtomicBoolean(false);

  public PromptManagementService(
      PromptTemplateRepository templateRepository, PromptVersionRepository versionRepository) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
  }

  /**
   * 创建 Prompt 模板
   *
   * <p>同时创建模板记录（version=1）和初始版本快照。
   *
   * @param code 模板唯一编码
   * @param name 模板名称
   * @param content 模板内容
   * @param description 模板描述
   * @param category 分类
   * @return 创建的模板快照
   * @throws IllegalArgumentException 当模板编码已存在时抛出
   */
  @Transactional
  public PromptTemplate create(
      String code, String name, String content, String description, String category) {
    PromptTemplateVO existing = selectByCode(code);
    if (existing != null) {
      throw new IllegalArgumentException("Prompt 模板已存在: " + code);
    }
    LocalDateTime now = LocalDateTime.now();
    // 插入模板主表（初始版本号为 1）
    PromptTemplateDTO templateDTO = new PromptTemplateDTO();
    templateDTO.setTemplateCode(code);
    templateDTO.setTemplateName(name);
    templateDTO.setContent(content);
    templateDTO.setDescription(description);
    templateDTO.setCategory(category);
    templateDTO.setCurrentVersion(1);
    templateRepository.insert(templateDTO);
    // 插入版本快照
    insertVersion(code, 1, content, "初始版本");
    // 更新缓存
    PromptTemplate template =
        new PromptTemplate(code, name, content, description, category, 1, now, now);
    templateCache.put(code, template);
    log.info("[Prompt] 创建模板: code={}, name={}", code, name);
    return template;
  }

  /**
   * 更新 Prompt 模板（创建新版本）
   *
   * <p>原子操作：递增版本号 + 更新主表 + 追加版本快照。
   *
   * @param code 模板编码
   * @param content 新版本内容
   * @return 更新后的模板快照
   * @throws IllegalArgumentException 当模板不存在时抛出
   */
  @Transactional
  public PromptTemplate update(String code, String content) {
    PromptTemplateVO existing = selectByCode(code);
    if (existing == null) {
      throw new IllegalArgumentException("Prompt 模板不存在: " + code);
    }
    int newVersion = existing.getCurrentVersion() + 1;
    LocalDateTime now = LocalDateTime.now();
    // 更新主表版本号与内容
    PromptTemplateDTO templateDTO = new PromptTemplateDTO();
    templateDTO.setId(existing.getId());
    templateDTO.setTemplateCode(existing.getTemplateCode());
    templateDTO.setTemplateName(existing.getTemplateName());
    templateDTO.setContent(content);
    templateDTO.setDescription(existing.getDescription());
    templateDTO.setCategory(existing.getCategory());
    templateDTO.setCurrentVersion(newVersion);
    templateRepository.updateById(templateDTO);
    // 插入新版本快照
    insertVersion(code, newVersion, content, null);
    // 更新缓存
    PromptTemplate updated =
        new PromptTemplate(
            existing.getTemplateCode(),
            existing.getTemplateName(),
            content,
            existing.getDescription(),
            existing.getCategory(),
            newVersion,
            existing.getCreatedAt(),
            now);
    templateCache.put(code, updated);
    log.info("[Prompt] 更新模板: code={}, version={}", code, newVersion);
    return updated;
  }

  /**
   * 获取 Prompt 模板（先查缓存，未命中则从数据库加载）
   *
   * @param code 模板编码
   * @return 模板快照，不存在时返回 null
   */
  public PromptTemplate get(String code) {
    PromptTemplate cached = templateCache.get(code);
    if (cached != null) {
      return cached;
    }
    return loadAndCache(code);
  }

  /**
   * 获取指定版本的内容
   *
   * @param code 模板编码
   * @param version 版本号
   * @return 版本快照，不存在时返回 null
   */
  public PromptVersion getVersion(String code, int version) {
    Optional<PromptVersionVO> versionVO =
        versionRepository.findByTemplateCodeAndVersion(code, version);
    if (versionVO.isEmpty()) {
      return null;
    }
    PromptVersionVO vo = versionVO.get();
    return new PromptVersion(code, version, vo.getContent(), vo.getCreatedAt());
  }

  /**
   * 列出所有模板
   *
   * @return 模板快照列表（全库扫描，结果集通常较小）
   */
  public List<PromptTemplate> list() {
    warmCacheIfNeeded();
    return List.copyOf(templateCache.values());
  }

  /**
   * 列出模板的所有版本
   *
   * @param code 模板编码
   * @return 版本快照列表（按版本号升序）
   */
  public List<PromptVersion> listVersions(String code) {
    List<PromptVersionVO> versionVOs = versionRepository.findByTemplateCode(code);
    return versionVOs.stream()
        .map(v -> new PromptVersion(code, v.getVersion(), v.getContent(), v.getCreatedAt()))
        .collect(Collectors.toList());
  }

  /**
   * 按分类列出模板
   *
   * @param category 分类名称
   * @return 属于该分类的模板快照列表
   */
  public List<PromptTemplate> listByCategory(String category) {
    warmCacheIfNeeded();
    return templateCache.values().stream()
        .filter(t -> category.equals(t.category()))
        .collect(Collectors.toList());
  }

  /**
   * 删除模板（逻辑删除主表，保留版本历史以供审计）
   *
   * @param pCode 模板编码
   */
  @Transactional
  public void delete(String pCode) {
    PromptTemplateVO existing = selectByCode(pCode);
    if (existing != null) {
      templateRepository.deleteById(existing.getId());
      log.info("[Prompt] 删除模板: code={}", pCode);
    }
    templateCache.remove(pCode);
  }

  /**
   * 回滚到指定版本（基于目标版本内容创建新版本）
   *
   * @param code 模板编码
   * @param targetVersion 目标版本号
   * @return 回滚后的新版本快照
   * @throws IllegalArgumentException 当版本不存在时抛出
   */
  @Transactional
  public PromptTemplate rollback(String code, int targetVersion) {
    PromptVersion pv = getVersion(code, targetVersion);
    if (pv == null) {
      throw new IllegalArgumentException("版本不存在: " + targetVersion);
    }
    PromptTemplateVO existing = selectByCode(code);
    int newVersion = existing.getCurrentVersion() + 1;
    LocalDateTime now = LocalDateTime.now();
    PromptTemplateDTO templateDTO = new PromptTemplateDTO();
    templateDTO.setId(existing.getId());
    templateDTO.setTemplateCode(existing.getTemplateCode());
    templateDTO.setTemplateName(existing.getTemplateName());
    templateDTO.setContent(pv.content());
    templateDTO.setDescription(existing.getDescription());
    templateDTO.setCategory(existing.getCategory());
    templateDTO.setCurrentVersion(newVersion);
    templateRepository.updateById(templateDTO);
    insertVersion(code, newVersion, pv.content(), "回滚自版本 " + targetVersion);
    PromptTemplate rolledBack =
        new PromptTemplate(
            existing.getTemplateCode(),
            existing.getTemplateName(),
            pv.content(),
            existing.getDescription(),
            existing.getCategory(),
            newVersion,
            existing.getCreatedAt(),
            now);
    templateCache.put(code, rolledBack);
    log.info(
        "[Prompt] 回滚模板: code={}, targetVersion={}, newVersion={}", code, targetVersion, newVersion);
    return rolledBack;
  }

  /**
   * 渲染 Prompt（变量替换）
   *
   * @param code 模板编码
   * @param variables 变量映射
   * @return 渲染后的字符串
   * @throws IllegalArgumentException 当模板不存在时抛出
   */
  public String render(String code, Map<String, Object> variables) {
    PromptTemplate template = get(code);
    if (template == null) {
      throw new IllegalArgumentException("Prompt 模板不存在: " + code);
    }
    String content = template.content();
    if (variables != null) {
      for (Map.Entry<String, Object> entry : variables.entrySet()) {
        content = content.replace("#{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
      }
    }
    return content;
  }

  /** 缓存预热：首次读取时从数据库全量加载 */
  private void warmCacheIfNeeded() {
    if (!cacheWarmed.compareAndSet(false, true)) {
      return;
    }
    List<PromptTemplateVO> allTemplates = templateRepository.findAllActive();
    for (PromptTemplateVO t : allTemplates) {
      templateCache.put(
          t.getTemplateCode(),
          new PromptTemplate(
              t.getTemplateCode(),
              t.getTemplateName(),
              t.getContent(),
              t.getDescription(),
              t.getCategory(),
              t.getCurrentVersion(),
              t.getCreatedAt(),
              t.getUpdatedAt()));
    }
    log.info("[Prompt] 缓存预热完成, count={}", allTemplates.size());
  }

  /** 从数据库加载并缓存指定模板 */
  private PromptTemplate loadAndCache(String code) {
    Optional<PromptTemplateVO> templateVO = templateRepository.findByCode(code);
    if (templateVO.isEmpty()) {
      return null;
    }
    PromptTemplateVO vo = templateVO.get();
    PromptTemplate template =
        new PromptTemplate(
            vo.getTemplateCode(), vo.getTemplateName(),
            vo.getContent(), vo.getDescription(),
            vo.getCategory(), vo.getCurrentVersion(),
            vo.getCreatedAt(), vo.getUpdatedAt());
    templateCache.put(code, template);
    return template;
  }

  /** 根据编码查询模板 */
  private PromptTemplateVO selectByCode(String code) {
    return templateRepository.findByCode(code).orElse(null);
  }

  /** 插入版本快照记录 */
  private void insertVersion(String code, int version, String content, String changeNote) {
    PromptVersionDTO versionDTO = new PromptVersionDTO();
    versionDTO.setTemplateCode(code);
    versionDTO.setVersion(version);
    versionDTO.setContent(content);
    versionDTO.setChangeNote(changeNote);
    versionRepository.insert(versionDTO);
  }

  /**
   * Prompt 模板（当前版本快照）
   *
   * <p>对外返回的不可变视图，与数据库实体解耦。
   */
  public record PromptTemplate(
      /** 模板唯一编码 */
      String code,
      /** 模板名称 */
      String name,
      /** 模板内容，支持 #{var} 占位符 */
      String content,
      /** 模板描述 */
      String description,
      /** 分类 */
      String category,
      /** 当前版本号 */
      int version,
      /** 创建时间 */
      LocalDateTime createdAt,
      /** 最近更新时间 */
      LocalDateTime updatedAt) {

    public PromptTemplate {
      Objects.requireNonNull(code, "code 不能为 null");
    }
  }

  /** Prompt 模板的历史版本 */
  public record PromptVersion(
      /** 所属模板编码 */
      String code,
      /** 版本号 */
      int version,
      /** 该版本的模板内容快照 */
      String content,
      /** 版本创建时间 */
      LocalDateTime createdAt) {

    public PromptVersion {
      Objects.requireNonNull(code, "code 不能为 null");
    }
  }
}

package com.njydsz.agent.server.prompt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.agent.domain.dto.PromptTemplateDTO;
import com.njydsz.agent.domain.dto.PromptVersionDTO;
import com.njydsz.agent.domain.repository.PromptTemplateRepository;
import com.njydsz.agent.domain.repository.PromptVersionRepository;
import com.njydsz.agent.domain.vo.PromptTemplateVO;
import com.njydsz.agent.domain.vo.PromptVersionVO;

/**
 * Prompt 管理服务（含版本管理 + 发布/灰度/回滚能力）
 *
 * <p>提供 Prompt 模板的完整生命周期管理：
 *
 * <ul>
 *   <li>Prompt 模板 CRUD（数据库存储，重启不丢失）
 *   <li>版本管理（每次更新创建新版本快照；支持蓝绿发布与回滚）
 *   <li>灰度发布（A/B 测试流量切分，按比例将请求路由到新旧版本）
 *   <li>分类检索与变量替换（#{var} 占位符）
 * </ul>
 *
 * <h3>缓存策略</h3>
 *
 * <p>首次读取后缓存在内存中，写操作同步更新缓存与数据库， 确保单实例内读取一致性。多实例部署时依赖数据库保证最终一致性。
 *
 * <h3>灰度发布策略</h3>
 *
 * <p>通过灰度配置（{@link com.njydsz.agent.domain.dto.PromptTemplateDTO#getAbTrafficPercent()}  +
 * {@code abTargetVersion}）控制新旧版本流量切分比例。路由算法采用 contextId 取模（保证同一用户会话一致性）：
 *
 * <pre>
 * slot = hash(contextId) % 100
 * slot &lt; abTrafficPercent → 新版本
 * slot &ge; abTrafficPercent → 稳定版本
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
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

  /** 灰度流量分母（百分比精度） */
  private static final int AB_TEST_DENOMINATOR = 100;

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
    insertVersion(code, newVersion, content, null);
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
   * 获取 Prompt 模板内容（带灰度路由）。
   *
   * <p>当模板启用 A/B 测试时，使用 {@code contextId} 计算路由：
   * 流量按比例分配到 stable 版本（currentVersion - 1）和 canary 版本（currentVersion）。 当参数 {@code contextId} 为 null 时直接返回稳定版本。
   *
   * @param code 模板编码
   * @param contextId 请求上下文 ID（用户 ID / 会话 ID 等），为 null 时使用稳定版本
   * @return 路由到的 Prompt 内容
   * @throws IllegalArgumentException 当模板不存在时抛出
   */
  public String getContentWithAbTest(String code, String contextId) {
    PromptTemplateVO config = selectByCode(code);
    if (config == null) {
      throw new IllegalArgumentException("Prompt 模板不存在: " + code);
    }
    if (!Boolean.TRUE.equals(config.getIsAbTestEnabled())
        || config.getAbTrafficPercent() == null
        || config.getAbTargetVersion() == null
        || contextId == null
        || contextId.isBlank()) {
      // 灰度未启用或无 contextId → 返回稳定版本
      return config.getContent();
    }
    int trafficPercent = Math.min(config.getAbTrafficPercent(), AB_TEST_DENOMINATOR);
    int slot = Math.abs(contextId.hashCode()) % AB_TEST_DENOMINATOR;
    if (slot < trafficPercent) {
      // 路由到灰度（目标）版本
      PromptVersion pv = getVersion(code, config.getAbTargetVersion());
      if (pv != null) {
        log.trace("[Prompt] A/B 灰度命中: code={}, contextId={}, targetVersion={}",
            code, contextId, config.getAbTargetVersion());
        return pv.content();
      }
    }
    // 稳定版本（主表）
    return config.getContent();
  }

  /**
   * 发布（激活）指定版本 — 蓝绿发布。
   *
   * <p>将目标版本的内容激活到主表（当前生效版本），实现蓝绿切换。
   *
   * @param code 模板编码
   * @param targetVersion 要激活的版本号
   * @param changeNote 发布说明
   * @return 激活后的模板
   */
  @Transactional
  public PromptTemplate releaseVersion(String code, int targetVersion, String changeNote) {
    PromptVersion pv = getVersion(code, targetVersion);
    if (pv == null) {
      throw new IllegalArgumentException("版本不存在: " + targetVersion);
    }
    PromptTemplateVO existing = selectByCode(code);
    LocalDateTime now = LocalDateTime.now();
    PromptTemplateDTO templateDTO = new PromptTemplateDTO();
    templateDTO.setId(existing.getId());
    templateDTO.setTemplateCode(existing.getTemplateCode());
    templateDTO.setTemplateName(existing.getTemplateName());
    templateDTO.setContent(pv.content());
    templateDTO.setDescription(existing.getDescription());
    templateDTO.setCategory(existing.getCategory());
    templateDTO.setCurrentVersion(targetVersion);
    templateDTO.setIsAbTestEnabled(false);
    templateDTO.setAbTrafficPercent(null);
    templateDTO.setAbTargetVersion(null);
    templateRepository.updateById(templateDTO);
    insertVersion(code, targetVersion, pv.content(),
        "发布版本" + targetVersion + (changeNote != null ? ": " + changeNote : ""));
    PromptTemplate released =
        new PromptTemplate(
            existing.getTemplateCode(),
            existing.getTemplateName(),
            pv.content(),
            existing.getDescription(),
            existing.getCategory(),
            targetVersion,
            existing.getCreatedAt(),
            now);
    templateCache.put(code, released);
    log.info("[Prompt] 发布版本: code={}, version={}", code, targetVersion);
    return released;
  }

  /**
   * 启用灰度测试（A/B 测试）。
   *
   * <p>将主表当前版本作为稳定版本，新版本作为灰度（canary）版本，按 {@code trafficPercent}% 切分流量。
   *
   * @param code 模板编码
   * @param canaryVersion 灰度（canary）版本号
   * @param trafficPercent 灰度流量百分比（1-100）
   */
  @Transactional
  public void enableAbTest(String code, int canaryVersion, int trafficPercent) {
    PromptTemplateVO existing = selectByCode(code);
    if (existing == null) {
      throw new IllegalArgumentException("Prompt 模板不存在: " + code);
    }
    PromptVersion pv = getVersion(code, canaryVersion);
    if (pv == null) {
      throw new IllegalArgumentException("灰度版本不存在: " + canaryVersion);
    }
    PromptTemplateDTO templateDTO = new PromptTemplateDTO();
    templateDTO.setId(existing.getId());
    templateDTO.setIsAbTestEnabled(true);
    templateDTO.setAbTargetVersion(canaryVersion);
    templateDTO.setAbTrafficPercent(Math.min(trafficPercent, AB_TEST_DENOMINATOR));
    templateRepository.updateByIdAbTest(templateDTO);
    templateCache.remove(code);
    log.info("[Prompt] 启用 A/B 灰度: code={}, canaryVersion={}, trafficPercent={}%",
        code, canaryVersion, trafficPercent);
  }

  /**
   * 停止灰度测试。
   *
   * <p>关闭 A/B 测试，所有流量回退到主表稳定版本。
   *
   * @param code 模板编码
   */
  @Transactional
  public void disableAbTest(String code) {
    PromptTemplateVO existing = selectByCode(code);
    if (existing == null) {
      return;
    }
    PromptTemplateDTO templateDTO = new PromptTemplateDTO();
    templateDTO.setId(existing.getId());
    templateDTO.setIsAbTestEnabled(false);
    templateDTO.setAbTrafficPercent(null);
    templateDTO.setAbTargetVersion(null);
    templateRepository.updateByIdAbTest(templateDTO);
    templateCache.remove(code);
    log.info("[Prompt] 停用 A/B 灰度: code={}", code);
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
   * @param code 模板编码
   */
  @Transactional
  public void delete(String code) {
    PromptTemplateVO existing = selectByCode(code);
    if (existing != null) {
      templateRepository.deleteById(existing.getId());
      log.info("[Prompt] 删除模板: code={}", code);
    }
    templateCache.remove(code);
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
    PromptTemplateVO t = templateVO.get();
    PromptTemplate template =
        new PromptTemplate(
            t.getTemplateCode(),
            t.getTemplateName(),
            t.getContent(),
            t.getDescription(),
            t.getCategory(),
            t.getCurrentVersion(),
            t.getCreatedAt(),
            t.getUpdatedAt());
    templateCache.put(code, template);
    return template;
  }

  /**
   * 根据编码查询 Prompt 模板 VO。
   *
   * @param code 模板编码
   * @return PromptTemplateVO 或 null（未找到）
   */
  private PromptTemplateVO selectByCode(String code) {
    Optional<PromptTemplateVO> templateVO = templateRepository.findByCode(code);
    return templateVO.orElse(null);
  }

  /**
   * 插入版本快照记录。
   *
   * @param code 模板编码
   * @param version 版本号
   * @param content 内容快照
   * @param changeNote 版本备注
   */
  private void insertVersion(String code, int version, String content, String changeNote) {
    PromptVersionDTO versionDTO = new PromptVersionDTO();
    versionDTO.setTemplateCode(code);
    versionDTO.setVersion(version);
    versionDTO.setContent(content);
    versionDTO.setChangeNote(changeNote);
    versionRepository.insert(versionDTO);
  }

  // ======================== 内部模型 ========================

  /**
   * Prompt 模板值对象（缓存载体）。
   *
   * <p>注意：record 字段命名需与 {@link PromptTemplateDTO} 保持语义对齐以便映射。
   *
   * @param code          模板编码
   * @param name          模板名称
   * @param content       模板内容
   * @param description   模板描述
   * @param category      模板分类
   * @param currentVersion 当前版本号
   * @param createdAt     创建时间
   * @param updatedAt     更新时间
   */
  public record PromptTemplate(
      String code,
      String name,
      String content,
      String description,
      String category,
      int currentVersion,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {

    /** 内容 getter（兼容旧调用路径）。
     *
     * @return 模板内容
     */
    public String content() {
      return content;
    }

    /** 分类 getter（兼容旧调用路径）。
     *
     * @return 模板分类
     */
    public String category() {
      return category;
    }

    /** 当前版本 getter。
     *
     * @return 当前版本号
     */
    public int version() {
      return currentVersion;
    }
  }

  /**
   * Prompt 版本值对象。
   *
   * @param templateCode 模板编码
   * @param version      版本号
   * @param content      版本内容
   * @param createdAt    创建时间
   */
  public record PromptVersion(
      String templateCode,
      int version,
      String content,
      LocalDateTime createdAt) {

    /** 内容 getter（兼容旧调用路径）。
     *
     * @return 版本内容
     */
    public String content() {
      return content;
    }
  }
}

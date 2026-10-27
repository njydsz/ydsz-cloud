package com.njydsz.literule.server.cache;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.entity.RuleDefinition;
import com.njydsz.literule.domain.repository.RuleDefinitionConfigRepository;
import com.njydsz.literule.server.spi.RuleConfigProvider;

/**
 * 直接数据库访问规则配置提供者（无装饰依赖）。
 *
 * <p>通过 domain 端口 {@link RuleDefinitionConfigRepository} 读写数据库，不包装其他 {@link
 * RuleConfigProvider}，解决启动时多个 Provider 相互依赖导致的循环依赖（鸡生蛋蛋生鸡）问题。
 *
 * <p>作为 {@link CachingRuleConfigProvider} 的 delegate 被注入（@Primary），提供最终 DB 数据源。
 *
 * <p><b>分层说明</b>：本类原先直接持有 infra 层 {@code RuleDefinitionMapper}，属 YDIZ-DDD-005 违例（pom 中标注为"权宜之计"）。
 * 26.10.27 起改为依赖 domain 端口，Mapper 调用收口到 {@code infra.repository.impl.RuleDefinitionConfigRepositoryImpl}，
 * server 对 literule-infra 的编译期依赖已同步移除。
 *
 * @since 26.10.01
 * @author ydsz-team
 */
@Slf4j
@RequiredArgsConstructor
public class DirectDbRuleConfigProvider implements RuleConfigProvider {

  private final RuleDefinitionConfigRepository ruleDefinitionConfigRepository;

  @Override
  public List<RuleDefinitionDTO> loadEnabledRules() {
    List<RuleDefinition> entities = ruleDefinitionConfigRepository.listAll();
    return entities.stream()
        .filter(e -> "PUBLISHED".equals(e.getStatus()) && Boolean.TRUE.equals(e.getIsEnabled()))
        .map(this::entityToDto)
        .toList();
  }

  @Override
  public List<RuleDefinitionDTO> loadAllRules() {
    List<RuleDefinition> entities = ruleDefinitionConfigRepository.listAll();
    return entities.stream().map(this::entityToDto).toList();
  }

  @Override
  public RuleDefinitionDTO save(RuleDefinitionDTO definition, String operator) {
    RuleDefinition existing = ruleDefinitionConfigRepository.findByCode(definition.getCode());
    RuleDefinition entity = dtoToEntity(definition);
    if (existing == null) {
      if (entity.getVersion() == null) {
        entity.setVersion(1);
      }
      ruleDefinitionConfigRepository.insert(entity);
    } else {
      entity.setId(existing.getId());
      ruleDefinitionConfigRepository.updateById(entity);
    }
    log.info("[LiteRule-DirectDb] 规则已保存: code={}, operator={}", definition.getCode(), operator);
    return entityToDto(ruleDefinitionConfigRepository.findByCode(definition.getCode()));
  }

  @Override
  public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
    RuleDefinition existing = ruleDefinitionConfigRepository.findByCode(ruleCode);
    if (existing == null) {
      log.warn("[LiteRule-DirectDb] 规则不存在，无法切换启停状态: ruleCode={}", ruleCode);
      return;
    }
    existing.setIsEnabled(enabled);
    ruleDefinitionConfigRepository.updateById(existing);
    log.info("[LiteRule-DirectDb] 规则启停切换: ruleCode={}, enabled={}", ruleCode, enabled);
  }

  @Override
  public RuleDefinitionDTO findByCode(String ruleCode) {
    return entityToDto(ruleDefinitionConfigRepository.findByCode(ruleCode));
  }

  @Override
  public SourceType getType() {
    return SourceType.DB;
  }

  @Override
  public boolean supportsWatch() {
    return false;
  }

  @Override
  public boolean isAvailable() {
    return ruleDefinitionConfigRepository != null;
  }

  // ==================== 内部方法 ====================

  /** Entity → DTO 转换（手动映射） */
  private RuleDefinitionDTO entityToDto(RuleDefinition entity) {
    if (entity == null) {
      return null;
    }
    RuleDefinitionDTO dto = new RuleDefinitionDTO();
    dto.setCode(entity.getRuleCode());
    dto.setName(entity.getRuleName());
    dto.setCategory(entity.getCategory());
    dto.setCategoryPath(entity.getCategoryPath());
    dto.setOwner(entity.getOwner());
    dto.setDescription(entity.getDescription());
    dto.setConditionExpression(entity.getConditionExpression());
    dto.setSeverityExpression(entity.getSeverityExpression());
    dto.setTitleTemplate(entity.getTitleTemplate());
    dto.setDescriptionTemplate(entity.getDescriptionTemplate());
    dto.setPriority(entity.getPriority());
    dto.setEnabled(Boolean.TRUE.equals(entity.getIsEnabled()));
    dto.setScope(entity.getScope());
    dto.setMutexGroup(entity.getMutexGroup());
    dto.setDrilldownAvailable(Boolean.TRUE.equals(entity.getIsDrilldownAvailable()));
    dto.setVersion(entity.getVersion() != null ? entity.getVersion() : 1);
    dto.setTenantId(entity.getTenantId());
    dto.setStatus(entity.getStatus());
    dto.setEffectiveFrom(entity.getEffectiveFrom());
    dto.setEffectiveTo(entity.getEffectiveTo());
    dto.setReviewedBy(entity.getReviewedBy());
    dto.setReviewedAt(entity.getReviewedAt());
    dto.setReviewComment(entity.getReviewComment());
    dto.setCanaryRatio(entity.getCanaryRatio());
    dto.setCanaryConditionExpression(entity.getCanaryConditionExpression());
    dto.setCanarySeverityExpression(entity.getCanarySeverityExpression());
    return dto;
  }

  /** DTO → Entity 转换（手动映射） */
  private RuleDefinition dtoToEntity(RuleDefinitionDTO dto) {
    RuleDefinition entity = new RuleDefinition();
    entity.setRuleCode(dto.getCode());
    entity.setRuleName(dto.getName());
    entity.setCategory(dto.getCategory());
    entity.setCategoryPath(dto.getCategoryPath());
    entity.setOwner(dto.getOwner());
    entity.setDescription(dto.getDescription());
    entity.setConditionExpression(dto.getConditionExpression());
    entity.setSeverityExpression(dto.getSeverityExpression());
    entity.setDefaultSeverity(
        dto.getDefaultSeverity() != null ? dto.getDefaultSeverity().name() : null);
    entity.setTitleTemplate(dto.getTitleTemplate());
    entity.setDescriptionTemplate(dto.getDescriptionTemplate());
    entity.setPriority(dto.getPriority());
    entity.setIsEnabled(dto.isEnabled());
    entity.setScope(dto.getScope());
    entity.setMutexGroup(dto.getMutexGroup());
    entity.setIsDrilldownAvailable(dto.isDrilldownAvailable());
    entity.setVersion(dto.getVersion());
    entity.setStatus(dto.getStatus());
    entity.setEffectiveFrom(dto.getEffectiveFrom());
    entity.setEffectiveTo(dto.getEffectiveTo());
    entity.setReviewedBy(dto.getReviewedBy());
    entity.setReviewedAt(dto.getReviewedAt());
    entity.setReviewComment(dto.getReviewComment());
    entity.setCanaryRatio(dto.getCanaryRatio());
    entity.setCanaryConditionExpression(dto.getCanaryConditionExpression());
    entity.setCanarySeverityExpression(dto.getCanarySeverityExpression());
    return entity;
  }
}

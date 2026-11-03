package com.njydsz.literule.infra.repository.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;

import com.njydsz.literule.domain.entity.RuleDefinition;
import com.njydsz.literule.domain.repository.RuleDefinitionConfigRepository;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;

/**
 * 规则定义「引擎配置源」持久化实现（Infra 层）。
 *
 * <p>实现 {@link RuleDefinitionConfigRepository}，把 {@link RuleDefinitionMapper} 的直接调用收口在 infra 内部，
 * 使 server 层的 {@code DirectDbRuleConfigProvider} 只依赖 domain 端口（YDIZ-DDD-005）。
 *
 * <p><b>注册方式（与同包其它 {@code *RepositoryImpl} 不同，勿改回 {@code @Repository}）：</b>
 * 本类由 {@code LiteruleInfraAutoConfiguration} 以 {@code @Bean} 注册，而不是靠宿主应用的组件扫描。
 * 原因：消费本 Bean 的 {@code directDbRuleConfigProvider} 使用 {@code @ConditionalOnBean} 判定，
 * 而它的可用性决定规则数据源是走 DB 还是静默回落到 NO-OP（空规则）。
 * 若依赖组件扫描，嵌入方（不扫 {@code com.njydsz.literule} 包）会得到"Mapper 已就绪、端口未注册"的组合，
 * DB 规则源会在无任何报错的情况下消失。自动装配的激活条件与 {@code @MapperScan} 完全一致，二者同生同灭。
 *
 * <p><b>语义保持要点（改动本类前必读）：</b>
 *
 * <ul>
 *   <li>{@link #listAll()} 沿用 {@code selectList(null)}：不加任何 Wrapper 条件，
 *       "启用中"的筛选由调用方按 {@code status + isEnabled} 在内存完成，不能提前下推到 SQL；
 *   <li>{@link #findByCode(String)} 查不到时返回 {@code null}（而非 Optional）——
 *       调用方依赖 null 判定"新增 or 更新"与"规则不存在则跳过启停"，改成抛异常会破坏保存链路；
 *   <li>多租户 {@code tenant_id} 与逻辑删除过滤由 MyBatis 拦截器注入，本类不感知、不得手写条件。
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@RequiredArgsConstructor
public class RuleDefinitionConfigRepositoryImpl implements RuleDefinitionConfigRepository {

  private final RuleDefinitionMapper ruleDefinitionMapper;

  @Override
  public List<RuleDefinitionVO> listAll() {
    return ruleDefinitionMapper.selectList(null).stream()
        .map(this::entityToVO)
        .toList();
  }

  @Override
  public RuleDefinitionVO findByCode(String ruleCode) {
    RuleDefinition entity = ruleDefinitionMapper.selectByCode(ruleCode);
    return entity == null ? null : entityToVO(entity);
  }

  @Override
  public int insert(RuleDefinition definition) {
    return ruleDefinitionMapper.insert(definition);
  }

  @Override
  public int updateById(RuleDefinition definition) {
    return ruleDefinitionMapper.updateById(definition);
  }

  /**
   * Entity → VO 转换（手动映射，收口在 infra 内部，满足 YDIZ-DDD-007）。
   */
  private RuleDefinitionVO entityToVO(RuleDefinition entity) {
    RuleDefinitionVO vo = new RuleDefinitionVO();
    vo.setId(entity.getId());
    vo.setRuleCode(entity.getRuleCode());
    vo.setRuleName(entity.getRuleName());
    vo.setCategory(entity.getCategory());
    vo.setCategoryPath(entity.getCategoryPath());
    vo.setOwner(entity.getOwner());
    vo.setDescription(entity.getDescription());
    vo.setConditionExpression(entity.getConditionExpression());
    vo.setSeverityExpression(entity.getSeverityExpression());
    vo.setDefaultSeverity(entity.getDefaultSeverity());
    vo.setTitleTemplate(entity.getTitleTemplate());
    vo.setDescriptionTemplate(entity.getDescriptionTemplate());
    vo.setPriority(entity.getPriority());
    vo.setIsEnabled(entity.getIsEnabled());
    vo.setScope(entity.getScope());
    vo.setMutexGroup(entity.getMutexGroup());
    vo.setIsDrilldownAvailable(entity.getIsDrilldownAvailable());
    vo.setVersion(entity.getVersion());
    vo.setRevision(entity.getRevision());
    vo.setStatus(entity.getStatus());
    vo.setTenantId(entity.getTenantId());
    vo.setEffectiveFrom(entity.getEffectiveFrom());
    vo.setEffectiveTo(entity.getEffectiveTo());
    vo.setReviewedBy(entity.getReviewedBy());
    vo.setReviewedAt(entity.getReviewedAt());
    vo.setReviewComment(entity.getReviewComment());
    vo.setCanaryRatio(entity.getCanaryRatio() != null
        ? entity.getCanaryRatio().doubleValue() : null);
    vo.setCanaryConditions(entity.getCanaryConditions());
    vo.setCanaryConditionExpression(entity.getCanaryConditionExpression());
    vo.setCanarySeverityExpression(entity.getCanarySeverityExpression());
    vo.setCreatedBy(entity.getCreatedBy());
    vo.setCreatedAt(entity.getCreatedAt());
    vo.setUpdatedBy(entity.getUpdatedBy());
    vo.setUpdatedAt(entity.getUpdatedAt());
    return vo;
  }
}

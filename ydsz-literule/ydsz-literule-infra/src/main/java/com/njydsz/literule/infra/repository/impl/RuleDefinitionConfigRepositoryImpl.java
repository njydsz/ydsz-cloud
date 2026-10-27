package com.njydsz.literule.infra.repository.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;

import com.njydsz.literule.domain.entity.RuleDefinition;
import com.njydsz.literule.domain.repository.RuleDefinitionConfigRepository;
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
  public List<RuleDefinition> listAll() {
    return ruleDefinitionMapper.selectList(null);
  }

  @Override
  public RuleDefinition findByCode(String ruleCode) {
    return ruleDefinitionMapper.selectByCode(ruleCode);
  }

  @Override
  public int insert(RuleDefinition definition) {
    return ruleDefinitionMapper.insert(definition);
  }

  @Override
  public int updateById(RuleDefinition definition) {
    return ruleDefinitionMapper.updateById(definition);
  }
}

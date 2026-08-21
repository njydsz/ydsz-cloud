package com.njydsz.literule.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.literule.domain.repository.RuleDefinitionRepository;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.infra.converter.LiteruleConverter;
import com.njydsz.literule.infra.entity.RuleDefinitionDO;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;

/**
 * 规则定义仓储实现（Infra 层）。
 *
 * <p>实现 {@link RuleDefinitionRepository} 接口，封装 {@link RuleDefinitionMapper} 的数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper</li>
 *   <li>通过 {@link LiteruleConverter} 将 Entity 转换为 VO 后返回</li>
 *   <li>返回值必须为 VO，禁止返回 infra 层 Entity（DO）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RuleDefinitionRepositoryImpl implements RuleDefinitionRepository {

  private final RuleDefinitionMapper ruleDefinitionMapper;

  private final LiteruleConverter converter = LiteruleConverter.INSTANCE;

  @Override
  public Optional<RuleDefinitionVO> findByCode(String ruleCode) {
    RuleDefinitionDO entity = ruleDefinitionMapper.selectByCode(ruleCode);
    return Optional.ofNullable(converter.entityToVO(entity));
  }

  @Override
  public Optional<RuleDefinitionVO> findById(String id) {
    RuleDefinitionDO entity = ruleDefinitionMapper.selectById(id);
    return Optional.ofNullable(converter.entityToVO(entity));
  }

  @Override
  public PageResponse<List<RuleDefinitionVO>> pageRuleDefinitions(PageQuery pageQuery) {
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<RuleDefinitionDO> page =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
            pageQuery.getEffectivePageNum(), pageQuery.getEffectivePageSize());
    LambdaQueryWrapper<RuleDefinitionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByAsc(RuleDefinitionDO::getPriority).orderByDesc(RuleDefinitionDO::getCreatedAt);
    IPage<RuleDefinitionDO> doPage = ruleDefinitionMapper.selectPage(page, wrapper);

    // DO → VO 转换，封装为框架无关的 PageResponse
    List<RuleDefinitionVO> records = converter.ruleDefinitionListToVO(doPage.getRecords());
    return PageResponse.success(
        doPage.getTotal(), doPage.getCurrent(), doPage.getSize(), records);
  }

  @Override
  public List<RuleDefinitionVO> search(
      String query, String status, String category, Boolean enabled, int offset, int limit) {
    IPage<RuleDefinitionDO> page =
        ruleDefinitionMapper.searchRules(
            buildSearchQuery(query),
            status,
            category,
            enabled,
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                (offset / Math.max(limit, 1)) + 1, Math.max(limit, 1)));
    return converter.ruleDefinitionListToVO(page.getRecords());
  }

  @Override
  public int searchCount(String query, String status, String category, Boolean enabled) {
    return ruleDefinitionMapper.searchRulesCount(buildSearchQuery(query), status, category, enabled);
  }

  @Override
  public PageResponse<List<RuleDefinitionVO>> searchPage(
      String query, String status, String category, Boolean enabled, PageQuery pageQuery) {
    IPage<RuleDefinitionDO> page =
        ruleDefinitionMapper.searchRules(
            buildSearchQuery(query),
            status,
            category,
            enabled,
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                pageQuery.getEffectivePageNum(), pageQuery.getEffectivePageSize()));
    List<RuleDefinitionVO> records = converter.ruleDefinitionListToVO(page.getRecords());
    return PageResponse.success(
        page.getTotal(), page.getCurrent(), page.getSize(), records);
  }

  /**
   * 构建搜索查询字符串
   *
   * @param query 原始搜索关键词
   * @return 处理后的搜索字符串
   */
  private String buildSearchQuery(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    return query.trim();
  }
}

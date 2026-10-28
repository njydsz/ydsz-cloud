package com.njydsz.literule.infra.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.literule.domain.entity.RuleDefinition;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;

/**
 * {@link RuleDefinitionRepositoryImpl} 单元测试（管理端查询口径）。
 *
 * <p>锁定三类易被"顺手改"破坏的契约：
 *
 * <ol>
 *   <li>出入参一律为 VO（Entity 不得外泄，这是 {@code RuleDefinitionRepository} 的接口约定）；
 *   <li>分页字段顺序为 {@code (total, pageNum, pageSize)} —— {@link PageResponse#success} 的形参顺序
 *       与直觉不同，写错不会编译失败，只会让前端翻页显示错误的总数；
 *   <li>{@code buildSearchQuery} 的三档口径：null/空白 → 传 null（Mapper 侧按"不过滤"处理），
 *       非空白 → 去首尾空格后下推；以及 offset/limit 换算页码时的除零保护。
 * </ol>
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleDefinitionRepositoryImpl — 规则定义管理端查询")
class RuleDefinitionRepositoryImplTest {

  @Mock private RuleDefinitionMapper ruleDefinitionMapper;

  private RuleDefinitionRepositoryImpl repository() {
    return new RuleDefinitionRepositoryImpl(ruleDefinitionMapper);
  }

  private static RuleDefinition entity(String ruleCode, Integer priority) {
    RuleDefinition entity = new RuleDefinition();
    entity.setId("id-" + ruleCode);
    entity.setRuleCode(ruleCode);
    entity.setRuleName(ruleCode + "-name");
    entity.setStatus("PUBLISHED");
    entity.setIsEnabled(true);
    entity.setPriority(priority);
    entity.setVersion(1);
    return entity;
  }

  /** 构造一个只承载结果、不参与查询执行的 MP 分页对象（Mapper 已被 mock）。 */
  private static Page<RuleDefinition> doPage(
      long current, long size, long total, List<RuleDefinition> records) {
    Page<RuleDefinition> page = new Page<>(current, size);
    page.setRecords(records);
    page.setTotal(total);
    return page;
  }

  @Nested
  @DisplayName("单条查询")
  class FindOne {

    @Test
    @DisplayName("findByCode 命中返回 VO、未命中返回 empty（不外泄 Entity）")
    void should_map_entity_to_vo_and_empty_on_miss() {
      when(ruleDefinitionMapper.selectByCode("R_ON")).thenReturn(entity("R_ON", 10));
      when(ruleDefinitionMapper.selectByCode("R_MISSING")).thenReturn(null);

      Optional<RuleDefinitionVO> hit = repository().findByCode("R_ON");
      Optional<RuleDefinitionVO> miss = repository().findByCode("R_MISSING");

      assertThat(hit).isPresent();
      assertThat(hit.orElseThrow().getRuleCode()).isEqualTo("R_ON");
      assertThat(miss).isEmpty();
    }

    @Test
    @DisplayName("findById 命中返回 VO、未命中返回 empty")
    void should_map_by_id_and_empty_on_miss() {
      when(ruleDefinitionMapper.selectById("id-1")).thenReturn(entity("R_ON", 10));
      when(ruleDefinitionMapper.selectById("id-x")).thenReturn(null);

      assertThat(repository().findById("id-1")).isPresent();
      assertThat(repository().findById("id-x")).isEmpty();
    }
  }

  @Nested
  @DisplayName("分页查询")
  class Paging {

    @Test
    @DisplayName("分页响应字段顺序为 (total, pageNum, pageSize)")
    void should_fill_page_metadata_in_contract_order() {
      when(ruleDefinitionMapper.<Page<RuleDefinition>>selectPage(
              any(), any(Wrapper.class)))
          .thenReturn(doPage(3L, 20L, 57L, List.of(entity("R_A", 1), entity("R_B", 2))));

      PageResponse<List<RuleDefinitionVO>> response =
          repository().pageRuleDefinitions(PageQuery.of(3, 20));

      assertThat(response.getTotal()).isEqualTo(57L);
      assertThat(response.getPageNum()).isEqualTo(3L);
      assertThat(response.getPageSize()).isEqualTo(20L);
      assertThat(response.getData())
          .extracting(RuleDefinitionVO::getRuleCode)
          .containsExactly("R_A", "R_B");
    }

    @Test
    @DisplayName("请求页码/页大小透传给 MyBatis-Plus 分页对象")
    void should_pass_requested_page_into_mp_page() {
      when(ruleDefinitionMapper.<Page<RuleDefinition>>selectPage(any(), any(Wrapper.class)))
          .thenReturn(doPage(4L, 15L, 0L, List.of()));

      ArgumentCaptor<Page<RuleDefinition>> captor = ArgumentCaptor.forClass(Page.class);
      repository().pageRuleDefinitions(PageQuery.of(4, 15));

      verify(ruleDefinitionMapper).selectPage(captor.capture(), any(Wrapper.class));
      assertThat(captor.getValue().getCurrent()).isEqualTo(4L);
      assertThat(captor.getValue().getSize()).isEqualTo(15L);
    }
  }

  @Nested
  @DisplayName("搜索")
  class Search {

    @Test
    @DisplayName("空白关键词归一为 null，非空白去首尾空格")
    void should_normalize_blank_query_to_null_and_trim_others() {
      RuleDefinitionRepositoryImpl repository = repository();
      when(ruleDefinitionMapper.searchRules(any(), any(), any(), any(), any()))
          .thenReturn(doPage(1L, 10L, 0L, List.of()));

      repository.search("   ", null, null, null, 0, 10);
      repository.search("  折扣  ", null, null, null, 0, 10);

      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
      verify(ruleDefinitionMapper, times(2))
          .searchRules(queryCaptor.capture(), any(), any(), any(), any());
      assertThat(queryCaptor.getAllValues()).containsExactly(null, "折扣");
    }

    @Test
    @DisplayName("offset/limit 换算为页码，且 limit<=0 时按 1 保护（不抛除零）")
    void should_convert_offset_into_page_number_with_divide_guard() {
      RuleDefinitionRepositoryImpl repository = repository();
      when(ruleDefinitionMapper.searchRules(any(), any(), any(), any(), any()))
          .thenReturn(doPage(1L, 1L, 0L, List.of()));

      repository.search("折扣", "PUBLISHED", null, Boolean.TRUE, 20, 10);
      repository.search("折扣", null, null, null, 5, 0);

      ArgumentCaptor<Page<RuleDefinition>> captor = ArgumentCaptor.forClass(Page.class);
      verify(ruleDefinitionMapper, times(2))
          .searchRules(any(), any(), any(), any(), captor.capture());
      // 20 / 10 + 1 = 第 3 页，每页 10 条
      assertThat(captor.getAllValues().get(0).getCurrent()).isEqualTo(3L);
      assertThat(captor.getAllValues().get(0).getSize()).isEqualTo(10L);
      // limit=0 兜底为 1，页码 = 5 / 1 + 1 = 6
      assertThat(captor.getAllValues().get(1).getCurrent()).isEqualTo(6L);
      assertThat(captor.getAllValues().get(1).getSize()).isEqualTo(1L);
    }

    @Test
    @DisplayName("searchCount 归一后的关键词与过滤条件原样下推并透传计数")
    void should_push_down_filters_and_return_count() {
      when(ruleDefinitionMapper.searchRulesCount("折扣", "PUBLISHED", "PROMO", Boolean.TRUE))
          .thenReturn(42);

      assertThat(repository().searchCount(" 折扣 ", "PUBLISHED", "PROMO", Boolean.TRUE))
          .isEqualTo(42);
    }

    @Test
    @DisplayName("searchPage 按 PageQuery 分页并组装 PageResponse")
    void should_page_search_with_requested_size() {
      when(ruleDefinitionMapper.searchRules(any(), any(), any(), any(), any()))
          .thenReturn(doPage(2L, 25L, 30L, List.of(entity("R_A", 1))));

      PageResponse<List<RuleDefinitionVO>> response =
          repository().searchPage("折扣", null, null, null, PageQuery.of(2, 25));

      assertThat(response.getTotal()).isEqualTo(30L);
      assertThat(response.getPageNum()).isEqualTo(2L);
      assertThat(response.getPageSize()).isEqualTo(25L);
      assertThat(response.getData()).hasSize(1);
    }
  }
}

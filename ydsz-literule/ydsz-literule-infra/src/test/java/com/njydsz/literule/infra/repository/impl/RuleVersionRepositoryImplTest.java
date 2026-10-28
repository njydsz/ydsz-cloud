package com.njydsz.literule.infra.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.dto.RuleVersionDTO;
import com.njydsz.literule.domain.entity.RuleDefinition;
import com.njydsz.literule.domain.entity.RuleVersionHistory;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;
import com.njydsz.literule.infra.mapper.RuleVersionHistoryMapper;

/**
 * {@link RuleVersionRepositoryImpl} 单元测试（版本快照与回滚）。
 *
 * <p>回滚是本模块唯一"改写生产规则 + 追加两条版本记录"的复合写路径，且失败时靠
 * {@code SysException} 阻断，故这里钉住四件事：
 *
 * <ol>
 *   <li>分页响应字段顺序为 {@code (total, pageNum, pageSize)}；
 *   <li>回滚前必须先落"当前版本快照"，回滚后再落"回滚记录"，两次插入缺一不可；
 *   <li>恢复到主表的实体必须重置为 DRAFT 并清空评审三字段（防止带着旧审批结论对外生效）；
 *   <li>主表更新影响行数为 0 时抛 {@code SysException}，不能静默返回"回滚成功"。
 * </ol>
 *
 * <p>{@code selectOne(Wrapper)} 在本路径上按"目标版本 → 最新版本"的固定顺序各调用一次，
 * 需要区分返回值时用连续打桩 {@code thenReturn(a, b)} 并显式注明——这是被测实现的调用顺序契约，
 * 一旦顺序变化，本测试即失败（而非默默放过）。
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleVersionRepositoryImpl — 版本记录与回滚")
class RuleVersionRepositoryImplTest {

  @Mock private RuleVersionHistoryMapper ruleVersionHistoryMapper;

  @Mock private RuleDefinitionMapper ruleDefinitionMapper;

  private RuleVersionRepositoryImpl repository() {
    return new RuleVersionRepositoryImpl(ruleVersionHistoryMapper, ruleDefinitionMapper);
  }

  private static RuleDefinitionDTO definition(String code, String conditionExpression) {
    RuleDefinitionDTO def = new RuleDefinitionDTO();
    def.setCode(code);
    def.setName(code + "-名称");
    def.setConditionExpression(conditionExpression);
    def.setPriority(10);
    def.setVersion(2);
    def.setStatus("PUBLISHED");
    return def;
  }

  private static RuleVersionHistory history(Integer version, RuleDefinitionDTO def) {
    RuleVersionHistory entity = new RuleVersionHistory();
    entity.setRuleCode("R_1");
    entity.setVersion(version);
    entity.setDefinitionJson(def == null ? null : YdszJson.toJson(def));
    entity.setOperator("tester");
    return entity;
  }

  private static RuleDefinition rule(String code, Integer version, String status) {
    RuleDefinition rule = new RuleDefinition();
    rule.setId("id-1");
    rule.setRuleCode(code);
    rule.setRuleName(code + "-名称");
    rule.setVersion(version);
    rule.setStatus(status);
    rule.setIsEnabled(true);
    rule.setDefaultSeverity("RED");
    rule.setReviewedBy("old-reviewer");
    rule.setPriority(10);
    return rule;
  }

  @Nested
  @DisplayName("版本记录")
  class VersionRecord {

    @Test
    @DisplayName("saveVersion 落库前不携带主键（版本历史只增不改）")
    void should_insert_version_history_from_dto() {
      when(ruleVersionHistoryMapper.insert(any(RuleVersionHistory.class))).thenReturn(1);
      RuleVersionDTO saveDTO = new RuleVersionDTO();
      saveDTO.setRuleCode("R_1");
      saveDTO.setVersion(3);
      saveDTO.setDefinitionJson("{\"code\":\"R_1\"}");

      repository().saveVersion(saveDTO);

      ArgumentCaptor<RuleVersionHistory> captor =
          ArgumentCaptor.forClass(RuleVersionHistory.class);
      verify(ruleVersionHistoryMapper).insert(captor.capture());
      assertThat(captor.getValue().getRuleCode()).isEqualTo("R_1");
      assertThat(captor.getValue().getVersion()).isEqualTo(3);
      assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    @DisplayName("listVersions 走 selectList 并按入参顺序转 VO")
    void should_list_versions_and_preserve_order() {
      when(ruleVersionHistoryMapper.selectList(any(Wrapper.class)))
          .thenReturn(List.of(history(2, null), history(1, null)));

      List<RuleVersionVO> versions = repository().listVersions("R_1");

      assertThat(versions).extracting(RuleVersionVO::getVersion).containsExactly(2, 1);
      ArgumentCaptor<LambdaQueryWrapper<RuleVersionHistory>> captor =
          ArgumentCaptor.forClass(LambdaQueryWrapper.class);
      verify(ruleVersionHistoryMapper).selectList(captor.capture());
      // 只断言"确实带条件对象"：MyBatis-Plus 的 lambda Wrapper 里列名与参数值都是惰性渲染
      // （eq() 只登记 ISqlSegment，getSqlSegment() 时才写入 paramNameValuePairs），
      // 而渲染需要 TableInfo 列缓存，纯 Mockito 环境拿不到，故不断言 rule_code 条件内容。
      assertThat(captor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("pageVersions 的分页字段顺序为 (total, pageNum, pageSize)")
    void should_fill_page_metadata_in_contract_order() {
      Page<RuleVersionHistory> entityPage = new Page<>(2L, 5L);
      entityPage.setTotal(8L);
      entityPage.setRecords(List.of(history(2, null)));
      when(ruleVersionHistoryMapper.<Page<RuleVersionHistory>>selectPage(any(), any()))
          .thenReturn(entityPage);

      PageResponse<List<RuleVersionVO>> response =
          repository().pageVersions("R_1", 2, 5);

      assertThat(response.getTotal()).isEqualTo(8L);
      assertThat(response.getPageNum()).isEqualTo(2L);
      assertThat(response.getPageSize()).isEqualTo(5L);
      assertThat(response.getData()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("版本定义读取")
  class FindVersionDefinition {

    @Test
    @DisplayName("版本不存在或快照为空时返回 empty")
    void should_return_empty_when_history_or_json_missing() {
      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
      assertThat(repository().findVersionDefinition("R_1", 9)).isEmpty();

      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class)))
          .thenReturn(history(2, null));
      Optional<RuleDefinitionDTO> blankJson = repository().findVersionDefinition("R_1", 2);

      assertThat(blankJson).isEmpty();
    }

    @Test
    @DisplayName("快照可解析时返回规则定义")
    void should_deserialize_stored_definition() {
      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class)))
          .thenReturn(history(2, definition("R_1", "amount > 100")));

      Optional<RuleDefinitionDTO> def = repository().findVersionDefinition("R_1", 2);

      assertThat(def).isPresent();
      assertThat(def.orElseThrow().getConditionExpression()).isEqualTo("amount > 100");
    }

    @Test
    @DisplayName("快照 JSON 截断时降级为字段空定义，且不得带出其他快照的字段值")
    void should_not_throw_on_truncated_definition_json() {
      // 先解析一条完整快照，占据线程级 JSON 缓冲区；历史上紧随其后的截断快照会读出上一条的值，
      // 回滚于是把 A 规则的定义写进 B 规则（见报告 B1-13，common-json 缓冲区残留已修）。
      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class)))
          .thenReturn(history(1, definition("R_OTHER", "amount > 999")));
      repository().findVersionDefinition("R_OTHER", 1);

      RuleVersionHistory broken = history(2, null);
      broken.setDefinitionJson("{\"code\":");
      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class))).thenReturn(broken);

      Optional<RuleDefinitionDTO> def = repository().findVersionDefinition("R_1", 2);

      // 实测：YdszJson 对截断 JSON 不报错，返回"字段全空但非 null"的对象，
      // 因此实现里的 try/catch 兜不住损坏快照——回滚会把主表刷成一条空条件规则，
      // 而 DTO 的 @Builder.Default 还会把 status 补成 PUBLISHED。降级口径需产品确认（报告未修清单）。
      assertThat(def).isPresent();
      assertThat(def.orElseThrow().getCode()).isNull();
      assertThat(def.orElseThrow().getConditionExpression()).isNull();
    }
  }

  @Nested
  @DisplayName("回滚")
  class Rollback {

    @Test
    @DisplayName("目标版本不存在时不产生任何写操作")
    void should_not_write_when_target_version_absent() {
      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class))).thenReturn(null);

      assertThat(repository().rollback("R_1", 9, "tester")).isEmpty();

      verify(ruleVersionHistoryMapper, never()).insert(any(RuleVersionHistory.class));
      verify(ruleDefinitionMapper, never()).updateById(any(RuleDefinition.class));
    }

    @Test
    @DisplayName("规则主表记录不存在时不写版本历史")
    void should_not_backup_when_rule_absent() {
      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class)))
          .thenReturn(history(2, definition("R_1", "amount > 100")));
      when(ruleDefinitionMapper.selectByCode("R_1")).thenReturn(null);

      assertThat(repository().rollback("R_1", 2, "tester")).isEmpty();

      verify(ruleVersionHistoryMapper, never()).insert(any(RuleVersionHistory.class));
    }

    @Test
    @DisplayName("主表更新 0 行时抛 SysException（不得谎报回滚成功）")
    void should_fail_loudly_when_main_update_affects_no_row() {
      RuleVersionHistory target = history(2, definition("R_1", "amount > 100"));
      // 调用顺序：目标版本 → 最新版本快照基准
      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class)))
          .thenReturn(target, target);
      when(ruleDefinitionMapper.selectByCode("R_1")).thenReturn(rule("R_1", 5, "PUBLISHED"));
      when(ruleDefinitionMapper.updateById(any(RuleDefinition.class))).thenReturn(0);

      assertThatThrownBy(() -> repository().rollback("R_1", 2, "tester"))
          .isInstanceOf(SysException.class);
    }

    @Test
    @DisplayName("回滚成功：先备份当前版本、再恢复主表为 DRAFT、最后追加回滚记录")
    void should_backup_then_restore_as_draft_and_record_rollback() {
      RuleVersionHistory target = history(2, definition("R_1", "amount > 100"));
      RuleVersionHistory latest = history(5, null);
      // 调用顺序：目标版本 → 最新版本（决定 nextVersion）
      when(ruleVersionHistoryMapper.selectOne(any(Wrapper.class)))
          .thenReturn(target, latest);
      RuleDefinition current = rule("R_1", 5, "PUBLISHED");
      RuleDefinition refreshed = rule("R_1", 7, "DRAFT");
      when(ruleDefinitionMapper.selectByCode("R_1")).thenReturn(current, refreshed);
      when(ruleDefinitionMapper.updateById(any(RuleDefinition.class))).thenReturn(1);
      when(ruleVersionHistoryMapper.insert(any(RuleVersionHistory.class))).thenReturn(1);

      Optional<RuleDefinitionVO> result = repository().rollback("R_1", 2, "tester");

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().getRuleCode()).isEqualTo("R_1");

      ArgumentCaptor<RuleDefinition> restored = ArgumentCaptor.forClass(RuleDefinition.class);
      verify(ruleDefinitionMapper).updateById(restored.capture());
      assertThat(restored.getValue().getId()).isEqualTo("id-1");
      assertThat(restored.getValue().getStatus()).isEqualTo("DRAFT");
      assertThat(restored.getValue().getVersion()).isEqualTo(7);
      assertThat(restored.getValue().getConditionExpression()).isEqualTo("amount > 100");
      // 回滚后必须重新走审批：评审结论不得沿用
      assertThat(restored.getValue().getReviewedBy()).isNull();

      ArgumentCaptor<RuleVersionHistory> inserts =
          ArgumentCaptor.forClass(RuleVersionHistory.class);
      verify(ruleVersionHistoryMapper, times(2))
          .insert(inserts.capture());
      // 第 1 条 = 回滚前快照（版本号 = 最大版本 5 + 1）；第 2 条 = 回滚记录（版本号再 +1）
      assertThat(inserts.getAllValues().get(0).getVersion()).isEqualTo(6);
      assertThat(inserts.getAllValues().get(0).getChangeDesc()).startsWith("[回滚前快照]");
      assertThat(inserts.getAllValues().get(1).getVersion()).isEqualTo(7);
      assertThat(inserts.getAllValues().get(1).getChangeDesc()).startsWith("[回滚]");
    }
  }
}

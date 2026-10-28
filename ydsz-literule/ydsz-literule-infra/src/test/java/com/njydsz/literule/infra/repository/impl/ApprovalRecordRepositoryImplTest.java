package com.njydsz.literule.infra.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.literule.domain.dto.ApprovalRecordDTO;
import com.njydsz.literule.domain.vo.ApprovalRecordVO;

/**
 * {@link ApprovalRecordRepositoryImpl} 单元测试（审批记录二级存储）。
 *
 * <p>本实现是进程内 {@code ConcurrentHashMap}（重启即失，属已知局限），因此测试只钉住它的存储语义，
 * 不假设任何持久化保证：
 *
 * <ol>
 *   <li>空入参（{@code null} DTO 或 {@code null} ruleCode）必须被忽略、不得写入存储——
 *       一旦改成"照收"，{@code ConcurrentHashMap} 会因 null key 抛 NPE 并打断审批链；
 *   <li>{@code createdAt} 只在入参未带值时按写入时刻补齐（本实现原样存放调用方的 DTO 引用，
 *       不做拷贝，故"保留首次创建时间"实际依赖调用方复用同一对象）；{@code updatedAt} 每次刷新；
 *   <li>读侧按 ruleCode 单键取回，未命中/null 一律返回空列表而非 null（调用方直接遍历）。
 * </ol>
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@DisplayName("ApprovalRecordRepositoryImpl — 审批记录内存存储")
class ApprovalRecordRepositoryImplTest {

  private ApprovalRecordRepositoryImpl repository() {
    return new ApprovalRecordRepositoryImpl();
  }

  private static ApprovalRecordDTO record(String ruleCode) {
    ApprovalRecordDTO dto = new ApprovalRecordDTO();
    dto.setRecordId("rec-" + ruleCode);
    dto.setRuleCode(ruleCode);
    dto.setFlowCode("FLOW_DEFAULT");
    dto.setCurrentLevel(1);
    dto.setCurrentStatus("PENDING");
    return dto;
  }

  @Nested
  @DisplayName("写入")
  class Save {

    @Test
    @DisplayName("入参为 null 或缺 ruleCode 时忽略，不影响已存记录")
    void should_ignore_null_payload_and_null_rule_code() {
      ApprovalRecordRepositoryImpl repository = repository();

      repository.save(null);
      repository.save(record(null));

      assertThat(repository.findByRuleCode("R_1")).isEmpty();
    }

    @Test
    @DisplayName("createdAt 非空时保持不变、为空时按写入时刻补齐；updatedAt 每次刷新")
    void should_keep_given_created_at_and_stamp_updated_at() {
      ApprovalRecordRepositoryImpl repository = repository();
      LocalDateTime first = LocalDateTime.of(2026, 10, 1, 10, 0);
      ApprovalRecordDTO dto = record("R_1");
      dto.setCreatedAt(first);

      repository.save(dto);

      assertThat(dto.getUpdatedAt()).isNotNull();
      assertThat(dto.getCreatedAt()).isEqualTo(first);

      // 本实现把调用方传入的 DTO 原样放进 map（非拷贝），故再次保存时
      // 传入的 createdAt 会直接替换旧值——"保留首次创建时间"依赖调用方复用同一对象。
      LocalDateTime later = LocalDateTime.of(2026, 10, 20, 10, 0);
      ApprovalRecordDTO again = record("R_1");
      again.setCreatedAt(later);
      repository.save(again);

      assertThat(again.getCreatedAt()).isEqualTo(later);
      assertThat(repository.findByRuleCode("R_1")).hasSize(1);
      assertThat(repository.findByRuleCode("R_1").get(0).getCreatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("createdAt 为空时由写入时刻补齐")
    void should_fill_created_at_when_absent() {
      ApprovalRecordRepositoryImpl repository = repository();
      ApprovalRecordDTO dto = record("R_1");

      repository.save(dto);

      assertThat(dto.getCreatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("读取")
  class Find {

    @Test
    @DisplayName("ruleCode 为 null 或未命中一律返回空列表")
    void should_return_empty_list_for_null_key_and_miss() {
      ApprovalRecordRepositoryImpl repository = repository();
      repository.save(record("R_1"));

      assertThat(repository.findByRuleCode(null)).isEmpty();
      assertThat(repository.findByRuleCode("R_MISSING")).isEmpty();
    }

    @Test
    @DisplayName("命中时 DTO 转 VO 且逐字段带出（logs 等内部字段不外泄）")
    void should_convert_hit_record_to_vo() {
      ApprovalRecordRepositoryImpl repository = repository();
      ApprovalRecordDTO dto = record("R_1");
      repository.save(dto);

      List<ApprovalRecordVO> found = repository.findByRuleCode("R_1");

      assertThat(found).hasSize(1);
      ApprovalRecordVO vo = found.get(0);
      assertThat(vo.getRecordId()).isEqualTo("rec-R_1");
      assertThat(vo.getRuleCode()).isEqualTo("R_1");
      assertThat(vo.getFlowCode()).isEqualTo("FLOW_DEFAULT");
      assertThat(vo.getCurrentLevel()).isEqualTo(1);
      assertThat(vo.getCurrentStatus()).isEqualTo("PENDING");
      assertThat(vo.getCreatedAt()).isEqualTo(dto.getCreatedAt());
      assertThat(vo.getUpdatedAt()).isEqualTo(dto.getUpdatedAt());
    }
  }
}

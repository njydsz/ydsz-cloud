package com.njydsz.literule.infra.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.literule.domain.dto.DecisionTableDTO;
import com.njydsz.literule.domain.entity.DecisionTable;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.infra.mapper.DecisionTableMapper;

/**
 * {@link DecisionTableRepositoryImpl} 单元测试。
 *
 * <p>{@code DecisionTableRepository#save} 的契约是"DTO 带 id 即更新，否则新增"。
 * 但 {@code RuleSupportConverter.postDtoToEntity(DecisionTableDTO)} 刻意
 * {@code @Mapping(target = "id", ignore = true)}（主键由持久层生成），若实现类不加回填，
 * 更新分支恒不可达——"编辑决策表"会退化成"插入同 table_code 的新行"。
 * 本测试把这条契约钉死在实现层（见 should_update_when_dto_carries_id）。
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionTableRepositoryImpl — 决策表读写")
class DecisionTableRepositoryImplTest {

  @Mock private DecisionTableMapper decisionTableMapper;

  private DecisionTableRepositoryImpl repository() {
    return new DecisionTableRepositoryImpl(decisionTableMapper);
  }

  private static DecisionTable entity(Long id, String tableCode) {
    DecisionTable entity = new DecisionTable();
    entity.setId(id == null ? null : String.valueOf(id));
    entity.setTableCode(tableCode);
    entity.setTableName(tableCode + "-表");
    entity.setIsEnabled(true);
    entity.setPriority(100);
    return entity;
  }

  private static DecisionTableDTO dto(Long id, String tableCode) {
    DecisionTableDTO dto = new DecisionTableDTO();
    dto.setId(id);
    dto.setTableCode(tableCode);
    dto.setTableName(tableCode + "-表");
    dto.setIsEnabled(true);
    dto.setPriority(100);
    return dto;
  }

  @Nested
  @DisplayName("查询")
  class Read {

    @Test
    @DisplayName("findAll 全量读取并转 VO，顺序与入参一致")
    void should_list_all_and_convert_to_vo() {
      when(decisionTableMapper.selectList(null))
          .thenReturn(List.of(entity(1L, "T_A"), entity(2L, "T_B")));

      List<DecisionTableVO> result = repository().findAll();

      assertThat(result)
          .extracting(DecisionTableVO::getTableCode)
          .containsExactly("T_A", "T_B");
    }

    @Test
    @DisplayName("findByTableCode 命中转 VO、未命中返回 empty")
    void should_find_by_table_code() {
      when(decisionTableMapper.selectOne(any(Wrapper.class))).thenReturn(entity(1L, "T_A"));
      assertThat(repository().findByTableCode("T_A"))
          .containsInstanceOf(DecisionTableVO.class);

      when(decisionTableMapper.selectOne(any(Wrapper.class))).thenReturn(null);
      Optional<DecisionTableVO> miss = repository().findByTableCode("T_MISSING");

      assertThat(miss).isEmpty();
    }
  }

  @Nested
  @DisplayName("保存")
  class Save {

    @Test
    @DisplayName("DTO 带 id 时走 updateById（编辑不得退化为新增）")
    void should_update_when_dto_carries_id() {
      when(decisionTableMapper.updateById(any(DecisionTable.class))).thenReturn(1);

      DecisionTableVO saved = repository().save(dto(7L, "T_EDIT"));

      ArgumentCaptor<DecisionTable> captor = ArgumentCaptor.forClass(DecisionTable.class);
      verify(decisionTableMapper).updateById(captor.capture());
      verify(decisionTableMapper, never()).insert(any(DecisionTable.class));
      assertThat(captor.getValue().getId()).isEqualTo("7");
      assertThat(captor.getValue().getTableCode()).isEqualTo("T_EDIT");
      assertThat(saved.getId()).isEqualTo("7");
    }

    @Test
    @DisplayName("DTO 不带 id 时走 insert 且不写入主键")
    void should_insert_when_dto_has_no_id() {
      when(decisionTableMapper.insert(any(DecisionTable.class))).thenReturn(1);

      repository().save(dto(null, "T_NEW"));

      ArgumentCaptor<DecisionTable> captor = ArgumentCaptor.forClass(DecisionTable.class);
      verify(decisionTableMapper).insert(captor.capture());
      verify(decisionTableMapper, never()).updateById(any(DecisionTable.class));
      assertThat(captor.getValue().getId()).isNull();
      assertThat(captor.getValue().getTableCode()).isEqualTo("T_NEW");
    }

    @Test
    @DisplayName("deleteById 按主键删除")
    void should_delete_by_id() {
      repository().deleteById("7");

      verify(decisionTableMapper).deleteById("7");
    }
  }
}

package com.njydsz.literule.server.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.entity.RuleDefinition;
import com.njydsz.literule.domain.repository.RuleDefinitionConfigRepository;
import com.njydsz.literule.server.spi.RuleConfigProvider;

/**
 * {@link DirectDbRuleConfigProvider} 单元测试。
 *
 * <p>26.10.27 分层整改：本提供者由直接持有 infra {@code RuleDefinitionMapper} 改为依赖 domain 端口
 * {@link RuleDefinitionConfigRepository}。本测试锁定改造前后必须保持的 4 条 DB 语义，
 * 防止端口方法被重写为"顺手加个过滤/改成 Optional"而导致规则加载行为漂移。
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DirectDbRuleConfigProvider — DB 规则源语义")
class DirectDbRuleConfigProviderTest {

  @Mock private RuleDefinitionConfigRepository configRepository;

  /**
   * 构造与 {@code ydsz_rule_def} 约束一致的实体样本。
   *
   * <p>{@code priority / is_enabled / version} 在三种方言 DDL 里均为 {@code NOT NULL DEFAULT}
   * （postgres {@code priority INTEGER NOT NULL DEFAULT 100}，mysql/oracle 同义），
   * 而 {@code DirectDbRuleConfigProvider#entityToDto} 把这三列映射到 DTO 的基本类型字段，
   * 因此样本必须带值——把 priority 留空会在映射时触发 {@code Integer.intValue()} NPE，
   * 那是一条只有 DDL 改成可空才会出现的真实故障路径，不属于本测试要锁定的语义。
   */
  private static RuleDefinition entity(String ruleCode, String status, Boolean enabled) {
    RuleDefinition entity = new RuleDefinition();
    entity.setRuleCode(ruleCode);
    entity.setRuleName(ruleCode + "-name");
    entity.setStatus(status);
    entity.setIsEnabled(enabled);
    entity.setPriority(Rule.DEFAULT_PRIORITY);
    entity.setVersion(1);
    return entity;
  }

  @Nested
  @DisplayName("规则加载")
  class LoadRules {

    @Test
    @DisplayName("loadEnabledRules 只保留 PUBLISHED 且 isEnabled=true 的规则")
    void should_keep_only_published_and_enabled_rules() {
      when(configRepository.listAll())
          .thenReturn(
              List.of(
                  entity("R_ON", "PUBLISHED", true),
                  entity("R_DRAFT", "DRAFT", true),
                  entity("R_OFF", "PUBLISHED", false)));

      DirectDbRuleConfigProvider provider =
          new DirectDbRuleConfigProvider(configRepository);

      List<String> codes =
          provider.loadEnabledRules().stream().map(RuleDefinitionDTO::getCode).toList();

      assertThat(codes).containsExactly("R_ON");
      assertThat(provider.getType()).isEqualTo(RuleConfigProvider.SourceType.DB);
      assertThat(provider.supportsWatch()).isFalse();
    }

    @Test
    @DisplayName("loadAllRules 不做任何状态过滤（全量口径由调用方负责）")
    void should_return_every_rule_without_filtering() {
      when(configRepository.listAll())
          .thenReturn(List.of(entity("R_ON", "PUBLISHED", true), entity("R_DRAFT", "DRAFT", false)));

      DirectDbRuleConfigProvider provider =
          new DirectDbRuleConfigProvider(configRepository);

      assertThat(provider.loadAllRules())
          .extracting(RuleDefinitionDTO::getCode)
          .containsExactly("R_ON", "R_DRAFT");
    }
  }

  @Nested
  @DisplayName("规则保存")
  class SaveRule {

    @Test
    @DisplayName("编码不存在时走 insert 且不携带旧主键")
    void should_insert_without_id_when_rule_is_new() {
      when(configRepository.findByCode("R_NEW")).thenReturn(null, entity("R_NEW", "DRAFT", true));

      DirectDbRuleConfigProvider provider =
          new DirectDbRuleConfigProvider(configRepository);
      RuleDefinitionDTO dto = new RuleDefinitionDTO();
      dto.setCode("R_NEW");

      RuleDefinitionDTO saved = provider.save(dto, "tester");

      ArgumentCaptor<RuleDefinition> captor = ArgumentCaptor.forClass(RuleDefinition.class);
      verify(configRepository).insert(captor.capture());
      verify(configRepository, never()).updateById(any());
      assertThat(captor.getValue().getId()).isNull();
      // RuleDefinitionDTO.version 是基本类型 int（@Builder.Default = 1），
      // 因此 provider 里"version 为空时补 1"的兜底分支在本路径上恒不触发，实际生效值就是 1。
      assertThat(captor.getValue().getVersion()).isEqualTo(1);
      assertThat(saved).isNotNull();
      assertThat(saved.getCode()).isEqualTo("R_NEW");
    }

    @Test
    @DisplayName("编码已存在时沿用原主键走 updateById")
    void should_reuse_existing_id_when_rule_already_exists() {
      RuleDefinition existing = entity("R_OLD", "PUBLISHED", true);
      existing.setId("id-1");
      when(configRepository.findByCode("R_OLD")).thenReturn(existing, existing);

      DirectDbRuleConfigProvider provider =
          new DirectDbRuleConfigProvider(configRepository);
      RuleDefinitionDTO dto = new RuleDefinitionDTO();
      dto.setCode("R_OLD");

      provider.save(dto, "tester");

      ArgumentCaptor<RuleDefinition> captor = ArgumentCaptor.forClass(RuleDefinition.class);
      verify(configRepository).updateById(captor.capture());
      verify(configRepository, never()).insert(any());
      assertThat(captor.getValue().getId()).isEqualTo("id-1");
    }
  }

  @Nested
  @DisplayName("规则启停")
  class ToggleEnabled {

    @Test
    @DisplayName("规则不存在时不产生任何写操作")
    void should_skip_write_when_rule_not_found() {
      when(configRepository.findByCode("R_MISSING")).thenReturn(null);

      DirectDbRuleConfigProvider provider =
          new DirectDbRuleConfigProvider(configRepository);

      provider.toggleEnabled("R_MISSING", false, "tester");

      verify(configRepository, never()).updateById(any());
      verify(configRepository, never()).insert(any());
    }

    @Test
    @DisplayName("规则存在时按原实体改写 isEnabled 后更新")
    void should_update_enabled_flag_on_existing_rule() {
      RuleDefinition existing = entity("R_ON", "PUBLISHED", true);
      existing.setId("id-2");
      when(configRepository.findByCode("R_ON")).thenReturn(existing);

      DirectDbRuleConfigProvider provider =
          new DirectDbRuleConfigProvider(configRepository);

      provider.toggleEnabled("R_ON", false, "tester");

      ArgumentCaptor<RuleDefinition> captor = ArgumentCaptor.forClass(RuleDefinition.class);
      verify(configRepository).updateById(captor.capture());
      assertThat(captor.getValue().getIsEnabled()).isFalse();
      assertThat(captor.getValue().getId()).isEqualTo("id-2");
    }

    @Test
    @DisplayName("findByCode 查不到规则时返回 null（而非空对象）")
    void should_return_null_dto_when_rule_absent() {
      when(configRepository.findByCode("R_MISSING")).thenReturn(null);

      DirectDbRuleConfigProvider provider =
          new DirectDbRuleConfigProvider(configRepository);

      assertThat(provider.findByCode("R_MISSING")).isNull();
    }
  }
}

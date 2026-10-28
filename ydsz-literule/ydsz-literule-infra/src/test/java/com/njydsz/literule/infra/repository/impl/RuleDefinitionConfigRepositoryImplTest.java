package com.njydsz.literule.infra.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.njydsz.literule.domain.entity.RuleDefinition;
import com.njydsz.literule.domain.repository.RuleDefinitionConfigRepository;
import com.njydsz.literule.infra.config.LiteruleInfraAutoConfiguration;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;

/**
 * {@link RuleDefinitionConfigRepositoryImpl} 单元测试。
 *
 * <p>本实现是 server 层 {@code DirectDbRuleConfigProvider} 唯一的 DB 出入口，因此这里锁定的是
 * 类 Javadoc 写明的四条语义契约，防止后续"顺手优化"改变引擎看到的规则集合：
 *
 * <ol>
 *   <li>{@code listAll()} 必须以 {@code null} Wrapper 查询，并把结果原样返回——状态/启用过滤一旦下推到
 *       SQL，DRAFT 规则就再也无法被 {@code loadAllRules()}（管理端全量口径）读到；
 *   <li>{@code findByCode()} 查不到时返回 {@code null}，改成 {@code Optional} 或抛异常会让保存链路
 *       无法区分"新增 or 更新"，启停链路会由"跳过"变成"报错"；
 *   <li>{@code insert()} / {@code updateById()} 的影响行数必须原样透传，调用方据此判定写入是否发生；
 *   <li>本类不得带组件注解——Bean 由 {@link LiteruleInfraAutoConfiguration} 以 {@code @Bean} 注册，
 *       否则嵌入方不扫描 {@code com.njydsz.literule} 时端口缺失，DB 规则源会静默回落成 NO-OP（空规则）。
 * </ol>
 *
 * <p>多租户 {@code tenant_id} 与逻辑删除过滤由 MyBatis 拦截器注入，不在 Mapper 接口上体现，
 * 因此本测试不涉及（也无法用 Mapper mock 覆盖），其正确性由 {@code DddLayerArchTest} 与集成环境验证。
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleDefinitionConfigRepositoryImpl — 引擎配置源持久化语义")
class RuleDefinitionConfigRepositoryImplTest {

  @Mock private RuleDefinitionMapper ruleDefinitionMapper;

  private RuleDefinitionConfigRepositoryImpl repository() {
    return new RuleDefinitionConfigRepositoryImpl(ruleDefinitionMapper);
  }

  private static RuleDefinition entity(String ruleCode, String status, Boolean enabled) {
    RuleDefinition entity = new RuleDefinition();
    entity.setRuleCode(ruleCode);
    entity.setStatus(status);
    entity.setIsEnabled(enabled);
    return entity;
  }

  @Nested
  @DisplayName("全量读取")
  class ListAll {

    @Test
    @DisplayName("以 null Wrapper 查询：过滤条件下推 SQL 即判红")
    void should_query_without_any_wrapper() {
      when(ruleDefinitionMapper.selectList(null)).thenReturn(List.of());

      repository().listAll();

      // 断言实参确为 null：一旦改成 LambdaQueryWrapper/状态过滤，本 verify 失配。
      verify(ruleDefinitionMapper).selectList(null);
      verifyNoMoreInteractions(ruleDefinitionMapper);
    }

    @Test
    @DisplayName("结果原样返回，DRAFT 与停用规则不被剔除")
    void should_pass_through_every_row_untouched() {
      List<RuleDefinition> rows =
          List.of(
              entity("R_ON", "PUBLISHED", true),
              entity("R_DRAFT", "DRAFT", true),
              entity("R_OFF", "PUBLISHED", false));
      when(ruleDefinitionMapper.selectList(null)).thenReturn(rows);

      assertThat(repository().listAll()).isSameAs(rows);
    }

    @Test
    @DisplayName("无数据时返回空列表而非 null")
    void should_return_empty_list_when_no_rule() {
      when(ruleDefinitionMapper.selectList(null)).thenReturn(List.of());

      assertThat(repository().listAll()).isEmpty();
    }
  }

  @Nested
  @DisplayName("按编码读取")
  class FindByCode {

    @Test
    @DisplayName("查不到时返回 null（调用方据此区分新增与更新）")
    void should_return_null_when_rule_absent() {
      when(ruleDefinitionMapper.selectByCode("R_MISSING")).thenReturn(null);

      assertThat(repository().findByCode("R_MISSING")).isNull();
    }

    @Test
    @DisplayName("编码原样透传给 selectByCode 并返回同一实体")
    void should_delegate_code_and_return_same_entity() {
      RuleDefinition found = entity("R_ON", "PUBLISHED", true);
      when(ruleDefinitionMapper.selectByCode("R_ON")).thenReturn(found);

      assertThat(repository().findByCode("R_ON")).isSameAs(found);
      verify(ruleDefinitionMapper).selectByCode("R_ON");
    }
  }

  @Nested
  @DisplayName("写操作")
  class Write {

    @Test
    @DisplayName("insert 影响行数原样透传（0 行不被掩盖成成功）")
    void should_return_insert_affected_rows() {
      RuleDefinition definition = entity("R_NEW", "DRAFT", true);
      when(ruleDefinitionMapper.insert(definition)).thenReturn(1);

      assertThat(repository().insert(definition)).isEqualTo(1);

      when(ruleDefinitionMapper.insert(definition)).thenReturn(0);

      assertThat(repository().insert(definition)).isZero();
    }

    @Test
    @DisplayName("updateById 影响行数原样透传")
    void should_return_update_affected_rows() {
      RuleDefinition definition = entity("R_ON", "PUBLISHED", false);
      definition.setId("id-1");
      when(ruleDefinitionMapper.updateById(definition)).thenReturn(1);

      assertThat(repository().updateById(definition)).isEqualTo(1);
      verify(ruleDefinitionMapper).updateById(definition);
    }
  }

  @Nested
  @DisplayName("Bean 注册方式")
  class Registration {

    @Test
    @DisplayName("实现类不带组件注解：改回 @Repository 会依赖宿主扫描范围")
    void should_not_rely_on_component_scan() {
      assertThat(RuleDefinitionConfigRepositoryImpl.class.getAnnotation(Repository.class)).isNull();
      assertThat(RuleDefinitionConfigRepositoryImpl.class.getAnnotation(Component.class)).isNull();
    }

    @Test
    @DisplayName("自动配置方法真实构造出端口实现（而非仅声明存在）")
    void should_be_registered_as_bean_by_auto_configuration() throws Exception {
      Method beanMethod =
          LiteruleInfraAutoConfiguration.class.getMethod(
              "ruleDefinitionConfigRepository", RuleDefinitionMapper.class);

      assertThat(beanMethod.isAnnotationPresent(Bean.class)).isTrue();
      assertThat(beanMethod.getReturnType()).isEqualTo(RuleDefinitionConfigRepository.class);
      assertThat(beanMethod.invoke(new LiteruleInfraAutoConfiguration(), ruleDefinitionMapper))
          .isInstanceOf(RuleDefinitionConfigRepositoryImpl.class);
    }
  }
}

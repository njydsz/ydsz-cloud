package com.njydsz.pmis.project.literule;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.project.entity.RuleDefinitionDO;
import com.njydsz.pmis.project.entity.RuleVersionHistoryDO;
import com.njydsz.pmis.project.mapper.RuleDefinitionMapper;
import com.njydsz.pmis.project.mapper.RuleVersionHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RuleVersionRepositoryImpl 单元测试
 *
 * <p>覆盖版本快照保存、版本列表查询、回滚多步事务逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("规则版本仓库实现测试")
class RuleVersionRepositoryImplTest {

    @Mock
    private RuleVersionHistoryMapper versionMapper;

    @Mock
    private RuleDefinitionMapper ruleDefinitionMapper;

    @InjectMocks
    private RuleVersionRepositoryImpl repository;

    @Test
    @DisplayName("saveVersion - 应序列化 Definition 并插入版本历史")
    void saveVersionShouldSerializeAndInsert() {
        RuleDefinition def = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 1000")
                .defaultSeverity(RuleSeverity.YELLOW)
                .version(3)
                .build();

        repository.saveVersion(def, "admin", "修改条件表达式");

        ArgumentCaptor<RuleVersionHistoryDO> captor = ArgumentCaptor.forClass(RuleVersionHistoryDO.class);
        verify(versionMapper, times(1)).insert(captor.capture());

        RuleVersionHistoryDO saved = captor.getValue();
        assertThat(saved.getRuleCode()).isEqualTo("R001");
        assertThat(saved.getVersion()).isEqualTo(3);
        assertThat(saved.getOperator()).isEqualTo("admin");
        assertThat(saved.getChangeDesc()).isEqualTo("修改条件表达式");
        assertThat(saved.getDefinitionJson()).contains("amount > 1000");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("listVersions - 应返回版本列表")
    void listVersionsShouldReturnVersionList() {
        RuleVersionHistoryDO v1 = buildVersionHistory(1L, "R001", 1, "初始化");
        RuleVersionHistoryDO v2 = buildVersionHistory(2L, "R001", 2, "修改条件");
        when(versionMapper.listByCode("R001")).thenReturn(List.of(v1, v2));

        List<RuleVersion> versions = repository.listVersions("R001");

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo(1);
        assertThat(versions.get(1).getVersion()).isEqualTo(2);
        assertThat(versions.get(0).getChangeDesc()).isEqualTo("初始化");
    }

    @Test
    @DisplayName("listVersions - 无版本记录时返回空列表")
    void listVersionsShouldReturnEmptyWhenNoHistory() {
        when(versionMapper.listByCode("R999")).thenReturn(List.of());

        List<RuleVersion> versions = repository.listVersions("R999");

        assertThat(versions).isEmpty();
    }

    @Test
    @DisplayName("rollback - 版本不存在应抛 IllegalArgumentException")
    void rollbackShouldThrowWhenVersionNotFound() {
        when(versionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> repository.rollback("R001", 99, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本不存在");
    }

    @Test
    @DisplayName("rollback - 规则主表记录不存在应抛 IllegalStateException")
    void rollbackShouldThrowWhenRuleNotFound() {
        RuleVersionHistoryDO versionHistory = buildVersionHistory(1L, "R001", 1, "初始化");
        versionHistory.setDefinitionJson(JSON.toJSONString(buildDefinition("R001", "amount > 1000")));
        when(versionMapper.selectOne(any())).thenReturn(versionHistory);
        when(ruleDefinitionMapper.selectByCode("R001")).thenReturn(null);

        assertThatThrownBy(() -> repository.rollback("R001", 1, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("规则主表记录不存在");
    }

    @Test
    @DisplayName("rollback - 应从快照恢复并版本号+1")
    void rollbackShouldRestoreFromSnapshotAndIncrementVersion() {
        // 目标版本快照（v1 的条件是 amount > 500）
        RuleDefinition snapshot = buildDefinition("R001", "amount > 500");
        RuleVersionHistoryDO versionHistory = buildVersionHistory(1L, "R001", 1, "初始化");
        versionHistory.setDefinitionJson(JSON.toJSONString(snapshot));
        when(versionMapper.selectOne(any())).thenReturn(versionHistory);

        // 主表现状（v2，条件是 amount > 1000）
        RuleDefinitionDO existing = buildRuleDefinitionDO("R001", "amount > 1000", 2);
        when(ruleDefinitionMapper.selectByCode("R001")).thenReturn(existing);

        RuleDefinition result = repository.rollback("R001", 1, "admin");

        // 验证主表更新：条件恢复为快照值，版本号 +1
        ArgumentCaptor<RuleDefinitionDO> captor = ArgumentCaptor.forClass(RuleDefinitionDO.class);
        verify(ruleDefinitionMapper, times(1)).updateById(captor.capture());
        RuleDefinitionDO updated = captor.getValue();
        assertThat(updated.getConditionExpression()).isEqualTo("amount > 500");
        assertThat(updated.getVersion()).isEqualTo(3);  // 2 + 1 = 3
        assertThat(updated.getUpdatedBy()).isEqualTo("admin");

        // 验证回滚后保存了新的版本快照
        verify(versionMapper, times(1)).insert(any(RuleVersionHistoryDO.class));

        // 验证返回值
        assertThat(result.getCode()).isEqualTo("R001");
        assertThat(result.getConditionExpression()).isEqualTo("amount > 500");
        assertThat(result.getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("rollback - defaultSeverity 为 null 时主表默认 YELLOW")
    void rollbackShouldDefaultToYellowWhenSeverityNull() {
        // 快照中 defaultSeverity = null
        RuleDefinition snapshot = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 500")
                .defaultSeverity(null)
                .version(1)
                .build();
        RuleVersionHistoryDO versionHistory = buildVersionHistory(1L, "R001", 1, "初始化");
        versionHistory.setDefinitionJson(JSON.toJSONString(snapshot));
        when(versionMapper.selectOne(any())).thenReturn(versionHistory);

        RuleDefinitionDO existing = buildRuleDefinitionDO("R001", "amount > 1000", 2);
        when(ruleDefinitionMapper.selectByCode("R001")).thenReturn(existing);

        repository.rollback("R001", 1, "admin");

        ArgumentCaptor<RuleDefinitionDO> captor = ArgumentCaptor.forClass(RuleDefinitionDO.class);
        verify(ruleDefinitionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDefaultSeverity()).isEqualTo("YELLOW");
    }

    // ============ 辅助方法 ============

    private RuleVersionHistoryDO buildVersionHistory(Long id, String ruleCode, int version, String desc) {
        RuleVersionHistoryDO DO = new RuleVersionHistoryDO();
        DO.setId(id);
        DO.setRuleCode(ruleCode);
        DO.setVersion(version);
        DO.setChangeDesc(desc);
        DO.setOperator("admin");
        return DO;
    }

    private RuleDefinition buildDefinition(String code, String conditionExpr) {
        return RuleDefinition.builder()
                .code(code)
                .name("测试规则")
                .conditionExpression(conditionExpr)
                .defaultSeverity(RuleSeverity.YELLOW)
                .version(1)
                .build();
    }

    private RuleDefinitionDO buildRuleDefinitionDO(String code, String conditionExpr, int version) {
        RuleDefinitionDO DO = new RuleDefinitionDO();
        DO.setId(1L);
        DO.setRuleCode(code);
        DO.setRuleName("测试规则");
        DO.setConditionExpression(conditionExpr);
        DO.setDefaultSeverity("RED");
        DO.setVersion(version);
        DO.setEnabled(true);
        DO.setDrilldownAvailable(true);
        return DO;
    }
}

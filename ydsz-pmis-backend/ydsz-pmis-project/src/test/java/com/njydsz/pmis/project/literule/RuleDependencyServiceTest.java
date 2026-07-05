package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.project.entity.RuleDependencyDO;
import com.njydsz.pmis.project.mapper.RuleDependencyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RuleDependencyService 单元测试
 *
 * <p>覆盖核心图算法：DFS 循环检测、BFS 级联禁用、依赖 CRUD、参数校验。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("规则依赖关系服务测试")
class RuleDependencyServiceTest {

    @Mock
    private RuleDependencyMapper ruleDependencyMapper;

    @InjectMocks
    private RuleDependencyService service;

    /** 模拟数据库中的全部依赖记录 */
    private List<RuleDependencyDO> db;

    @BeforeEach
    void setUp() {
        db = new ArrayList<>();
        // selectList(null) 返回全部记录（用于构建邻接表）
        lenient().when(ruleDependencyMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(db));
        // selectByRuleCode 返回 ruleCode 匹配的记录
        lenient().when(ruleDependencyMapper.selectByRuleCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return db.stream().filter(d -> code.equals(d.getRuleCode())).toList();
        });
        // selectByDependsOn 返回被依赖的记录
        lenient().when(ruleDependencyMapper.selectByDependsOn(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return db.stream().filter(d -> code.equals(d.getDependsOnRuleCode())).toList();
        });
        // selectCascadingByDependsOn 返回级联禁用记录
        lenient().when(ruleDependencyMapper.selectCascadingByDependsOn(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return db.stream()
                    .filter(d -> code.equals(d.getDependsOnRuleCode()) && Boolean.TRUE.equals(d.getCascadeOnDisable()))
                    .toList();
        });
        // insert 将记录加入 db
        lenient().when(ruleDependencyMapper.insert(any(RuleDependencyDO.class))).thenAnswer(inv -> {
            RuleDependencyDO entity = inv.getArgument(0);
            entity.setId((long) (db.size() + 1));
            db.add(entity);
            return 1;
        });
        // deleteById 删除指定 id
        lenient().when(ruleDependencyMapper.deleteById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            int before = db.size();
            db.removeIf(d -> id.equals(d.getId()));
            return before - db.size();
        });
    }

    // ============ 参数校验 ============

    @Test
    @DisplayName("add - ruleCode 为空应抛 IllegalArgumentException")
    void addShouldThrowWhenRuleCodeBlank() {
        assertThatThrownBy(() -> service.add("", "R002", "EXECUTE", true, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruleCode");
    }

    @Test
    @DisplayName("add - dependsOnRuleCode 为空应抛 IllegalArgumentException")
    void addShouldThrowWhenDependsOnBlank() {
        assertThatThrownBy(() -> service.add("R001", "", "EXECUTE", true, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dependsOnRuleCode");
    }

    @Test
    @DisplayName("add - 规则不能依赖自身")
    void addShouldThrowWhenSelfDependency() {
        assertThatThrownBy(() -> service.add("R001", "R001", "EXECUTE", true, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("自身");
    }

    // ============ 依赖 CRUD ============

    @Test
    @DisplayName("add - 新增依赖成功")
    void addShouldInsertNewDependency() {
        when(ruleDependencyMapper.selectByRuleCode("R001")).thenReturn(Collections.emptyList());

        RuleDependencyDO result = service.add("R001", "R002", "EXECUTE", true, "测试依赖", "admin");

        assertThat(result.getRuleCode()).isEqualTo("R001");
        assertThat(result.getDependsOnRuleCode()).isEqualTo("R002");
        assertThat(result.getDependencyType()).isEqualTo("EXECUTE");
        verify(ruleDependencyMapper, times(1)).insert(any(RuleDependencyDO.class));
    }

    @Test
    @DisplayName("add - dependencyType 为空时默认 EXECUTE")
    void addShouldDefaultDependencyTypeToExecute() {
        when(ruleDependencyMapper.selectByRuleCode("R001")).thenReturn(Collections.emptyList());

        RuleDependencyDO result = service.add("R001", "R002", null, true, null, "admin");

        assertThat(result.getDependencyType()).isEqualTo("EXECUTE");
    }

    @Test
    @DisplayName("add - 重复依赖直接返回已有记录，不重复插入")
    void addShouldReturnExistingWhenDuplicate() {
        RuleDependencyDO existing = new RuleDependencyDO();
        existing.setId(1L);
        existing.setRuleCode("R001");
        existing.setDependsOnRuleCode("R002");
        existing.setDependencyType("EXECUTE");
        when(ruleDependencyMapper.selectByRuleCode("R001")).thenReturn(List.of(existing));

        RuleDependencyDO result = service.add("R001", "R002", "EXECUTE", true, null, "admin");

        assertThat(result).isEqualTo(existing);
        verify(ruleDependencyMapper, never()).insert(any(RuleDependencyDO.class));
    }

    @Test
    @DisplayName("remove - 删除已存在的依赖")
    void removeShouldDeleteExisting() {
        RuleDependencyDO dep = new RuleDependencyDO();
        dep.setId(1L);
        dep.setRuleCode("R001");
        dep.setDependsOnRuleCode("R002");
        when(ruleDependencyMapper.selectByRuleCode("R001")).thenReturn(List.of(dep));

        service.remove("R001", "R002");

        verify(ruleDependencyMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("remove - 依赖不存在时不调用 deleteById")
    void removeShouldNotDeleteWhenNotFound() {
        when(ruleDependencyMapper.selectByRuleCode("R001")).thenReturn(Collections.emptyList());

        service.remove("R001", "R002");

        verify(ruleDependencyMapper, never()).deleteById(anyLong());
    }

    // ============ 查询 ============

    @Test
    @DisplayName("listDependencies - 返回正向依赖列表")
    void listDependenciesShouldReturnForwardDeps() {
        RuleDependencyDO dep = new RuleDependencyDO();
        dep.setRuleCode("R001");
        dep.setDependsOnRuleCode("R002");
        when(ruleDependencyMapper.selectByRuleCode("R001")).thenReturn(List.of(dep));

        List<RuleDependencyDO> result = service.listDependencies("R001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDependsOnRuleCode()).isEqualTo("R002");
    }

    @Test
    @DisplayName("listDependents - 返回反向依赖列表")
    void listDependentsShouldReturnReverseDeps() {
        RuleDependencyDO dep = new RuleDependencyDO();
        dep.setRuleCode("R001");
        dep.setDependsOnRuleCode("R002");
        when(ruleDependencyMapper.selectByDependsOn("R002")).thenReturn(List.of(dep));

        List<RuleDependencyDO> result = service.listDependents("R002");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRuleCode()).isEqualTo("R001");
    }

    @Test
    @DisplayName("listDependencies - ruleCode 为 null 返回空列表")
    void listDependenciesShouldReturnEmptyWhenNullCode() {
        assertThat(service.listDependencies(null)).isEmpty();
    }

    // ============ 循环依赖检测（DFS） ============

    @Test
    @DisplayName("detectCycle - 无环时返回空列表")
    void detectCycleShouldReturnEmptyWhenNoCycle() {
        // R001 -> R002 -> R003（线性，无环）
        addDep("R001", "R002");
        addDep("R002", "R003");

        List<String> cycle = service.detectCycle("R001");

        assertThat(cycle).isEmpty();
    }

    @Test
    @DisplayName("detectCycle - 检测到简单环 R001 -> R002 -> R001")
    void detectCycleShouldFindSimpleCycle() {
        addDep("R001", "R002");
        addDep("R002", "R001");

        List<String> cycle = service.detectCycle("R001");

        assertThat(cycle).isNotEmpty();
        assertThat(cycle.get(0)).isEqualTo("R001");
        assertThat(cycle.get(cycle.size() - 1)).isEqualTo("R001");
    }

    @Test
    @DisplayName("detectCycle - 检测到三节点环 R001 -> R002 -> R003 -> R001")
    void detectCycleShouldFindThreeNodeCycle() {
        addDep("R001", "R002");
        addDep("R002", "R003");
        addDep("R003", "R001");

        List<String> cycle = service.detectCycle("R001");

        assertThat(cycle).isNotEmpty();
        assertThat(cycle.get(0)).isEqualTo("R001");
        assertThat(cycle.get(cycle.size() - 1)).isEqualTo("R001");
        assertThat(cycle).hasSize(4); // R001, R002, R003, R001
    }

    @Test
    @DisplayName("detectCycle - 菱形依赖（非环）不应误报")
    void detectCycleShouldNotReportDiamondDependency() {
        // R001 -> R002 -> R004
        // R001 -> R003 -> R004（菱形汇聚，非环）
        addDep("R001", "R002");
        addDep("R001", "R003");
        addDep("R002", "R004");
        addDep("R003", "R004");

        List<String> cycle = service.detectCycle("R001");

        assertThat(cycle).isEmpty();
    }

    @Test
    @DisplayName("add - 添加后形成环应回滚并抛异常")
    void addShouldRollbackWhenCycleDetected() {
        // 已有 R002 -> R001
        addDep("R002", "R001");
        when(ruleDependencyMapper.selectByRuleCode("R001")).thenReturn(Collections.emptyList());

        // 尝试添加 R001 -> R002，会形成环
        assertThatThrownBy(() -> service.add("R001", "R002", "EXECUTE", true, null, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("循环依赖");

        // 验证回滚：deleteById 被调用
        verify(ruleDependencyMapper, times(1)).deleteById(anyLong());
    }

    // ============ 级联禁用（BFS） ============

    @Test
    @DisplayName("cascadingDisable - 单层级联禁用")
    void cascadingDisableShouldReturnDirectDependents() {
        // R001 <- R002（R002 依赖 R001，cascade=true）
        addCascadeDep("R002", "R001", true);

        List<String> result = service.cascadingDisable("R001");

        assertThat(result).containsExactly("R002");
    }

    @Test
    @DisplayName("cascadingDisable - 多层级联禁用（BFS 传播）")
    void cascadingDisableShouldPropagateMultiLevel() {
        // R001 <- R002 <- R003（均为 cascade=true）
        addCascadeDep("R002", "R001", true);
        addCascadeDep("R003", "R002", true);

        List<String> result = service.cascadingDisable("R001");

        assertThat(result).containsExactlyInAnyOrder("R002", "R003");
    }

    @Test
    @DisplayName("cascadingDisable - cascade=false 的依赖不传播")
    void cascadingDisableShouldNotPropagateWhenCascadeFalse() {
        // R001 <- R002(cascade=true) <- R003(cascade=false)
        addCascadeDep("R002", "R001", true);
        addCascadeDep("R003", "R002", false);

        List<String> result = service.cascadingDisable("R001");

        // R003 不应被级联禁用（因为 R002->R003 的 cascade=false）
        assertThat(result).containsExactly("R002");
    }

    @Test
    @DisplayName("cascadingDisable - ruleCode 为空返回空列表")
    void cascadingDisableShouldReturnEmptyWhenBlankCode() {
        assertThat(service.cascadingDisable("")).isEmpty();
        assertThat(service.cascadingDisable(null)).isEmpty();
    }

    @Test
    @DisplayName("cascadingDisable - 无依赖时返回空列表")
    void cascadingDisableShouldReturnEmptyWhenNoDependents() {
        List<String> result = service.cascadingDisable("R999");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("cascadingDisable - 避免重复访问（同一规则只出现一次）")
    void cascadingDisableShouldAvoidDuplicateVisits() {
        // 菱形：R001 <- R002, R001 <- R003, R002 <- R004, R003 <- R004
        // R004 通过两条路径可达，但只应出现一次
        addCascadeDep("R002", "R001", true);
        addCascadeDep("R003", "R001", true);
        addCascadeDep("R004", "R002", true);
        addCascadeDep("R004", "R003", true);

        List<String> result = service.cascadingDisable("R001");

        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrder("R002", "R003", "R004");
        // R004 只出现一次
        assertThat(result.stream().filter("R004"::equals).count()).isEqualTo(1);
    }

    // ============ 辅助方法 ============

    private void addDep(String ruleCode, String dependsOn) {
        RuleDependencyDO dep = new RuleDependencyDO();
        dep.setId((long) (db.size() + 1));
        dep.setRuleCode(ruleCode);
        dep.setDependsOnRuleCode(dependsOn);
        dep.setDependencyType("EXECUTE");
        dep.setCascadeOnDisable(false);
        db.add(dep);
    }

    private void addCascadeDep(String ruleCode, String dependsOn, boolean cascade) {
        RuleDependencyDO dep = new RuleDependencyDO();
        dep.setId((long) (db.size() + 1));
        dep.setRuleCode(ruleCode);
        dep.setDependsOnRuleCode(dependsOn);
        dep.setDependencyType("EXECUTE");
        dep.setCascadeOnDisable(cascade);
        db.add(dep);
    }
}

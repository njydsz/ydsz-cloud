package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.InstanceMigrationDTO;
import com.njydsz.pmis.workflow.dto.InstanceMigrationResultDTO;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowInstanceMigrationServiceImpl 单元测试
 *
 * <p>P3-3：覆盖实例迁移主流程与新增的"任务级迁移"能力。验证：
 * <ul>
 *   <li>实例迁移成功后同步更新 pmis_flow_task 的 definitionId / nodeCode / nodeName</li>
 *   <li>任务节点 == 实例旧节点 → 跟随实例迁移到新节点</li>
 *   <li>任务节点 != 实例节点时按 nodeMapping 映射</li>
 *   <li>任务节点直接存在于目标定义时保持不变</li>
 *   <li>任务节点无法映射时跳过（不调用 updateById）</li>
 *   <li>dryRun / preview 不修改任何数据</li>
 *   <li>无 PENDING 任务时不触发任务更新</li>
 *   <li>实例节点在新定义不存在且无映射 → SKIPPED</li>
 *   <li>源定义不存在 / flowCode 不一致 → 抛 BizException</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class FlowInstanceMigrationServiceImplTest {

    private static final Long SOURCE_DEF_ID = 100L;
    private static final Long TARGET_DEF_ID = 200L;
    private static final Long TENANT_ID = 1L;
    private static final String FLOW_CODE = "LEAVE";

    @Mock
    private FlowInstanceMapper instanceMapper;
    @Mock
    private FlowNodeMapper nodeMapper;
    @Mock
    private FlowDefinitionMapper definitionMapper;
    @Mock
    private FlowTaskMapper flowTaskMapper;

    private FlowInstanceMigrationServiceImpl service;

    @BeforeEach
    void setUp() {
        // 构造器顺序：instanceMapper, nodeMapper, definitionMapper, flowTaskMapper
        service = new FlowInstanceMigrationServiceImpl(
                instanceMapper, nodeMapper, definitionMapper, flowTaskMapper);
    }

    // ============ 任务级迁移场景 ============

    @Test
    @DisplayName("migrate：任务节点 == 实例旧节点时，任务跟随实例迁移到新节点")
    void migrateShouldUpdateTaskFollowingInstanceWhenNodeEquals() {
        // 目标定义节点：apply / approve（实例旧节点 approve 在目标定义存在）
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "approve");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        FlowTaskDO task = buildTask(5001L, 1001L, SOURCE_DEF_ID, "approve");
        when(flowTaskMapper.selectPendingByInstance(1001L)).thenReturn(List.of(task));

        InstanceMigrationDTO dto = buildDto(null); // 无节点映射
        InstanceMigrationResultDTO result = service.migrate(dto);

        assertThat(result.getMigratedCount()).isEqualTo(1);
        // 实例更新
        verify(instanceMapper).updateById(any(FlowInstanceDO.class));
        // 任务更新：definitionId 改为 200，nodeCode 保持 approve
        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(flowTaskMapper).updateById(taskCaptor.capture());
        FlowTaskDO updated = taskCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(5001L);
        assertThat(updated.getDefinitionId()).isEqualTo(TARGET_DEF_ID);
        assertThat(updated.getNodeCode()).isEqualTo("approve");
        assertThat(updated.getNodeName()).isEqualTo("approve-node");
    }

    @Test
    @DisplayName("migrate：实例节点重命名时（apply→submit），任务跟随实例迁移到新节点名")
    void migrateShouldUpdateTaskFollowingInstanceWhenNodeRenamed() {
        // 目标定义只有 submit / approve，apply 已重命名为 submit
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "submit", "approve");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "apply");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        FlowTaskDO task = buildTask(5001L, 1001L, SOURCE_DEF_ID, "apply");
        when(flowTaskMapper.selectPendingByInstance(1001L)).thenReturn(List.of(task));

        // 节点映射 apply → submit
        InstanceMigrationDTO dto = buildDto(Map.of("apply", "submit"));
        InstanceMigrationResultDTO result = service.migrate(dto);

        assertThat(result.getMigratedCount()).isEqualTo(1);
        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(flowTaskMapper).updateById(taskCaptor.capture());
        FlowTaskDO updated = taskCaptor.getValue();
        // 任务节点 == 实例旧节点 apply → 跟随实例迁移到 submit
        assertThat(updated.getNodeCode()).isEqualTo("submit");
        assertThat(updated.getDefinitionId()).isEqualTo(TARGET_DEF_ID);
        assertThat(updated.getNodeName()).isEqualTo("submit-node");
    }

    @Test
    @DisplayName("migrate：任务节点 != 实例节点时，按 nodeMapping 映射任务节点")
    void migrateShouldMapTaskViaNodeMappingWhenNodeDiffers() {
        // 实例在 approve 节点；任务在 counter_sign 节点（会签场景）
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve", "cs_new");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "approve");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        FlowTaskDO task = buildTask(5001L, 1001L, SOURCE_DEF_ID, "counter_sign");
        when(flowTaskMapper.selectPendingByInstance(1001L)).thenReturn(List.of(task));

        // 节点映射：counter_sign → cs_new
        InstanceMigrationDTO dto = buildDto(Map.of("counter_sign", "cs_new"));
        service.migrate(dto);

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(flowTaskMapper).updateById(taskCaptor.capture());
        FlowTaskDO updated = taskCaptor.getValue();
        assertThat(updated.getNodeCode()).isEqualTo("cs_new");
        assertThat(updated.getDefinitionId()).isEqualTo(TARGET_DEF_ID);
    }

    @Test
    @DisplayName("migrate：任务节点 != 实例节点且无映射，但节点直接存在于目标定义时，保持节点不变")
    void migrateShouldKeepTaskNodeWhenExistsInTarget() {
        // 实例在 apply；任务在 notify 节点（目标定义仍存在 notify）
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve", "notify");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "apply");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        FlowTaskDO task = buildTask(5001L, 1001L, SOURCE_DEF_ID, "notify");
        when(flowTaskMapper.selectPendingByInstance(1001L)).thenReturn(List.of(task));

        // 无节点映射
        InstanceMigrationDTO dto = buildDto(null);
        service.migrate(dto);

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(flowTaskMapper).updateById(taskCaptor.capture());
        FlowTaskDO updated = taskCaptor.getValue();
        // 节点保持 notify，仅 definitionId 变更
        assertThat(updated.getNodeCode()).isEqualTo("notify");
        assertThat(updated.getDefinitionId()).isEqualTo(TARGET_DEF_ID);
    }

    @Test
    @DisplayName("migrate：任务节点无法映射时，跳过该任务（不调用 updateById）")
    void migrateShouldSkipTaskWhenNodeUnresolvable() {
        // 实例在 apply；任务在 old_review 节点（目标定义已删除）
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "apply");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        FlowTaskDO task = buildTask(5001L, 1001L, SOURCE_DEF_ID, "old_review");
        when(flowTaskMapper.selectPendingByInstance(1001L)).thenReturn(List.of(task));

        InstanceMigrationDTO dto = buildDto(null);
        service.migrate(dto);

        // 实例更新仍发生
        verify(instanceMapper).updateById(any(FlowInstanceDO.class));
        // 任务未被更新
        verify(flowTaskMapper, never()).updateById(any(FlowTaskDO.class));
    }

    @Test
    @DisplayName("migrate：无 PENDING 任务时不调用 flowTaskMapper.updateById")
    void migrateShouldNotUpdateTasksWhenNoPending() {
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "apply");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(flowTaskMapper.selectPendingByInstance(1001L)).thenReturn(Collections.emptyList());

        InstanceMigrationDTO dto = buildDto(null);
        service.migrate(dto);

        verify(flowTaskMapper, never()).updateById(any(FlowTaskDO.class));
    }

    @Test
    @DisplayName("migrate：dryRun=true 时既不更新实例也不更新任务")
    void migrateShouldNotUpdateAnythingWhenDryRun() {
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "apply");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        InstanceMigrationDTO dto = buildDto(null);
        dto.setDryRun(Boolean.TRUE);
        InstanceMigrationResultDTO result = service.migrate(dto);

        assertThat(result.getMigratedCount()).isEqualTo(1);
        assertThat(result.getDetails().get(0).getStatus()).isEqualTo("MIGRATED");
        assertThat(result.getDetails().get(0).getReason()).contains("试运行");
        verify(instanceMapper, never()).updateById(any(FlowInstanceDO.class));
        verify(flowTaskMapper, never()).selectPendingByInstance(any());
        verify(flowTaskMapper, never()).updateById(any(FlowTaskDO.class));
    }

    @Test
    @DisplayName("previewMigration：不修改任何数据")
    void previewMigrationShouldNotUpdateAnything() {
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "apply");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        InstanceMigrationDTO dto = buildDto(null);
        InstanceMigrationResultDTO result = service.previewMigration(dto);

        assertThat(result.getMigratedCount()).isEqualTo(1);
        verify(instanceMapper, never()).updateById(any(FlowInstanceDO.class));
        verify(flowTaskMapper, never()).selectPendingByInstance(any());
        verify(flowTaskMapper, never()).updateById(any(FlowTaskDO.class));
    }

    @Test
    @DisplayName("migrate：实例当前节点在新定义不存在且无映射 → SKIPPED")
    void migrateShouldSkipInstanceWhenNodeNotInTargetAndNoMapping() {
        // 目标定义没有 old_node 节点
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO instance = buildInstance(1001L, SOURCE_DEF_ID, "old_node");

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        InstanceMigrationDTO dto = buildDto(null);
        InstanceMigrationResultDTO result = service.migrate(dto);

        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getDetails().get(0).getStatus()).isEqualTo("SKIPPED");
        verify(instanceMapper, never()).updateById(any(FlowInstanceDO.class));
        verify(flowTaskMapper, never()).selectPendingByInstance(any());
    }

    @Test
    @DisplayName("migrate：多实例 + 多任务，仅成功的实例触发任务级迁移")
    void migrateShouldHandleMultipleInstancesAndTasks() {
        List<FlowNodeDO> targetNodes = buildNodes(TARGET_DEF_ID, "apply", "approve");
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, FLOW_CODE, "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, FLOW_CODE, "2");
        FlowInstanceDO inst1 = buildInstance(1001L, SOURCE_DEF_ID, "apply");
        FlowInstanceDO inst2 = buildInstance(1002L, SOURCE_DEF_ID, "approve");
        FlowInstanceDO inst3 = buildInstance(1003L, SOURCE_DEF_ID, "old_node"); // 会被跳过

        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);
        when(nodeMapper.selectByDefinitionId(TARGET_DEF_ID)).thenReturn(targetNodes);
        when(instanceMapper.selectList(any())).thenReturn(List.of(inst1, inst2, inst3));

        FlowTaskDO task1 = buildTask(5001L, 1001L, SOURCE_DEF_ID, "apply");
        FlowTaskDO task2 = buildTask(5002L, 1002L, SOURCE_DEF_ID, "approve");
        when(flowTaskMapper.selectPendingByInstance(1001L)).thenReturn(List.of(task1));
        when(flowTaskMapper.selectPendingByInstance(1002L)).thenReturn(List.of(task2));

        InstanceMigrationDTO dto = buildDto(null);
        InstanceMigrationResultDTO result = service.migrate(dto);

        assertThat(result.getTotalInstances()).isEqualTo(3);
        assertThat(result.getMigratedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(instanceMapper, times(2)).updateById(any(FlowInstanceDO.class));
        verify(flowTaskMapper, times(2)).updateById(any(FlowTaskDO.class));
        // 跳过的实例不查询任务
        verify(flowTaskMapper, never()).selectPendingByInstance(eq(1003L));
    }

    // ============ 异常场景 ============

    @Test
    @DisplayName("migrate：源定义不存在 → 抛 BizException")
    void migrateShouldThrowWhenSourceDefNotFound() {
        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(null);

        InstanceMigrationDTO dto = buildDto(null);
        assertThatThrownBy(() -> service.migrate(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("源流程定义不存在");
    }

    @Test
    @DisplayName("migrate：源/目标 flowCode 不一致 → 抛 BizException")
    void migrateShouldThrowWhenFlowCodeMismatch() {
        FlowDefinitionDO sourceDef = buildDef(SOURCE_DEF_ID, "LEAVE", "1");
        FlowDefinitionDO targetDef = buildDef(TARGET_DEF_ID, "EXPENSE", "2");
        when(definitionMapper.selectById(SOURCE_DEF_ID)).thenReturn(sourceDef);
        when(definitionMapper.selectById(TARGET_DEF_ID)).thenReturn(targetDef);

        InstanceMigrationDTO dto = buildDto(null);
        assertThatThrownBy(() -> service.migrate(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("flowCode 不一致");
    }

    @Test
    @DisplayName("migrate：源定义与目标定义相同 → 抛 BizException")
    void migrateShouldThrowWhenSourceEqualsTarget() {
        InstanceMigrationDTO dto = buildDto(null);
        dto.setSourceDefinitionId(SOURCE_DEF_ID);
        dto.setTargetDefinitionId(SOURCE_DEF_ID);
        assertThatThrownBy(() -> service.migrate(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能相同");
    }

    @Test
    @DisplayName("migrate：sourceDefinitionId 为空 → 抛 BizException")
    void migrateShouldThrowWhenSourceDefIdNull() {
        InstanceMigrationDTO dto = new InstanceMigrationDTO();
        dto.setTargetDefinitionId(TARGET_DEF_ID);
        dto.setTenantId(TENANT_ID);
        assertThatThrownBy(() -> service.migrate(dto))
                .isInstanceOf(BizException.class);
    }

    // ============ 工具方法 ============

    private InstanceMigrationDTO buildDto(Map<String, String> nodeMapping) {
        InstanceMigrationDTO dto = new InstanceMigrationDTO();
        dto.setSourceDefinitionId(SOURCE_DEF_ID);
        dto.setTargetDefinitionId(TARGET_DEF_ID);
        dto.setTenantId(TENANT_ID);
        dto.setNodeMapping(nodeMapping);
        return dto;
    }

    private FlowDefinitionDO buildDef(Long id, String flowCode, String version) {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(id);
        def.setFlowCode(flowCode);
        def.setFlowVersion(version);
        return def;
    }

    private FlowInstanceDO buildInstance(Long id, Long defId, String currentNodeCode) {
        FlowInstanceDO inst = new FlowInstanceDO();
        inst.setId(id);
        inst.setDefinitionId(defId);
        inst.setFlowCode(FLOW_CODE);
        inst.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        inst.setCurrentNodeCode(currentNodeCode);
        inst.setTenantId(TENANT_ID);
        inst.setTitle("实例-" + id);
        return inst;
    }

    private FlowTaskDO buildTask(Long id, Long instanceId, Long defId, String nodeCode) {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(id);
        task.setInstanceId(instanceId);
        task.setDefinitionId(defId);
        task.setNodeCode(nodeCode);
        task.setNodeName(nodeCode + "-old");
        task.setTaskStatus("PENDING");
        return task;
    }

    private List<FlowNodeDO> buildNodes(Long defId, String... nodeCodes) {
        List<FlowNodeDO> nodes = new ArrayList<>();
        for (String code : nodeCodes) {
            FlowNodeDO node = new FlowNodeDO();
            node.setDefinitionId(defId);
            node.setNodeCode(code);
            node.setNodeName(code + "-node");
            nodes.add(node);
        }
        return nodes;
    }
}

package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.WorkflowNodeConfigDO;
import com.njydsz.pmis.workflow.mapper.WorkflowNodeConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkflowNodeConfigServiceImpl 单元测试
 */
@DisplayName("WorkflowNodeConfigServiceImpl 测试")
class WorkflowNodeConfigServiceImplTest {

    private WorkflowNodeConfigMapper mapper;
    private WorkflowNodeConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(WorkflowNodeConfigMapper.class);
        service = new WorkflowNodeConfigServiceImpl(mapper);
    }

    @Test
    @DisplayName("saveOrUpdate 缺 processKey 应抛 BAD_REQUEST")
    void bad() {
        WorkflowNodeConfigDO c = new WorkflowNodeConfigDO();
        assertThatThrownBy(() -> service.saveOrUpdate(c))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("saveOrUpdate 新增应设置默认 tenantId")
    void add() {
        when(mapper.selectByNode("p", "n", 1L)).thenReturn(null);
        when(mapper.insert(any(WorkflowNodeConfigDO.class))).thenAnswer(inv -> {
            ((WorkflowNodeConfigDO) inv.getArgument(0)).setId(7L);
            return 1;
        });
        WorkflowNodeConfigDO c = new WorkflowNodeConfigDO();
        c.setProcessKey("p");
        c.setNodeId("n");
        c.setAssigneeType("USER");
        c.setAssigneeValue("1");
        Long id = service.saveOrUpdate(c);
        assertThat(id).isEqualTo(7L);
        assertThat(c.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("saveOrUpdate 重复节点应抛 DUPLICATE_KEY")
    void duplicate() {
        when(mapper.selectByNode("p", "n", 1L)).thenReturn(new WorkflowNodeConfigDO());
        WorkflowNodeConfigDO c = new WorkflowNodeConfigDO();
        c.setProcessKey("p");
        c.setNodeId("n");
        c.setAssigneeType("USER");
        assertThatThrownBy(() -> service.saveOrUpdate(c))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }
}

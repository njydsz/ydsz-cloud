package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.flow.entity.FlowDelegateLogDO;
import com.njydsz.pmis.workflow.flow.mapper.FlowDelegateAuthMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowDelegateLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowDelegateAuthServiceImpl 单元测试
 *
 * <p>P1-4: 长期授权委派服务测试。
 * <p>覆盖：创建/校验/撤回/启停/匹配/扫描过期/日志分页。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@DisplayName("FlowDelegateAuthServiceImpl 单元测试")
class FlowDelegateAuthServiceImplTest {

    private FlowDelegateAuthMapper authMapper;
    private FlowDelegateLogMapper logMapper;
    private FlowDelegateAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        authMapper = mock(FlowDelegateAuthMapper.class);
        logMapper = mock(FlowDelegateLogMapper.class);
        // 模拟 MyBatis-Plus insert 后自动回填 id
        doAnswer(invocation -> {
            FlowDelegateAuthDO auth = invocation.getArgument(0);
            if (auth.getId() == null) {
                auth.setId(1L);
            }
            return 1;
        }).when(authMapper).insert(any(FlowDelegateAuthDO.class));
        service = new FlowDelegateAuthServiceImpl(authMapper, logMapper);
    }

    // ============== create ==============

    @Test
    @DisplayName("create: 必填校验 - auth 为 null")
    void testCreateNull() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("create: 必填校验 - 授权人为空")
    void testCreateOwnerMissing() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setOwnerUserId(null);
        assertThatThrownBy(() -> service.create(auth))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("授权人");
    }

    @Test
    @DisplayName("create: 必填校验 - 被授权人为空")
    void testCreateDelegateMissing() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setDelegateUserId(null);
        assertThatThrownBy(() -> service.create(auth))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("被授权人");
    }

    @Test
    @DisplayName("create: 不能自授权")
    void testCreateSelfDelegate() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setOwnerUserId(1001L);
        auth.setDelegateUserId(1001L);
        assertThatThrownBy(() -> service.create(auth))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能是自己");
    }

    @Test
    @DisplayName("create: 结束时间必须晚于开始时间")
    void testCreateTimeOrder() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setStartTime(LocalDateTime.of(2026, 7, 10, 0, 0));
        auth.setEndTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThatThrownBy(() -> service.create(auth))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("结束时间必须晚于开始时间");
    }

    @Test
    @DisplayName("create: FLOW 模式必填 flowCode")
    void testCreateFlowScopeMissingCode() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setScopeType("FLOW");
        auth.setFlowCode(null);
        assertThatThrownBy(() -> service.create(auth))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("flowCode");
    }

    @Test
    @DisplayName("create: FLOW_NODE 模式必填 flowCode 和 nodeCode")
    void testCreateFlowNodeScopeMissing() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setScopeType("FLOW_NODE");
        auth.setFlowCode("f1");
        auth.setNodeCode(null);
        assertThatThrownBy(() -> service.create(auth))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("nodeCode");
    }

    @Test
    @DisplayName("create: ROLE 模式必填 roleCode")
    void testCreateRoleScopeMissingCode() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setScopeType("ROLE");
        auth.setRoleCode(null);
        assertThatThrownBy(() -> service.create(auth))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("roleCode");
    }

    @Test
    @DisplayName("create: 不支持的 scopeType")
    void testCreateUnknownScope() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setScopeType("UNKNOWN");
        assertThatThrownBy(() -> service.create(auth))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的 scopeType");
    }

    @Test
    @DisplayName("create: ALL 模式成功 + 写入默认值")
    void testCreateAllScopeSuccess() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setScopeType("ALL");
        auth.setTenantId(null); // 测试默认值
        auth.setAuthStatus(null); // 测试默认值

        Long id = service.create(auth);

        assertThat(id).isNotNull();
        ArgumentCaptor<FlowDelegateAuthDO> captor = ArgumentCaptor.forClass(FlowDelegateAuthDO.class);
        verify(authMapper, times(1)).insert(captor.capture());
        FlowDelegateAuthDO inserted = captor.getValue();
        assertThat(inserted.getTenantId()).isNotNull();
        assertThat(inserted.getAuthStatus()).isEqualTo("ENABLED");
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(inserted.getUpdatedAt()).isNotNull();
        assertThat(inserted.getProviderTraceId()).isNotNull();
    }

    @Test
    @DisplayName("create: FLOW 模式成功")
    void testCreateFlowScopeSuccess() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setScopeType("FLOW");
        auth.setFlowCode("project_initiation");
        auth.setNodeCode(null);

        Long id = service.create(auth);

        assertThat(id).isNotNull();
        verify(authMapper, times(1)).insert((FlowDelegateAuthDO) any());
    }

    // ============== revoke ==============

    @Test
    @DisplayName("revoke: authId 为空")
    void testRevokeNullId() {
        assertThatThrownBy(() -> service.revoke(null, 1001L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("revoke: 记录不存在")
    void testRevokeNotFound() {
        when(authMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.revoke(99L, 1001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("revoke: 仅 owner 可撤回")
    void testRevokeForbidden() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setId(1L);
        auth.setOwnerUserId(1001L);
        when(authMapper.selectById(1L)).thenReturn(auth);

        assertThatThrownBy(() -> service.revoke(1L, 9999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅授权人本人");
    }

    @Test
    @DisplayName("revoke: 成功")
    void testRevokeSuccess() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setId(1L);
        auth.setOwnerUserId(1001L);
        when(authMapper.selectById(1L)).thenReturn(auth);
        when(authMapper.updateStatus(eq(1L), eq("REVOKED"), any())).thenReturn(1);

        service.revoke(1L, 1001L);

        verify(authMapper, times(1)).updateStatus(eq(1L), eq("REVOKED"), any());
    }

    // ============== updateStatus ==============

    @Test
    @DisplayName("updateStatus: 不支持的状态")
    void testUpdateStatusInvalid() {
        assertThatThrownBy(() -> service.updateStatus(1L, "INVALID", 1001L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("updateStatus: 记录不存在")
    void testUpdateStatusNotFound() {
        when(authMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateStatus(99L, "ENABLED", 1001L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("updateStatus: 仅 owner 可改")
    void testUpdateStatusForbidden() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setId(1L);
        auth.setOwnerUserId(1001L);
        when(authMapper.selectById(1L)).thenReturn(auth);

        assertThatThrownBy(() -> service.updateStatus(1L, "ENABLED", 9999L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("updateStatus: 成功")
    void testUpdateStatusSuccess() {
        FlowDelegateAuthDO auth = baseAuth();
        auth.setId(1L);
        auth.setOwnerUserId(1001L);
        when(authMapper.selectById(1L)).thenReturn(auth);
        when(authMapper.updateStatus(eq(1L), eq("DISABLED"), any())).thenReturn(1);

        service.updateStatus(1L, "DISABLED", 1001L);

        verify(authMapper, times(1)).updateStatus(eq(1L), eq("DISABLED"), any());
    }

    // ============== listMine / listAsDelegate ==============

    @Test
    @DisplayName("listMine: ownerUserId 为空返回空列表")
    void testListMineNullOwner() {
        assertThat(service.listMine(null, 1L, null)).isEmpty();
        verify(authMapper, never()).selectByOwner(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("listMine: 正常查询")
    void testListMineNormal() {
        FlowDelegateAuthDO auth = baseAuth();
        when(authMapper.selectByOwner(eq(1L), eq(1001L), eq(null)))
                .thenReturn(List.of(auth));
        List<FlowDelegateAuthDO> list = service.listMine(1001L, 1L, null);
        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("listAsDelegate: 正常查询")
    void testListAsDelegateNormal() {
        FlowDelegateAuthDO auth = baseAuth();
        when(authMapper.selectByDelegate(eq(1L), eq(2002L), eq("ENABLED")))
                .thenReturn(List.of(auth));
        List<FlowDelegateAuthDO> list = service.listAsDelegate(2002L, 1L, "ENABLED");
        assertThat(list).hasSize(1);
    }

    // ============== matchAuth ==============

    @Test
    @DisplayName("matchAuth: tenant 或 owner 为空返回 null")
    void testMatchAuthNullParams() {
        assertThat(service.matchAuth(null, 1001L, "f1", "t1")).isNull();
        assertThat(service.matchAuth(1L, null, "f1", "t1")).isNull();
        verify(authMapper, never()).matchAuth(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("matchAuth: 异常不抛 — 返回 null")
    void testMatchAuthException() {
        when(authMapper.matchAuth(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));
        assertThat(service.matchAuth(1L, 1001L, "f1", "t1")).isNull();
    }

    @Test
    @DisplayName("matchAuth: 正常匹配返回")
    void testMatchAuthSuccess() {
        FlowDelegateAuthDO auth = baseAuth();
        when(authMapper.matchAuth(eq(1L), eq(1001L), eq("f1"), eq("t1"), any()))
                .thenReturn(auth);
        FlowDelegateAuthDO result = service.matchAuth(1L, 1001L, "f1", "t1");
        assertThat(result).isSameAs(auth);
    }

    // ============== scanAndMarkExpired ==============

    @Test
    @DisplayName("scanAndMarkExpired: 异常不抛 — 返回 0")
    void testScanException() {
        when(authMapper.markExpired(any(), any()))
                .thenThrow(new RuntimeException("DB error"));
        assertThat(service.scanAndMarkExpired()).isEqualTo(0);
    }

    @Test
    @DisplayName("scanAndMarkExpired: 正常返回条数")
    void testScanNormal() {
        when(authMapper.markExpired(any(), any())).thenReturn(5);
        assertThat(service.scanAndMarkExpired()).isEqualTo(5);
    }

    // ============== 日志分页 ==============

    @Test
    @DisplayName("listDelegateLog: delegateUserId 为空返回空分页")
    void testListDelegateLogNullUser() {
        assertThat(service.listDelegateLog(null, 1, 10).getList()).isEmpty();
    }

    @Test
    @DisplayName("listDelegateLog: 正常返回")
    void testListDelegateLogNormal() {
        FlowDelegateLogDO log = new FlowDelegateLogDO();
        log.setId(1L);
        log.setTaskId(10L);
        when(logMapper.selectByDelegateUser(eq(2002L), eq(0), eq(20)))
                .thenReturn(List.of(log));
        var page = service.listDelegateLog(2002L, 1, 20);
        assertThat(page.getList()).hasSize(1);
    }

    @Test
    @DisplayName("listOwnerLog: ownerUserId 为空返回空分页")
    void testListOwnerLogNullUser() {
        assertThat(service.listOwnerLog(null, 1, 10).getList()).isEmpty();
    }

    @Test
    @DisplayName("listOwnerLog: 页码下限保护")
    void testListOwnerLogPageGuard() {
        when(logMapper.selectByOwnerUser(eq(1001L), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());
        var page = service.listOwnerLog(1001L, -1, 20);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
    }

    // ============== 工具 ==============

    private FlowDelegateAuthDO baseAuth() {
        FlowDelegateAuthDO auth = new FlowDelegateAuthDO();
        auth.setOwnerUserId(1001L);
        auth.setOwnerUserName("张三");
        auth.setDelegateUserId(2002L);
        auth.setDelegateUserName("李四");
        auth.setStartTime(LocalDateTime.of(2026, 7, 2, 0, 0));
        auth.setEndTime(LocalDateTime.of(2026, 7, 9, 23, 59));
        auth.setTenantId(1L);
        return auth;
    }
}

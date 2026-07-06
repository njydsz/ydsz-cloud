package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.EmbeddedApprovalActionDTO;
import com.njydsz.pmis.workflow.entity.FlowThirdPartyAccountDO;
import com.njydsz.pmis.workflow.entity.FlowThirdPartyLogDO;
import com.njydsz.pmis.workflow.service.FlowEmbeddedApprovalService;
import com.njydsz.pmis.workflow.service.FlowThirdPartyAccountService;
import com.njydsz.pmis.workflow.service.FlowThirdPartyLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowThirdPartyApprovalController 单元测试
 *
 * <p>P0-2: 三方审批回调驱动工作流的容错与调用链测试。
 *
 * <p>测试策略：
 * <ul>
 *   <li>通过反射调用 private 方法 {@code handleCallback} / {@code dispatchApprovalAction}，
 *       绕过 webhook 端点的签名校验（签名校验由独立的 *SignatureUtilTest 覆盖）</li>
 *   <li>验证 {@code dispatchApprovalAction} 在各种 BizException 场景下的容错行为
 *       （找不到任务 / 流程已结束 / 不支持的事件类型）</li>
 *   <li>验证回调日志 PENDING → SUCCESS/FAIL 状态流转</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("三方审批回调驱动工作流")
class FlowThirdPartyApprovalControllerTest {

    @Mock private FlowThirdPartyAccountService thirdPartyAccountService;
    @Mock private FlowEmbeddedApprovalService embeddedApprovalService;
    @Mock private FlowThirdPartyLogService thirdPartyLogService;

    @InjectMocks private FlowThirdPartyApprovalController controller;

    private static final Long LOG_ID = 9001L;
    private static final Long USER_ID = 500L;
    private static final Long TENANT_ID = 1L;
    private static final String OPEN_ID = "open-abc-123";
    private static final String BUSINESS_TYPE = "PROJECT";
    private static final String BUSINESS_ID = "PRJ-2024-001";

    // ==================== handleCallback 整体流程 ====================

    @Test
    @DisplayName("handleCallback - 成功路径：落库 PENDING → quickAction → 落库 SUCCESS → 返回 ok")
    void handleCallbackSuccessPath() throws Exception {
        Map<String, Object> body = buildDingTalkBody("AGREE");
        FlowThirdPartyAccountDO account = buildAccount();
        when(thirdPartyLogService.savePending(any(FlowThirdPartyLogDO.class))).thenReturn(LOG_ID);
        when(thirdPartyAccountService.getByOpenId("DINGTALK", OPEN_ID)).thenReturn(account);

        Map<String, Object> result = invokeHandleCallback("DINGTALK", body);

        assertThat(result).containsEntry("success", true);

        // 验证 PENDING 日志已落库
        verify(thirdPartyLogService).savePending(any(FlowThirdPartyLogDO.class));
        // 验证 quickAction 被调用，且参数正确
        ArgumentCaptor<EmbeddedApprovalActionDTO> dtoCaptor = ArgumentCaptor.forClass(EmbeddedApprovalActionDTO.class);
        verify(embeddedApprovalService).quickAction(dtoCaptor.capture());
        EmbeddedApprovalActionDTO dto = dtoCaptor.getValue();
        assertThat(dto.getBusinessType()).isEqualTo(BUSINESS_TYPE);
        assertThat(dto.getBusinessId()).isEqualTo(BUSINESS_ID);
        assertThat(dto.getAction()).isEqualTo("PASS");
        assertThat(dto.getUserId()).isEqualTo(USER_ID);
        assertThat(dto.getTenantId()).isEqualTo(TENANT_ID);
        // 验证日志状态更新为 SUCCESS
        verify(thirdPartyLogService).updateSuccess(LOG_ID);
        verify(thirdPartyLogService, never()).updateFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("handleCallback - account 未找到：落库 PENDING → updateFailed → 返回 fail")
    void handleCallbackAccountNotFound() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "bpmsTaskChange");
        body.put("openId", OPEN_ID);
        when(thirdPartyLogService.savePending(any(FlowThirdPartyLogDO.class))).thenReturn(LOG_ID);
        when(thirdPartyAccountService.getByOpenId("DINGTALK", OPEN_ID)).thenReturn(null);

        Map<String, Object> result = invokeHandleCallback("DINGTALK", body);

        assertThat(result).containsEntry("success", false);
        verify(thirdPartyLogService).updateFailed(eq(LOG_ID), eq("account not mapped"));
        verify(embeddedApprovalService, never()).quickAction(any());
        verify(thirdPartyLogService, never()).updateSuccess(anyLong());
    }

    @Test
    @DisplayName("handleCallback - quickAction 抛系统异常：落库 FAIL → 返回 fail")
    void handleCallbackSystemExceptionShouldUpdateFailed() throws Exception {
        Map<String, Object> body = buildDingTalkBody("AGREE");
        FlowThirdPartyAccountDO account = buildAccount();
        when(thirdPartyLogService.savePending(any(FlowThirdPartyLogDO.class))).thenReturn(LOG_ID);
        when(thirdPartyAccountService.getByOpenId("DINGTALK", OPEN_ID)).thenReturn(account);
        // 模拟系统异常（非 BizException）— 应抛出，由 handleCallback 捕获
        doThrow(new RuntimeException("DB connection lost"))
                .when(embeddedApprovalService).quickAction(any());

        Map<String, Object> result = invokeHandleCallback("DINGTALK", body);

        assertThat(result).containsEntry("success", false);
        verify(thirdPartyLogService).updateFailed(eq(LOG_ID), eq("DB connection lost"));
        verify(thirdPartyLogService, never()).updateSuccess(anyLong());
    }

    // ==================== dispatchApprovalAction 容错 ====================

    @Test
    @DisplayName("dispatchApprovalAction - 钉钉 AGREE → 调用 quickAction(PASS)")
    void dispatchDingTalkAgreeShouldCallQuickActionWithPass() throws Exception {
        Map<String, Object> body = buildDingTalkBody("AGREE");
        FlowThirdPartyAccountDO account = buildAccount();

        invokeDispatch("DINGTALK", "bpmsTaskChange", account, body);

        ArgumentCaptor<EmbeddedApprovalActionDTO> dtoCaptor = ArgumentCaptor.forClass(EmbeddedApprovalActionDTO.class);
        verify(embeddedApprovalService).quickAction(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getAction()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("dispatchApprovalAction - 钉钉 REFUSE → 调用 quickAction(REJECT)")
    void dispatchDingTalkRefuseShouldCallQuickActionWithReject() throws Exception {
        Map<String, Object> body = buildDingTalkBody("REFUSE");
        FlowThirdPartyAccountDO account = buildAccount();

        invokeDispatch("DINGTALK", "bpmsTaskChange", account, body);

        ArgumentCaptor<EmbeddedApprovalActionDTO> dtoCaptor = ArgumentCaptor.forClass(EmbeddedApprovalActionDTO.class);
        verify(embeddedApprovalService).quickAction(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getAction()).isEqualTo("REJECT");
    }

    @Test
    @DisplayName("dispatchApprovalAction - 飞书 approval.canceled → 调用 quickAction(WITHDRAW)")
    void dispatchFeishuCanceledShouldCallQuickActionWithWithdraw() throws Exception {
        Map<String, Object> body = buildFeishuBody();
        FlowThirdPartyAccountDO account = buildAccount();

        invokeDispatch("FEISHU", "approval.canceled", account, body);

        ArgumentCaptor<EmbeddedApprovalActionDTO> dtoCaptor = ArgumentCaptor.forClass(EmbeddedApprovalActionDTO.class);
        verify(embeddedApprovalService).quickAction(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getAction()).isEqualTo("WITHDRAW");
    }

    @Test
    @DisplayName("dispatchApprovalAction - 企微 status=1 → 调用 quickAction(PASS)")
    void dispatchWeComStatus1ShouldCallQuickActionWithPass() throws Exception {
        Map<String, Object> body = buildWeComBody("1");
        FlowThirdPartyAccountDO account = buildAccount();

        invokeDispatch("WECOM", "sys_approval_change", account, body);

        ArgumentCaptor<EmbeddedApprovalActionDTO> dtoCaptor = ArgumentCaptor.forClass(EmbeddedApprovalActionDTO.class);
        verify(embeddedApprovalService).quickAction(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getAction()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("dispatchApprovalAction - 不支持的事件类型 → 不调用 quickAction，不抛异常")
    void dispatchUnsupportedEventShouldNotCallQuickAction() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("businessType", BUSINESS_TYPE);
        body.put("businessId", BUSINESS_ID);
        FlowThirdPartyAccountDO account = buildAccount();

        invokeDispatch("DINGTALK", "bpmsInstanceChange", account, body);

        verify(embeddedApprovalService, never()).quickAction(any());
    }

    @Test
    @DisplayName("dispatchApprovalAction - 缺少 businessType → 不调用 quickAction，不抛异常")
    void dispatchMissingBusinessTypeShouldNotCallQuickAction() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("taskAction", "AGREE");
        // 缺少 businessType/businessId
        FlowThirdPartyAccountDO account = buildAccount();

        invokeDispatch("DINGTALK", "bpmsTaskChange", account, body);

        verify(embeddedApprovalService, never()).quickAction(any());
    }

    @Test
    @DisplayName("dispatchApprovalAction - 找不到任务（FORBIDDEN）→ 容错跳过，不抛异常")
    void dispatchForbiddenShouldBeTolerated() throws Exception {
        Map<String, Object> body = buildDingTalkBody("AGREE");
        FlowThirdPartyAccountDO account = buildAccount();
        doThrow(new BizException(BizErrorCode.FORBIDDEN, "error.workflow.msg_1440b2f2"))
                .when(embeddedApprovalService).quickAction(any());

        // 不应抛出异常
        invokeDispatch("DINGTALK", "bpmsTaskChange", account, body);

        verify(embeddedApprovalService).quickAction(any());
    }

    @Test
    @DisplayName("dispatchApprovalAction - 流程已结束（BIZ_ERROR）→ 容错跳过，不抛异常")
    void dispatchBizErrorShouldBeTolerated() throws Exception {
        Map<String, Object> body = buildDingTalkBody("AGREE");
        FlowThirdPartyAccountDO account = buildAccount();
        doThrow(new BizException(BizErrorCode.BIZ_ERROR, "error.workflow.msg_8243ec9a"))
                .when(embeddedApprovalService).quickAction(any());

        // 不应抛出异常
        invokeDispatch("DINGTALK", "bpmsTaskChange", account, body);

        verify(embeddedApprovalService).quickAction(any());
    }

    @Test
    @DisplayName("dispatchApprovalAction - 实例不存在（NOT_FOUND）→ 容错跳过，不抛异常")
    void dispatchNotFoundShouldBeTolerated() throws Exception {
        Map<String, Object> body = buildDingTalkBody("AGREE");
        FlowThirdPartyAccountDO account = buildAccount();
        doThrow(new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_b72e8598"))
                .when(embeddedApprovalService).quickAction(any());

        // 不应抛出异常
        invokeDispatch("DINGTALK", "bpmsTaskChange", account, body);

        verify(embeddedApprovalService).quickAction(any());
    }

    @Test
    @DisplayName("dispatchApprovalAction - 系统异常（非 BizException）→ 抛出，不吞掉")
    void dispatchSystemExceptionShouldPropagate() throws Exception {
        Map<String, Object> body = buildDingTalkBody("AGREE");
        FlowThirdPartyAccountDO account = buildAccount();
        doThrow(new RuntimeException("DB error"))
                .when(embeddedApprovalService).quickAction(any());

        // 系统异常应抛出（不被容错吞掉），invokeDispatch 已解包为 RuntimeException
        try {
            invokeDispatch("DINGTALK", "bpmsTaskChange", account, body);
            org.assertj.core.api.Assertions.fail("应抛出 RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("DB error");
        }
    }

    @Test
    @DisplayName("dispatchApprovalAction - tenantId 通过 account 传入")
    void dispatchShouldPassTenantIdFromAccount() throws Exception {
        Map<String, Object> body = buildFeishuBody();
        FlowThirdPartyAccountDO account = buildAccount();

        invokeDispatch("FEISHU", "approval.approved", account, body);

        ArgumentCaptor<EmbeddedApprovalActionDTO> dtoCaptor = ArgumentCaptor.forClass(EmbeddedApprovalActionDTO.class);
        verify(embeddedApprovalService).quickAction(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    }

    // ==================== 辅助方法 ====================

    private FlowThirdPartyAccountDO buildAccount() {
        FlowThirdPartyAccountDO account = new FlowThirdPartyAccountDO();
        account.setId(1L);
        account.setUserId(USER_ID);
        account.setPlatform("DINGTALK");
        account.setOpenId(OPEN_ID);
        account.setTenantId(TENANT_ID);
        return account;
    }

    private Map<String, Object> buildDingTalkBody(String taskAction) {
        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "bpmsTaskChange");
        body.put("taskAction", taskAction);
        body.put("businessType", BUSINESS_TYPE);
        body.put("businessId", BUSINESS_ID);
        body.put("openId", OPEN_ID);
        body.put("comment", "通过");
        return body;
    }

    private Map<String, Object> buildFeishuBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "approval.approved");
        body.put("businessType", BUSINESS_TYPE);
        body.put("businessId", BUSINESS_ID);
        body.put("openId", OPEN_ID);
        return body;
    }

    private Map<String, Object> buildWeComBody(String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "sys_approval_change");
        body.put("status", status);
        body.put("businessType", BUSINESS_TYPE);
        body.put("businessId", BUSINESS_ID);
        body.put("openId", OPEN_ID);
        return body;
    }

    /**
     * 反射调用 private handleCallback
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeHandleCallback(String platform, Map<String, Object> body) throws Exception {
        Method method = FlowThirdPartyApprovalController.class.getDeclaredMethod(
                "handleCallback", String.class, Map.class, String.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(controller, platform, body, null);
    }

    /**
     * 反射调用 private dispatchApprovalAction
     */
    private void invokeDispatch(String platform, String eventType,
                                FlowThirdPartyAccountDO account, Map<String, Object> body) throws Exception {
        Method method = FlowThirdPartyApprovalController.class.getDeclaredMethod(
                "dispatchApprovalAction", String.class, String.class,
                FlowThirdPartyAccountDO.class, Map.class);
        method.setAccessible(true);
        try {
            method.invoke(controller, platform, eventType, account, body);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 解包反射异常，让真实异常透传给测试断言
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }
}

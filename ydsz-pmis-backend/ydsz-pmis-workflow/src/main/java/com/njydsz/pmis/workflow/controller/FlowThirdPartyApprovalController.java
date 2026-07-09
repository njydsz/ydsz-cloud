package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.EmbeddedApprovalActionDTO;
import com.njydsz.pmis.workflow.entity.FlowThirdPartyAccountDO;
import com.njydsz.pmis.workflow.entity.FlowThirdPartyLogDO;
import com.njydsz.pmis.workflow.enums.ThirdPartyPlatform;
import com.njydsz.pmis.workflow.service.FlowEmbeddedApprovalService;
import com.njydsz.pmis.workflow.service.FlowThirdPartyAccountService;
import com.njydsz.pmis.workflow.service.FlowThirdPartyLogService;
import com.njydsz.pmis.workflow.thirdparty.DingTalkSignatureUtil;
import com.njydsz.pmis.workflow.thirdparty.FeishuSignatureUtil;
import com.njydsz.pmis.workflow.thirdparty.ThirdPartyApprovalActionResolver;
import com.njydsz.pmis.workflow.thirdparty.WeComSignatureUtil;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三方审批回调 Controller
 *
 * <p>P0-2: 三方审批 SDK — 钉钉/飞书/企微审批回调入口。
 *
 * <p>说明：
 * <ul>
 *   <li>三个 webhook 端点接收三方系统回调，均为免认证（需在安全配置中放行）</li>
 *   <li>每个端点先验证签名，再记录回调日志，最后驱动工作流（通过/驳回等）</li>
 *   <li>签名密钥/Token 通过配置注入，避免硬编码</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Tag(name = "三方审批回调")
@RestController
@RequestMapping("/workflow/third-party")
@RequiredArgsConstructor
@Validated
public class FlowThirdPartyApprovalController {

    /** 三方账号映射服务，负责 openId → 系统用户 ID 的反查 */
    private final FlowThirdPartyAccountService thirdPartyAccountService;
    /** 嵌入式审批服务（驱动工作流通过/驳回/撤回） */
    private final FlowEmbeddedApprovalService embeddedApprovalService;
    /** 三方审批回调日志服务（PENDING → SUCCESS/FAIL 状态流转） */
    private final FlowThirdPartyLogService thirdPartyLogService;

    /** 钉钉应用 appSecret（签名校验密钥） */
    @Value("${pmis.workflow.third-party.dingtalk.app-secret:}")
    private String dingTalkAppSecret;

    /** 飞书应用 appSecret（签名校验密钥） */
    @Value("${pmis.workflow.third-party.feishu.app-secret:}")
    private String feishuAppSecret;

    /** 企微回调 Token（签名校验密钥） */
    @Value("${pmis.workflow.third-party.wecom.token:}")
    private String weComToken;

    /**
     * 钉钉审批回调
     *
     * <p>签名相关参数通过请求头传入，加密载荷在 body 的 encrypt 字段。
     *
     * @param timestamp 时间戳（请求头）
     * @param nonce     随机串（请求头）
     * @param signature 签名（请求头 sign）
     * @param body      回调 JSON
     * @return 处理结果
     */
    @Operation(summary = "钉钉审批回调")
    @Idempotent(key = "flow-third-party-approval:ding-talk-callback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/dingtalk/callback")
    public Map<String, Object> dingTalkCallback(
            @RequestHeader(value = "timestamp", required = false) String timestamp,
            @RequestHeader(value = "nonce", required = false) String nonce,
            @RequestHeader(value = "sign", required = false) String signature,
            @RequestBody Map<String, Object> body) {
        String platform = ThirdPartyPlatform.DINGTALK.name();
        String encrypt = extractEncrypt(body);
        if (!DingTalkSignatureUtil.verifySignature(timestamp, nonce, encrypt, signature, dingTalkAppSecret)) {
            log.warn("[ThirdPartyCallback] 钉钉签名校验失败: timestamp={} nonce={}", timestamp, nonce);
            return fail("signature verify failed");
        }
        return handleCallback(platform, body, encrypt);
    }

    /**
     * 飞书审批回调
     *
     * @param timestamp 时间戳（请求头）
     * @param nonce     随机串（请求头）
     * @param signature 签名（请求头 X-Lark-Signature）
     * @param body      回调 JSON
     * @return 处理结果
     */
    @Operation(summary = "飞书审批回调")
    @Idempotent(key = "flow-third-party-approval:feishu-callback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/feishu/callback")
    public Map<String, Object> feishuCallback(
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature,
            @RequestBody Map<String, Object> body) {
        String platform = ThirdPartyPlatform.FEISHU.name();
        String encrypt = extractEncrypt(body);
        if (!FeishuSignatureUtil.verifySignature(timestamp, nonce, encrypt, signature, feishuAppSecret)) {
            log.warn("[ThirdPartyCallback] 飞书签名校验失败: timestamp={} nonce={}", timestamp, nonce);
            return fail("signature verify failed");
        }
        return handleCallback(platform, body, encrypt);
    }

    /**
     * 企微审批回调
     *
     * @param msgSignature 签名（查询参数 msg_signature）
     * @param timestamp    时间戳（查询参数 timestamp）
     * @param nonce        随机串（查询参数 nonce）
     * @param body         回调 JSON
     * @return 处理结果
     */
    @Operation(summary = "企业微信审批回调")
    @Idempotent(key = "flow-third-party-approval:we-com-callback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/wecom/callback")
    public Map<String, Object> weComCallback(
            @RequestParam(value = "msg_signature", required = false) String msgSignature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestBody Map<String, Object> body) {
        String platform = ThirdPartyPlatform.WECOM.name();
        String encrypt = extractEncrypt(body);
        if (!WeComSignatureUtil.verifySignature(weComToken, timestamp, nonce, encrypt, msgSignature)) {
            log.warn("[ThirdPartyCallback] 企微签名校验失败: timestamp={} nonce={}", timestamp, nonce);
            return fail("signature verify failed");
        }
        return handleCallback(platform, body, encrypt);
    }

    // ============================== 内部方法 ==============================

    /**
     * 统一回调处理：记录日志 + 反查系统用户 + 驱动工作流
     *
     * @param platform 平台
     * @param body     回调原始数据
     * @param encrypt  加密载荷（用于日志/排障）
     */
    private Map<String, Object> handleCallback(String platform, Map<String, Object> body, String encrypt) {
        String eventType = mapStr(body, "eventType");
        String processInstanceId = mapStr(body, "processInstanceId");
        String businessType = mapStr(body, "businessType");
        String businessId = mapStr(body, "businessId");
        String openId = mapStr(body, "openId");

        log.info("[ThirdPartyCallback] 收到回调: platform={} eventType={} processInstanceId={} businessType={} businessId={} openId={}",
                platform, eventType, processInstanceId, businessType, businessId, openId);

        // 1. 回调原始数据落库（handle_status=PENDING），由独立重试任务保证最终一致
        String logId = savePendingLog(platform, eventType, processInstanceId, businessType, businessId, body);

        // 2. 通过 openId 反查系统用户
        FlowThirdPartyAccountDO account = null;
        if (openId != null) {
            account = thirdPartyAccountService.getByOpenId(platform, openId);
        }
        if (account == null) {
            log.warn("[ThirdPartyCallback] 未找到账号映射: platform={} openId={}", platform, openId);
            thirdPartyLogService.updateFailed(logId, "account not mapped");
            return fail("account not mapped");
        }

        // 3. 驱动工作流（通过/驳回等）
        try {
            dispatchApprovalAction(platform, eventType, account, body);
            log.info("[ThirdPartyCallback] 回调处理成功: platform={} userId={} eventType={}",
                    platform, account.getUserId(), eventType);
            thirdPartyLogService.updateSuccess(logId);
            return ok();
        } catch (Exception e) {
            log.error("[ThirdPartyCallback] 回调处理失败: platform={} userId={} eventType={} err={}",
                    platform, account.getUserId(), eventType, e.getMessage(), e);
            thirdPartyLogService.updateFailed(logId, e.getMessage());
            return fail(e.getMessage());
        }
    }

    /**
     * 驱动工作流操作
     *
     * <p>根据三方事件类型映射为工作流动作（通过/驳回/撤回等），
     * 调用 {@link FlowEmbeddedApprovalService#quickAction} 执行。
     *
     * <p>容错策略：
     * <ul>
     *   <li>不支持的事件类型 → log.warn 跳过，不抛异常（三方会回调各种事件）</li>
     *   <li>缺少 businessType/businessId → log.warn 跳过，不抛异常</li>
     *   <li>找不到任务 / 流程已结束 / 实例不存在 → log.warn 跳过，不抛异常
     *       （三方可能重复回调、可能延迟回调已处理的任务）</li>
     *   <li>其他系统异常（数据库/网络） → 抛出，由 {@link #handleCallback} 捕获并返回 fail</li>
     * </ul>
     *
     * @param platform  平台
     * @param eventType 三方事件类型
     * @param account   账号映射（含系统用户 ID）
     * @param body      回调原始数据
     */
    private void dispatchApprovalAction(String platform, String eventType,
                                        FlowThirdPartyAccountDO account, Map<String, Object> body) {
        // 1. 解析三方事件 → 工作流动作
        ThirdPartyApprovalActionResolver.FlowAction action =
                ThirdPartyApprovalActionResolver.resolve(platform, eventType, body);
        if (action == null) {
            log.warn("[ThirdPartyCallback] 不支持的事件类型，跳过: platform={} eventType={}",
                    platform, eventType);
            return;
        }

        // 2. 读取业务类型/业务 ID（quickAction 通过 businessType+businessId 定位流程实例）
        String businessType = mapStr(body, "businessType");
        String businessId = mapStr(body, "businessId");
        if (businessType == null || businessType.isBlank()
                || businessId == null || businessId.isBlank()) {
            log.warn("[ThirdPartyCallback] 回调缺少 businessType/businessId，跳过: platform={} eventType={} action={}",
                    platform, eventType, action);
            return;
        }

        // 3. 构造 EmbeddedApprovalActionDTO 并调用 quickAction
        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType(businessType);
        dto.setBusinessId(businessId);
        dto.setAction(action.code());
        dto.setUserId(account.getUserId());
        dto.setComment(mapStr(body, "comment"));
        // webhook 免认证无 SecurityContext，显式传入租户 ID
        if (account.getTenantId() != null) {
            dto.setTenantId(account.getTenantId());
        }

        try {
            embeddedApprovalService.quickAction(dto);
            log.info("[ThirdPartyCallback] 派发审批动作成功: platform={} userId={} action={} businessType={} businessId={}",
                    platform, account.getUserId(), action, businessType, businessId);
        } catch (BizException e) {
            // 业务异常（找不到任务/流程已结束/参数错误等）— 三方可能回调已处理的任务或延迟回调，log.warn 不抛
            log.warn("[ThirdPartyCallback] 回调容错跳过: platform={} userId={} action={} code={} msg={}",
                    platform, account.getUserId(), action, e.getCode(), e.getMessage());
        }
    }

    /**
     * 保存 PENDING 状态的回调日志
     *
     * @return 日志 ID，落库失败返回 null
     */
    private String savePendingLog(String platform, String eventType, String processInstanceId,
                                String businessType, String businessId, Map<String, Object> body) {
        FlowThirdPartyLogDO logEntry = new FlowThirdPartyLogDO();
        logEntry.setPlatform(platform);
        logEntry.setEventType(eventType);
        logEntry.setProcessInstanceId(processInstanceId);
        logEntry.setBusinessType(businessType);
        logEntry.setBusinessId(businessId);
        logEntry.setCallbackData(body == null ? null : body.toString());
        logEntry.setTenantId("1"); // 默认租户，多租户场景由 account.tenantId 兜底
        return thirdPartyLogService.savePending(logEntry);
    }

    /**
     * 从回调 body 中提取加密载荷。
     *
     * @param body 回调原始数据
     * @return 加密载荷字符串，无则返回 null
     */
    private String extractEncrypt(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object enc = body.get("encrypt");
        if (enc == null) {
            enc = body.get("encrypted");
        }
        return enc == null ? null : enc.toString();
    }

    /**
     * 从 Map 中安全提取字符串值。
     *
     * @param body 数据源
     * @param key  键名
     * @return 字符串值，null 或不存在时返回 null
     */
    private String mapStr(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * 构造成功响应 Map。
     *
     * @return 包含 success=true 的 Map
     */
    private Map<String, Object> ok() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        return r;
    }

    /**
     * 构造失败响应 Map。
     *
     * @param msg 错误信息
     * @return 包含 success=false 与 errorMsg 的 Map
     */
    private Map<String, Object> fail(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("errorMsg", msg);
        return r;
    }
}

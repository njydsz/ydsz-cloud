package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.workflow.entity.FlowThirdPartyAccountDO;
import com.njydsz.pmis.workflow.enums.ThirdPartyPlatform;
import com.njydsz.pmis.workflow.service.FlowThirdPartyAccountService;
import com.njydsz.pmis.workflow.thirdparty.DingTalkSignatureUtil;
import com.njydsz.pmis.workflow.thirdparty.FeishuSignatureUtil;
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
@RequestMapping("/api/v1/workflow/third-party")
@RequiredArgsConstructor
@Validated
public class FlowThirdPartyApprovalController {

    private final FlowThirdPartyAccountService thirdPartyAccountService;

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

        // P2 待实现：将回调原始数据落库到 pmis_flow_third_party_log（handle_status=PENDING），由独立重试任务保证最终一致

        // 通过 openId 反查系统用户
        FlowThirdPartyAccountDO account = null;
        if (openId != null) {
            account = thirdPartyAccountService.getByOpenId(platform, openId);
        }
        if (account == null) {
            log.warn("[ThirdPartyCallback] 未找到账号映射: platform={} openId={}", platform, openId);
            return fail("account not mapped");
        }

        // 驱动工作流（通过/驳回等）
        try {
            dispatchApprovalAction(platform, eventType, account, body);
            log.info("[ThirdPartyCallback] 回调处理成功: platform={} userId={} eventType={}",
                    platform, account.getUserId(), eventType);
            return ok();
        } catch (Exception e) {
            log.error("[ThirdPartyCallback] 回调处理失败: platform={} userId={} eventType={} err={}",
                    platform, account.getUserId(), eventType, e.getMessage(), e);
            return fail(e.getMessage());
        }
    }

    /**
     * 驱动工作流操作
     *
     * <p>根据三方事件类型映射为工作流动作（通过/驳回/撤回等），
     * 调用嵌入式审批服务或工作流引擎执行。
     * P2 待对接：FlowEmbeddedApprovalService.quickAction，需根据三方回调字段映射 EmbeddedApprovalActionDTO
     *
     * @param platform       平台
     * @param eventType      三方事件类型
     * @param account        账号映射（含系统用户 ID）
     * @param body           回调原始数据
     */
    private void dispatchApprovalAction(String platform, String eventType,
                                        FlowThirdPartyAccountDO account, Map<String, Object> body) {
        // P2 待实现：按 platform + eventType 映射工作流动作：
        //   钉钉: bpmsTaskChange / bpmsInstanceChange
        //   飞书: approval.approved / approval.rejected / approval.canceled
        //   企微: sys_approval_change
        // 调用 FlowEmbeddedApprovalService.quickAction(actionDTO) 完成通过/驳回
        log.info("[ThirdPartyCallback] 派发审批动作: platform={} userId={} eventType={}（待对接工作流引擎）",
                platform, account.getUserId(), eventType);
    }

    /**
     * 从回调 body 中提取加密载荷
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

    private String mapStr(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private Map<String, Object> ok() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        return r;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("errorMsg", msg);
        return r;
    }
}

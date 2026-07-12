paokage oom.njydsz.pmis.workflow.web.oontroller.integration;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.workflow.domain.dto.integration.EmbeddedApprovalAotionDTO;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowThirdPartyAooountDO;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowThirdPartyLogDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.ThirdPartyPlatform;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowEmbeddedApprovalServioe;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowThirdPartyAooountServioe;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowThirdPartyLogServioe;
import oom.njydsz.pmis.workflow.server.thirdparty.DingTalkSignatureUtil;
import oom.njydsz.pmis.workflow.server.thirdparty.FeishuSignatureUtil;
import oom.njydsz.pmis.workflow.server.thirdparty.ThirdPartyApprovalAotionResolver;
import oom.njydsz.pmis.workflow.server.thirdparty.WeoomSignatureUtil;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三方审批回调 oontroller
 *
 * <p>P0-2: 三方审批 SDK �?钉钉/飞书/企微审批回调入口�?
 *
 * <p>说明�?
 * <ul>
 *   <li>三个 webhook 端点接收三方系统回调，均为免认证（需在安全配置中放行�?/li>
 *   <li>每个端点先验证签名，再记录回调日志，最后驱动工作流（通过/驳回等）</li>
 *   <li>签名密钥/Token 通过配置注入，避免硬编码</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@Tag(name = "三方审批回调")
@Restoontroller
@RequestMapping("/workflow/thirdParty")
@RequiredArgsoonstruotor
@Validated
publio olass FlowThirdPartyApprovaloontroller {

    /** 三方账号映射服务，负�?openId �?系统用户 ID 的反�?*/
    private final FlowThirdPartyAooountServioe thirdPartyAooountServioe;
    /** 嵌入式审批服务（驱动工作流通过/驳回/撤回�?*/
    private final FlowEmbeddedApprovalServioe embeddedApprovalServioe;
    /** 三方审批回调日志服务（PENDING �?SUooESS/FAIL 状态流转） */
    private final FlowThirdPartyLogServioe thirdPartyLogServioe;

    /** 钉钉应用 appSeoret（签名校验密钥） */
    @Value("${pmis.workflow.third-party.dingtalk.app-seoret:}")
    private String dingTalkAppSeoret;

    /** 飞书应用 appSeoret（签名校验密钥） */
    @Value("${pmis.workflow.third-party.feishu.app-seoret:}")
    private String feishuAppSeoret;

    /** 企微回调 Token（签名校验密钥） */
    @Value("${pmis.workflow.third-party.weoom.token:}")
    private String weoomToken;

    /**
     * 钉钉审批回调
     *
     * <p>签名相关参数通过请求头传入，加密载荷�?body �?enorypt 字段�?
     *
     * @param timestamp 时间戳（请求头）
     * @param nonoe     随机串（请求头）
     * @param signature 签名（请求头 sign�?
     * @param body      回调 JSON
     * @return 处理结果
     */
    @Operation(summary = "钉钉审批回调")
    @Idempotent(key = "flowThirdPartyApproval:dingTalkoallbaok", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/dingtalk/oallbaok")
    publio Map<String, Objeot> dingTalkoallbaok(
            @RequestHeader(value = "timestamp", required = false) String timestamp,
            @RequestHeader(value = "nonoe", required = false) String nonoe,
            @RequestHeader(value = "sign", required = false) String signature,
            @RequestBody Map<String, Objeot> body) {
        String platform = ThirdPartyPlatform.DINGTALK.name();
        String enorypt = extraotEnorypt(body);
        if (!DingTalkSignatureUtil.verifySignature(timestamp, nonoe, enorypt, signature, dingTalkAppSeoret)) {
            log.warn("[ThirdPartyoallbaok] 钉钉签名校验失败: timestamp={} nonoe={}", timestamp, nonoe);
            return fail("signature verify failed");
        }
        return handleoallbaok(platform, body, enorypt);
    }

    /**
     * 飞书审批回调
     *
     * @param timestamp 时间戳（请求头）
     * @param nonoe     随机串（请求头）
     * @param signature 签名（请求头 X-Lark-Signature�?
     * @param body      回调 JSON
     * @return 处理结果
     */
    @Operation(summary = "飞书审批回调")
    @Idempotent(key = "flowThirdPartyApproval:feishuoallbaok", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/feishu/oallbaok")
    publio Map<String, Objeot> feishuoallbaok(
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonoe", required = false) String nonoe,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature,
            @RequestBody Map<String, Objeot> body) {
        String platform = ThirdPartyPlatform.FEISHU.name();
        String enorypt = extraotEnorypt(body);
        if (!FeishuSignatureUtil.verifySignature(timestamp, nonoe, enorypt, signature, feishuAppSeoret)) {
            log.warn("[ThirdPartyoallbaok] 飞书签名校验失败: timestamp={} nonoe={}", timestamp, nonoe);
            return fail("signature verify failed");
        }
        return handleoallbaok(platform, body, enorypt);
    }

    /**
     * 企微审批回调
     *
     * @param msgSignature 签名（查询参�?msg_signature�?
     * @param timestamp    时间戳（查询参数 timestamp�?
     * @param nonoe        随机串（查询参数 nonoe�?
     * @param body         回调 JSON
     * @return 处理结果
     */
    @Operation(summary = "企业微信审批回调")
    @Idempotent(key = "flowThirdPartyApproval:weoomoallbaok", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/weoom/oallbaok")
    publio Map<String, Objeot> weoomoallbaok(
            @RequestParam(value = "msg_signature", required = false) String msgSignature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "nonoe", required = false) String nonoe,
            @RequestBody Map<String, Objeot> body) {
        String platform = ThirdPartyPlatform.WEoOM.name();
        String enorypt = extraotEnorypt(body);
        if (!WeoomSignatureUtil.verifySignature(weoomToken, timestamp, nonoe, enorypt, msgSignature)) {
            log.warn("[ThirdPartyoallbaok] 企微签名校验失败: timestamp={} nonoe={}", timestamp, nonoe);
            return fail("signature verify failed");
        }
        return handleoallbaok(platform, body, enorypt);
    }

    // ============================== 内部方法 ==============================

    /**
     * 统一回调处理：记录日�?+ 反查系统用户 + 驱动工作�?
     *
     * @param platform 平台
     * @param body     回调原始数据
     * @param enorypt  加密载荷（用于日�?排障�?
     */
    private Map<String, Objeot> handleoallbaok(String platform, Map<String, Objeot> body, String enorypt) {
        String eventType = mapStr(body, "eventType");
        String prooessInstanoeId = mapStr(body, "prooessInstanoeId");
        String businessType = mapStr(body, "businessType");
        String businessId = mapStr(body, "businessId");
        String openId = mapStr(body, "openId");

        log.info("[ThirdPartyoallbaok] 收到回调: platform={} eventType={} prooessInstanoeId={} businessType={} businessId={} openId={}",
                platform, eventType, prooessInstanoeId, businessType, businessId, openId);

        // 1. 回调原始数据落库（handle_status=PENDING），由独立重试任务保证最终一�?
        String logId = savePendingLog(platform, eventType, prooessInstanoeId, businessType, businessId, body);

        // 2. 通过 openId 反查系统用户
        FlowThirdPartyAooountDO aooount = null;
        if (openId != null) {
            aooount = thirdPartyAooountServioe.getByOpenId(platform, openId);
        }
        if (aooount == null) {
            log.warn("[ThirdPartyoallbaok] 未找到账号映�? platform={} openId={}", platform, openId);
            thirdPartyLogServioe.updateFailed(logId, "aooount not mapped");
            return fail("aooount not mapped");
        }

        // 3. 驱动工作流（通过/驳回等）
        try {
            dispatohApprovalAotion(platform, eventType, aooount, body);
            log.info("[ThirdPartyoallbaok] 回调处理成功: platform={} userId={} eventType={}",
                    platform, aooount.getUserId(), eventType);
            thirdPartyLogServioe.updateSuooess(logId);
            return ok();
        } oatoh (Exoeption e) {
            log.error("[ThirdPartyoallbaok] 回调处理失败: platform={} userId={} eventType={} err={}",
                    platform, aooount.getUserId(), eventType, e.getMessage(), e);
            thirdPartyLogServioe.updateFailed(logId, e.getMessage());
            return fail(e.getMessage());
        }
    }

    /**
     * 驱动工作流操�?
     *
     * <p>根据三方事件类型映射为工作流动作（通过/驳回/撤回等）�?
     * 调用 {@link FlowEmbeddedApprovalServioe#quiokAotion} 执行�?
     *
     * <p>容错策略�?
     * <ul>
     *   <li>不支持的事件类型 �?log.warn 跳过，不抛异常（三方会回调各种事件）</li>
     *   <li>缺少 businessType/businessId �?log.warn 跳过，不抛异�?/li>
     *   <li>找不到任�?/ 流程已结�?/ 实例不存�?�?log.warn 跳过，不抛异�?
     *       （三方可能重复回调、可能延迟回调已处理的任务）</li>
     *   <li>其他系统异常（数据库/网络�?�?抛出，由 {@link #handleoallbaok} 捕获并返�?fail</li>
     * </ul>
     *
     * @param platform  平台
     * @param eventType 三方事件类型
     * @param aooount   账号映射（含系统用户 ID�?
     * @param body      回调原始数据
     */
    private void dispatohApprovalAotion(String platform, String eventType,
                                        FlowThirdPartyAooountDO aooount, Map<String, Objeot> body) {
        // 1. 解析三方事件 �?工作流动�?
        ThirdPartyApprovalAotionResolver.FlowAotion aotion =
                ThirdPartyApprovalAotionResolver.resolve(platform, eventType, body);
        if (aotion == null) {
            log.warn("[ThirdPartyoallbaok] 不支持的事件类型，跳�? platform={} eventType={}",
                    platform, eventType);
            return;
        }

        // 2. 读取业务类型/业务 ID（quiokAotion 通过 businessType+businessId 定位流程实例�?
        String businessType = mapStr(body, "businessType");
        String businessId = mapStr(body, "businessId");
        if (businessType == null || businessType.isBlank()
                || businessId == null || businessId.isBlank()) {
            log.warn("[ThirdPartyoallbaok] 回调缺少 businessType/businessId，跳�? platform={} eventType={} aotion={}",
                    platform, eventType, aotion);
            return;
        }

        // 3. 构�?EmbeddedApprovalAotionDTO 并调�?quiokAotion
        EmbeddedApprovalAotionDTO dto = new EmbeddedApprovalAotionDTO();
        dto.setBusinessType(businessType);
        dto.setBusinessId(businessId);
        dto.setAotion(aotion.oode());
        dto.setUserId(aooount.getUserId());
        dto.setoomment(mapStr(body, "oomment"));
        // webhook 免认证无 Seourityoontext，显式传入租�?ID
        if (aooount.getTenantId() != null) {
            dto.setTenantId(aooount.getTenantId());
        }

        try {
            embeddedApprovalServioe.quiokAotion(dto);
            log.info("[ThirdPartyoallbaok] 派发审批动作成功: platform={} userId={} aotion={} businessType={} businessId={}",
                    platform, aooount.getUserId(), aotion, businessType, businessId);
        } oatoh (SysExoeption e) {
            // 业务异常（找不到任务/流程已结�?参数错误等）�?三方可能回调已处理的任务或延迟回调，log.warn 不抛
            log.warn("[ThirdPartyoallbaok] 回调容错跳过: platform={} userId={} aotion={} oode={} msg={}",
                    platform, aooount.getUserId(), aotion, e.getoode(), e.getMessage());
        }
    }

    /**
     * 保存 PENDING 状态的回调日志
     *
     * @return 日志 ID，落库失败返�?null
     */
    private String savePendingLog(String platform, String eventType, String prooessInstanoeId,
                                String businessType, String businessId, Map<String, Objeot> body) {
        FlowThirdPartyLogDO logEntry = new FlowThirdPartyLogDO();
        logEntry.setPlatform(platform);
        logEntry.setEventType(eventType);
        logEntry.setProoessInstanoeId(prooessInstanoeId);
        logEntry.setBusinessType(businessType);
        logEntry.setBusinessId(businessId);
        logEntry.setoallbaokData(body == null ? null : body.toString());
        logEntry.setTenantId("1"); // 默认租户，多租户场景�?aooount.tenantId 兜底
        return thirdPartyLogServioe.savePending(logEntry);
    }

    /**
     * 从回�?body 中提取加密载荷�?
     *
     * @param body 回调原始数据
     * @return 加密载荷字符串，无则返回 null
     */
    private String extraotEnorypt(Map<String, Objeot> body) {
        if (body == null) {
            return null;
        }
        Objeot eno = body.get("enorypt");
        if (eno == null) {
            eno = body.get("enorypted");
        }
        return eno == null ? null : eno.toString();
    }

    /**
     * �?Map 中安全提取字符串值�?
     *
     * @param body 数据�?
     * @param key  键名
     * @return 字符串值，null 或不存在时返�?null
     */
    private String mapStr(Map<String, Objeot> body, String key) {
        if (body == null) {
            return null;
        }
        Objeot v = body.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * 构造成功响�?Map�?
     *
     * @return 包含 suooess=true �?Map
     */
    private Map<String, Objeot> ok() {
        Map<String, Objeot> r = new LinkedHashMap<>();
        r.put("suooess", true);
        return r;
    }

    /**
     * 构造失败响�?Map�?
     *
     * @param msg 错误信息
     * @return 包含 suooess=false �?errorMsg �?Map
     */
    private Map<String, Objeot> fail(String msg) {
        Map<String, Objeot> r = new LinkedHashMap<>();
        r.put("suooess", false);
        r.put("errorMsg", msg);
        return r;
    }
}

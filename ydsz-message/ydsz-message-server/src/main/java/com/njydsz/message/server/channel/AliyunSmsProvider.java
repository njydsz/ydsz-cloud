package com.njydsz.message.server.channel.sms;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.server.config.MessageProperties;

/**
 * 阿里云短信服务商实现。
 *
 * <p>通过阿里云 SMS Common RPC API（{@code SendSms}）发送短信，签名使用
 * {@link AliyunSmsSigner} 自实现 HmacSHA1，零外部 SDK 依赖。
 *
 * <p>仅当 {@code ydsz.message.sms.provider=aliyun} 时装配；凭证缺失时返回 fail
 * （由 {@link com.njydsz.message.server.channel.impl.SmsChannel} 自动降级到 Mock）。
 *
 * <p>参数来源：
 * <ul>
 *   <li>PhoneNumbers = {@code request.getReceiver()}</li>
 *   <li>SignName = {@code template.signName}（回退配置默认签名）</li>
 *   <li>TemplateCode = {@code template.providerKey}（阿里云侧模板 ID）</li>
 *   <li>TemplateParam = {@code request.getParams()} 的 JSON</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.message.sms", name = "provider", havingValue = "aliyun")
public class AliyunSmsProvider implements SmsProvider {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final MessageProperties.AliyunSmsConfig config;
    private final RestTemplate restTemplate;

    /**
     * 生产构造：从 {@link MessageProperties} 读取阿里云配置并构建 RestTemplate。
     *
     * @param messageProperties 消息配置
     */
    public AliyunSmsProvider(MessageProperties messageProperties) {
        this.config = messageProperties.getSms().getAliyun();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeout());
        factory.setReadTimeout(config.getReadTimeout());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 测试构造：注入自定义 config 与 RestTemplate（便于 mock）。
     *
     * @param config       阿里云配置
     * @param restTemplate RestTemplate（测试可 mock）
     */
    AliyunSmsProvider(MessageProperties.AliyunSmsConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    @Override
    public String providerType() {
        return "aliyun";
    }

    @Override
    public MessageResult send(MessageRequest request, MsgTemplate template) {
        String phone = request.getReceiver();
        if (!StringUtils.hasText(phone)) {
            return MessageResult.fail("SMS", "手机号不能为空");
        }
        if (!StringUtils.hasText(config.getAccessKeyId())
                || !StringUtils.hasText(config.getAccessKeySecret())) {
            log.warn("[AliyunSms] 凭证未配置,发送失败: phone={}", phone);
            return MessageResult.fail("SMS", "阿里云 SMS 凭证未配置");
        }
        String signName = template != null && StringUtils.hasText(template.getSignName())
                ? template.getSignName() : config.getSignName();
        String templateCode = template != null ? template.getProviderKey() : null;
        if (!StringUtils.hasText(signName) || !StringUtils.hasText(templateCode)) {
            return MessageResult.fail("SMS", "短信签名或模板 Code 缺失");
        }
        try {
            Map<String, String> params = buildCommonParams();
            params.put("PhoneNumbers", phone);
            params.put("SignName", signName);
            params.put("TemplateCode", templateCode);
            params.put("TemplateParam", YdszJson.toJson(request.getParams()));
            String signature = AliyunSmsSigner.sign(params, config.getAccessKeySecret());
            params.put("Signature", signature);
            String url = "https://" + config.getEndpoint() + "/?"
                    + AliyunSmsSigner.buildQuery(params);
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            Map<String, Object> json = YdszJson.parseMap(resp.getBody());
            String code = MapUtils.getString(json, "Code");
            if ("OK".equals(code)) {
                String bizId = MapUtils.getString(json, "BizId");
                log.info("[AliyunSms] 发送成功: phone={} bizId={}", phone, bizId);
                return MessageResult.ok("SMS", "ALIYUN-" + bizId);
            }
            log.warn("[AliyunSms] 发送失败: phone={} code={} msg={}",
                    phone, code, MapUtils.getString(json, "Message"));
            return MessageResult.fail("SMS", code + ": " + MapUtils.getString(json, "Message"));
        } catch (Exception e) {
            log.error("[AliyunSms] 发送异常: phone={} err={}", phone, e.getMessage(), e);
            return MessageResult.fail("SMS", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 构造阿里云 RPC 公共参数。
     *
     * @return 公共参数 Map
     */
    private Map<String, String> buildCommonParams() {
        Map<String, String> p = new HashMap<>();
        p.put("AccessKeyId", config.getAccessKeyId());
        p.put("Action", "SendSms");
        p.put("Format", "JSON");
        p.put("RegionId", "cn-hangzhou");
        p.put("SignatureMethod", "HMAC-SHA1");
        p.put("SignatureNonce", UUID.randomUUID().toString());
        p.put("SignatureVersion", "1.0");
        p.put("Timestamp", LocalDateTime.now(ZoneOffset.UTC).format(ISO_FMT));
        p.put("Version", "2017-05-25");
        return p;
    }

    // ==================== P0-4: 批量发送 + 回执查询 ====================

    /** 阿里云 SendBatchSms 单次最大手机号数 */
    private static final int BATCH_MAX_PHONES = 100;

    @Override
    public List<MessageResult> batchSend(List<MessageRequest> requests, MsgTemplate template) {
        List<MessageResult> results = new ArrayList<>(requests.size());
        // 按 BATCH_MAX_PHONES 分批调用阿里云 SendBatchSms
        for (int i = 0; i < requests.size(); i += BATCH_MAX_PHONES) {
            int end = Math.min(i + BATCH_MAX_PHONES, requests.size());
            List<MessageRequest> chunk = requests.subList(i, end);
            results.addAll(doBatchSend(chunk, template));
        }
        return results;
    }

    /**
     * 调用阿里云 SendBatchSms 接口批量发送。
     *
     * <p>参数构造：PhoneNumberJson = ["phone1","phone2",...]，
     * SignNameJson = ["sign","sign",...]，TemplateParamJson = [{...},{...},...]。
     */
    private List<MessageResult> doBatchSend(List<MessageRequest> requests, MsgTemplate template) {
        List<MessageResult> results = new ArrayList<>(requests.size());
        if (!StringUtils.hasText(config.getAccessKeyId())
                || !StringUtils.hasText(config.getAccessKeySecret())) {
            for (int i = 0; i < requests.size(); i++) {
                results.add(MessageResult.fail("SMS", "阿里云 SMS 凭证未配置"));
            }
            return results;
        }
        String signName = template != null && StringUtils.hasText(template.getSignName())
                ? template.getSignName() : config.getSignName();
        String templateCode = template != null ? template.getProviderKey() : null;
        if (!StringUtils.hasText(signName) || !StringUtils.hasText(templateCode)) {
            for (int i = 0; i < requests.size(); i++) {
                results.add(MessageResult.fail("SMS", "短信签名或模板 Code 缺失"));
            }
            return results;
        }
        try {
            // 构造 JSON 数组参数
            List<String> phones = new ArrayList<>();
            List<String> signNames = new ArrayList<>();
            List<String> templateParams = new ArrayList<>();
            for (MessageRequest req : requests) {
                phones.add(req.getReceiver());
                signNames.add(signName);
                templateParams.add(YdszJson.toJson(req.getParams()));
            }
            Map<String, String> params = buildCommonParams();
            params.put("Action", "SendBatchSms");
            params.put("PhoneNumberJson", YdszJson.toJson(phones));
            params.put("SignNameJson", YdszJson.toJson(signNames));
            params.put("TemplateCode", templateCode);
            params.put("TemplateParamJson", YdszJson.toJson(templateParams));
            String signature = AliyunSmsSigner.sign(params, config.getAccessKeySecret());
            params.put("Signature", signature);
            String url = "https://" + config.getEndpoint() + "/?" + AliyunSmsSigner.buildQuery(params);
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            Map<String, Object> json = YdszJson.parseMap(resp.getBody());
            String code = MapUtils.getString(json, "Code");
            if ("OK".equals(code)) {
                String bizId = MapUtils.getString(json, "BizId");
                log.info("[AliyunSms] 批量发送成功: count={} bizId={}", requests.size(), bizId);
                for (int i = 0; i < requests.size(); i++) {
                    results.add(MessageResult.ok("SMS", "ALIYUN-" + bizId + "-" + i));
                }
            } else {
                log.warn("[AliyunSms] 批量发送失败: code={} msg={}", code, MapUtils.getString(json, "Message"));
                for (int i = 0; i < requests.size(); i++) {
                    results.add(MessageResult.fail("SMS", code + ": " + MapUtils.getString(json, "Message")));
                }
            }
        } catch (Exception e) {
            log.error("[AliyunSms] 批量发送异常: count={} err={}", requests.size(), e.getMessage(), e);
            for (int i = 0; i < requests.size(); i++) {
                results.add(MessageResult.fail("SMS", e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public MessageResult queryReceipt(String providerTraceId, String phone) {
        if (!StringUtils.hasText(providerTraceId) || !StringUtils.hasText(phone)) {
            return MessageResult.fail("SMS", "providerTraceId 或手机号为空");
        }
        if (!StringUtils.hasText(config.getAccessKeyId())
                || !StringUtils.hasText(config.getAccessKeySecret())) {
            return MessageResult.fail("SMS", "阿里云 SMS 凭证未配置");
        }
        // 从 ALIYUN-{bizId}-{idx} 中提取 bizId
        String bizId = providerTraceId;
        if (bizId.startsWith("ALIYUN-")) {
            bizId = bizId.substring(7);
            int dashIdx = bizId.lastIndexOf('-');
            if (dashIdx > 0) {
                bizId = bizId.substring(0, dashIdx);
            }
        }
        try {
            Map<String, String> params = buildCommonParams();
            params.put("Action", "QuerySendDetails");
            params.put("PhoneNumber", phone);
            params.put("BizId", bizId);
            params.put("SendDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            params.put("PageSize", "1");
            params.put("CurrentPage", "1");
            String signature = AliyunSmsSigner.sign(params, config.getAccessKeySecret());
            params.put("Signature", signature);
            String url = "https://" + config.getEndpoint() + "/?" + AliyunSmsSigner.buildQuery(params);
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            Map<String, Object> json = YdszJson.parseMap(resp.getBody());
            String code = MapUtils.getString(json, "Code");
            if ("OK".equals(code)) {
                Map<String, Object> detail = MapUtils.safeCastMap(json.get("SmsSendDetailDTOs"));
                if (detail != null) {
                    List<Map<String, Object>> arr = MapUtils.getListOfMaps(detail, "SmsSendDetailDTO");
                    if (arr != null && !arr.isEmpty()) {
                        Map<String, Object> first = arr.get(0);
                        String sendStatus = MapUtils.getString(first, "SendStatus");
                        String errMsg = MapUtils.getString(first, "ErrCode");
                        if ("DELIVERED".equals(sendStatus)) {
                            return MessageResult.ok("SMS", providerTraceId);
                        } else if ("FAILED".equals(sendStatus)) {
                            MessageResult r = MessageResult.fail("SMS", "发送失败: " + errMsg);
                            r.setProviderTraceId(providerTraceId);
                            return r;
                        }
                    }
                }
                // 未查询到详情,返回 UNKNOWN
                MessageResult r = new MessageResult("SMS", "UNKNOWN", providerTraceId, null);
                return r;
            }
            return MessageResult.fail("SMS", code + ": " + MapUtils.getString(json, "Message"));
        } catch (Exception e) {
            log.error("[AliyunSms] 回执查询异常: bizId={} err={}", bizId, e.getMessage(), e);
            return MessageResult.fail("SMS", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}

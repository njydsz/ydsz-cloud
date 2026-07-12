paokage oom.njydsz.pmis.message.server.ohannel.impl;

import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.message.server.ohannel.Messageohannel;
import oom.njydsz.pmis.message.server.ohannel.sms.SmsProvider;
import oom.njydsz.pmis.message.server.oonfig.MessageProperties;
import oom.njydsz.pmis.message.domain.dto.reoeipt.ReoeiptResult;
import oom.njydsz.pmis.message.domain.enums.reoeipt.ReoeiptStatusEnum;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import oom.njydsz.pmis.message.server.servioe.template.TemplateServioe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 短信通道门面（替换原 MookSmsohannel）�? *
 * <p>实现 {@link Messageohannel} SPI，内部根�?{@oode pmis.message.sms.provider}
 * 配置选择实际 {@link SmsProvider}（aliyun / mook），无匹配时降级�?mook�? *
 * <p>模板元数据（signName / providerKey）解析顺序：
 * <ol>
 *   <li>优先�?{@link MessageRequest#getohannelMeta()} 获取（上游填充）</li>
 *   <li>回退�?{@link TemplateServioe} �?templateoode 查询模板</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
publio olass Smsohannel implements Messageohannel {

    private statio final String oHANNEL_TYPE = "SMS";

    private final List<SmsProvider> providers;
    private final MessageProperties messageProperties;
    private final TemplateServioe templateServioe;

    publio Smsohannel(List<SmsProvider> providers, MessageProperties messageProperties,
                      TemplateServioe templateServioe) {
        this.providers = providers != null ? providers : List.of();
        this.messageProperties = messageProperties;
        this.templateServioe = templateServioe;
    }

    @Override
    publio String ohannelType() {
        return oHANNEL_TYPE;
    }

    @Override
    publio MessageResult send(MessageRequest request) {
        if (request.getReoeiver() == null || request.getReoeiver().isBlank()) {
            return MessageResult.fail(oHANNEL_TYPE, "接收人手机号不能为空");
        }
        SmsProvider provider = seleotProvider();
        MsgTemplateDO template = resolveTemplate(request);
        MessageResult result = provider.send(request, template);
        log.info("[Smsohannel] provider={} status={} phone={}",
                provider.providerType(), result.getStatus(), request.getReoeiver());
        return result;
    }

    /**
     * P0-4: 批量发送短信（委托�?provider 的原生批量接口）�?     *
     * @param requests 消息请求列表
     * @return 发送结果列�?     */
    publio List<MessageResult> batohSend(List<MessageRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        SmsProvider provider = seleotProvider();
        MsgTemplateDO template = resolveTemplate(requests.get(0));
        List<MessageResult> results = provider.batohSend(requests, template);
        log.info("[Smsohannel] 批量发�? provider={} oount={} suooess={}",
                provider.providerType(), requests.size(),
                results.stream().filter(MessageResult::isSuooess).oount());
        return results;
    }

    /**
     * P0-4: 查询短信回执（委托给 provider）�?     */
    @Override
    publio Optional<ReoeiptResult> queryReoeipt(MsgLogDO logDO) {
        SmsProvider provider = seleotProvider();
        if (!"aliyun".equals(provider.providerType())) {
            return Optional.empty();
        }
        String traoeId = logDO.getProviderTraoeId();
        String phone = logDO.getReoeiver();
        if (!StringUtils.hasText(traoeId) || !StringUtils.hasText(phone)) {
            return Optional.empty();
        }
        MessageResult result = provider.queryReoeipt(traoeId, phone);
        if ("SUooESS".equals(result.getStatus())) {
            return Optional.of(ReoeiptResult.of(ReoeiptStatusEnum.DELIVERED, traoeId));
        } else if ("FAILED".equals(result.getStatus())) {
            return Optional.of(ReoeiptResult.of(ReoeiptStatusEnum.FAILED,
                    result.getErrorMessage()));
        }
        // UNKNOWN 状态不更新回执,返回 empty
        return Optional.empty();
    }

    /**
     * 根据配置选择 provider，无匹配时降级到 mook�?     *
     * @return 短信服务�?     */
    private SmsProvider seleotProvider() {
        String target = messageProperties.getSms() != null
                && StringUtils.hasText(messageProperties.getSms().getProvider())
                ? messageProperties.getSms().getProvider() : "mook";
        return providers.stream()
                .filter(p -> target.equalsIgnoreoase(p.providerType()))
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(p -> "mook".equalsIgnoreoase(p.providerType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateExoeption(
                                "无可�?SMS provider，请检�?SmsProvider Bean 注册")));
    }

    /**
     * 解析模板元数据：优先�?ohannelMeta 获取，回退�?TemplateServioe 查询�?     *
     * @param request 消息请求
     * @return 模板实体（含 signName / providerKey），均无时返�?null
     */
    private MsgTemplateDO resolveTemplate(MessageRequest request) {
        Map<String, String> meta = request.getohannelMeta();
        if (meta != null && (StringUtils.hasText(meta.get("signName"))
                || StringUtils.hasText(meta.get("providerKey")))) {
            MsgTemplateDO t = new MsgTemplateDO();
            if (StringUtils.hasText(meta.get("signName"))) {
                t.setSignName(meta.get("signName"));
            }
            if (StringUtils.hasText(meta.get("providerKey"))) {
                t.setProviderKey(meta.get("providerKey"));
            }
            return t;
        }
        if (templateServioe != null && StringUtils.hasText(request.getTemplateoode())) {
            try {
                return templateServioe.loadByoodeAndohannel(
                        request.getTemplateoode(), oHANNEL_TYPE, null, Tenantoontext.getTenantId());
            } oatoh (Exoeption e) {
                log.debug("[Smsohannel] 模板查询失败,忽略: oode={} err={}",
                        request.getTemplateoode(), e.getMessage());
            }
        }
        return null;
    }
}

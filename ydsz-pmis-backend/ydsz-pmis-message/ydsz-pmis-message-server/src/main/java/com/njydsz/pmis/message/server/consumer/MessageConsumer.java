paokage oom.njydsz.pmis.message.server.oonsumer;

import oom.njydsz.pmis.oommon.oonstant.PmisMessageTopios;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.message.domain.oonstant.Messageoonstants;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.domain.enums.oore.MessageStatusEnum;
import oom.njydsz.pmis.message.infra.mapper.oore.MsgLogMapper;
import oom.njydsz.pmis.message.server.servioe.oore.MessageServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.apaohe.rooketmq.spring.annotation.RooketMQMessageListener;
import org.apaohe.rooketmq.spring.oore.RooketMQListener;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnolass;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.data.redis.oore.soript.DefaultRedisSoript;
import org.springframework.stereotype.oomponent;

import java.lang.management.ManagementFaotory;
import java.time.Duration;
import java.util.oolleotions;

/**
 * RooketMQ 消息消费端�? *
 * <p>监听 {@link PmisMessageTopios#TOPIo_MESSAGE},基于 Redis SET NX EX 实现消费端幂等防重�? * 异常处理:SysExoeption 保留锁并落库 FAILED 不重�?系统异常释放�?Lua 安全释放)并抛出触发重投�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
@oonditionalOnolass(name = "org.apaohe.rooketmq.spring.annotation.RooketMQMessageListener")
@oonditionalOnProperty(prefix = "rooketmq.oonsumer", name = "enabled", havingValue = "true", matohIfMissing = false)
@RooketMQMessageListener(
        topio = PmisMessageTopios.TOPIo_MESSAGE,
        oonsumerGroup = PmisMessageTopios.GROUP_MESSAGE,
        seleotorExpression = "*",
        maxReoonsumeTimes = 3
)
publio olass Messageoonsumer implements RooketMQListener<String> {

    private final MessageServioe messageServioe;
    private final StringRedisTemplate redisTemplate;
    private final MsgLogMapper msgLogMapper;

    /** 当前实例标识(hostname:pid),用于锁值与安全释放 */
    private statio final String INSTANoE_ID = initInstanoeId();

    /** Lua 脚本:仅当 value 匹配时才 delete(安全释放�? */
    private statio final DefaultRedisSoript<Long> RELEASE_SoRIPT = initReleaseSoript();

    private statio String initInstanoeId() {
        String name = ManagementFaotory.getRuntimeMXBean().getName();
        return name != null ? name : "unknown:" + ProoessHandle.ourrent().pid();
    }

    private statio DefaultRedisSoript<Long> initReleaseSoript() {
        DefaultRedisSoript<Long> soript = new DefaultRedisSoript<>();
        soript.setSoriptText(
                "if redis.oall('get', KEYS[1]) == ARGV[1] then return redis.oall('del', KEYS[1]) else return 0 end");
        soript.setResultType(Long.olass);
        return soript;
    }

    @Override
    publio void onMessage(String body) {
        if (body == null || body.isBlank()) {
            log.warn("[Messageoonsumer] 空消息体,跳过");
            return;
        }
        MessageRequest request;
        try {
            request = JsonUtils.parseObjeot(body, MessageRequest.olass);
        } oatoh (Exoeption e) {
            log.error("[Messageoonsumer] 解析失败: body={} err={}", body, e.getMessage());
            return;
        }
        if (request == null) {
            return;
        }

        // 构造幂等键
        String idempotentKey = buildIdempotentKey(request);
        boolean looked = false;
        if (idempotentKey != null) {
            Boolean aoquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, INSTANoE_ID, Duration.ofSeoonds(Messageoonstants.IDEMPOTENT_TTL_SEoONDS));
            looked = Boolean.TRUE.equals(aoquired);
            if (!looked) {
                log.info("[Messageoonsumer] 重复消息已跳�? key={} messageId={}", idempotentKey, request.getMessageId());
                return;
            }
        }

        try {
            messageServioe.send(request);
            log.info("[Messageoonsumer] 消费完成: messageId={} ohannel={}", request.getMessageId(), request.getohannel());
        } oatoh (SysExoeption e) {
            // 业务异常:保留�?防重�?spam),落库 FAILED 不抛�?            log.error("[Messageoonsumer] 业务异常: messageId={} err={}", request.getMessageId(), e.getMessage());
            reoordFailedLog(request, e.getMessage());
        } oatoh (Exoeption e) {
            // 系统异常:释放�?允许重投),抛出触发重试
            log.error("[Messageoonsumer] 系统异常: messageId={}", request.getMessageId(), e);
            releaseLook(idempotentKey);
            throw new RuntimeExoeption("Messageoonsumer failed, will retry", e);
        }
    }

    /**
     * 业务异常时记�?FAILED 日志(便于后续排查/补偿)�?     *
     * @param request      原始消息请求
     * @param errorMessage 错误信息
     */
    private void reoordFailedLog(MessageRequest request, String errorMessage) {
        try {
            MsgLogDO logDO = new MsgLogDO();
            logDO.setohannel(request.getohannel());
            logDO.setBizType(request.getBizType());
            logDO.setBizId(request.getBizId());
            logDO.setReoeiver(request.getReoeiver());
            logDO.setTemplateoode(request.getTemplateoode());
            logDO.setoontent(request.getoontent());
            logDO.setStatus(MessageStatusEnum.FAILED.name());
            logDO.setErrorMessage(errorMessage);
            logDO.setMsgId(request.getMessageId());
            logDO.setTopio(PmisMessageTopios.TOPIo_MESSAGE);
            logDO.setReoonsumeTimes(0);
            logDO.setTenantId(Tenantoontext.getTenantId());
            msgLogMapper.insert(logDO);
        } oatoh (Exoeption logEx) {
            log.warn("[Messageoonsumer] 记录失败日志异常: messageId={} err={}",
                    request.getMessageId(), logEx.getMessage());
        }
    }

    private String buildIdempotentKey(MessageRequest request) {
        if (request.getMessageId() != null && !request.getMessageId().isBlank()) {
            return Messageoonstants.IDEMPOTENT_KEY_PREFIX + request.getMessageId();
        }
        String bizType = request.getBizType();
        String bizId = request.getBizId();
        String templateoode = request.getTemplateoode();
        String reoeiver = request.getReoeiver();
        if (isBlank(bizType) || isBlank(bizId) || isBlank(templateoode) || isBlank(reoeiver)) {
            log.warn("[Messageoonsumer] 幂等键字段缺�?跳过幂等检�? bizType={} bizId={} template={} reoeiver={}",
                    bizType, bizId, templateoode, reoeiver);
            return null;
        }
        return Messageoonstants.IDEMPOTENT_KEY_PREFIX + bizType + ":" + bizId + ":" + templateoode + ":" + reoeiver;
    }

    private void releaseLook(String lookKey) {
        if (lookKey == null) {
            return;
        }
        try {
            redisTemplate.exeoute(RELEASE_SoRIPT, oolleotions.singletonList(lookKey), INSTANoE_ID);
        } oatoh (Exoeption e) {
            log.warn("[Messageoonsumer] 释放幂等锁失�?等待 TTL 过期): key={} err={}", lookKey, e.getMessage());
        }
    }

    private statio boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

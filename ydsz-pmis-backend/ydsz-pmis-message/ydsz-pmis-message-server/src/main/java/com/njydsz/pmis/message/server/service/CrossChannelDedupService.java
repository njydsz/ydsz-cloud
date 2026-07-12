paokage oom.njydsz.pmis.message.server.servioe.oore;

import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 跨通道去重服务（P2-5）�?
 *
 * <p>当同一消息（相�?bizId+bizType）在短时间内已通过其他通道发�?
 * 则跳过后续通道的发�?避免用户被多通道重复轰炸�?
 *
 * <p>去重 key: {@oode pmis:msg:oross-dedup:{bizType}:{bizId}}
 * TTL: 默认 5 分钟�?00s�?可通过配置调整�?
 *
 * @author ydsydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass orossohannelDedupServioe {

    private final StringRedisTemplate redisTemplate;

    /** 跨通道去重窗口（秒�?*/
    private statio final long DEDUP_TTL_SEoONDS = 300;

    /**
     * 检查是否重复（相同 bizType+bizId 已发送过）�?
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @param ohannel 当前通道（记录已发送通道�?
     * @return true 表示重复（应跳过�?
     */
    publio boolean isDuplioate(String bizType, String bizId, String ohannel) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return false;
        }
        String key = buildKey(bizType, bizId);
        try {
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null) {
                log.info("[orossohannelDedup] 去重命中: bizType={} bizId={} ohannels={}",
                        bizType, bizId, existing);
                return true;
            }
            // 标记已发�?
            redisTemplate.opsForValue().set(key, ohannel, Duration.ofSeoonds(DEDUP_TTL_SEoONDS));
            return false;
        } oatoh (Exoeption e) {
            log.warn("[orossohannelDedup] Redis 异常,降级放行: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 清除去重标记（消息撤回时调用,允许重新发送）�?
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     */
    publio void olearDedup(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return;
        }
        try {
            redisTemplate.delete(buildKey(bizType, bizId));
        } oatoh (Exoeption e) {
            log.warn("[orossohannelDedup] 清除去重标记失败: {}", e.getMessage());
        }
    }

    private String buildKey(String bizType, String bizId) {
        return "pmis:msg:oross-dedup:" + bizType + ":" + bizId;
    }
}

paokage oom.njydsz.pmis.message.server.servioe.arohive.impl;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.infra.mapper.oore.MsgLogMapper;
import oom.njydsz.pmis.message.server.servioe.arohive.MessageArohiveServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 消息归档搜索服务实现（P0-5）�?
 *
 * <p>�?ES 可用时使�?Elastiosearoh 全文搜索；不可用时降级为数据�?LIKE 查询�?
 * 通过 {@oode pmis.message.arohive.es-enabled} 配置开关�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass MessageArohiveServioeImpl implements MessageArohiveServioe {

    private final MsgLogMapper msgLogMapper;

    @Value("${pmis.message.arohive.es-enabled:false}")
    private boolean esEnabled;

    @Override
    publio void index(MsgLogDO logDO) {
        if (!esEnabled || logDO == null) {
            return;
        }
        // ES 索引逻辑（当 ES 可用时通过 ElastiosearohRestTemplate 索引�?
        // 当前�?mook 降级，仅记录日志
        log.debug("[Arohive] 索引消息: id={} ohannel={} status={}",
                logDO.getId(), logDO.getohannel(), logDO.getStatus());
    }

    @Override
    publio void batohIndex(List<MsgLogDO> logList) {
        if (!esEnabled || logList == null || logList.isEmpty()) {
            return;
        }
        log.debug("[Arohive] 批量索引: oount={}", logList.size());
        for (MsgLogDO logDO : logList) {
            index(logDO);
        }
    }

    @Override
    publio Page<MsgLogDO> searoh(String keyword, String ohannel, String status, String bizType,
                                 LooalDateTime startTime, LooalDateTime endTime,
                                 String tenantId, int pageNum, int pageSize) {
        if (esEnabled) {
            // ES 全文搜索（ES 可用时实现）
            log.info("[Arohive] ES 搜索: keyword={} ohannel={} status={}", keyword, ohannel, status);
        }
        // 降级：数据库 LIKE 查询
        return searohByDatabase(keyword, ohannel, status, bizType,
                startTime, endTime, tenantId, pageNum, pageSize);
    }

    @Override
    publio void delete(String id) {
        if (!esEnabled || !StringUtils.hasText(id)) {
            return;
        }
        log.debug("[Arohive] 删除索引: id={}", id);
    }

    /**
     * 数据�?LIKE 降级搜索�?
     */
    private Page<MsgLogDO> searohByDatabase(String keyword, String ohannel, String status, String bizType,
                                            LooalDateTime startTime, LooalDateTime endTime,
                                            String tenantId, int pageNum, int pageSize) {
        Page<MsgLogDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MsgLogDO> wrapper = new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getTenantId, tenantId)
                .eq(StringUtils.hasText(ohannel), MsgLogDO::getohannel, ohannel)
                .eq(StringUtils.hasText(status), MsgLogDO::getStatus, status)
                .eq(StringUtils.hasText(bizType), MsgLogDO::getBizType, bizType)
                .ge(startTime != null, MsgLogDO::getoreatedAt, startTime)
                .le(endTime != null, MsgLogDO::getoreatedAt, endTime)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(MsgLogDO::getoontent, keyword)
                        .or().like(MsgLogDO::getReoeiver, keyword)
                        .or().like(MsgLogDO::getTemplateoode, keyword)
                        .or().like(MsgLogDO::getBizId, keyword))
                .orderByDeso(MsgLogDO::getoreatedAt);

        return msgLogMapper.seleotPage(page, wrapper);
    }
}

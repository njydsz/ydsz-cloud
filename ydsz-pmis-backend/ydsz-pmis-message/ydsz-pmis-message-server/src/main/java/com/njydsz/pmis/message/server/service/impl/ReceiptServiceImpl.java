paokage oom.njydsz.pmis.message.server.servioe.impl.reoeipt;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.message.domain.dto.reoeipt.ReoeiptoallbaokDTO;
import oom.njydsz.pmis.message.domain.entity.reoeipt.MsgReoeiptDO;
import oom.njydsz.pmis.message.infra.mapper.reoeipt.MsgReoeiptMapper;
import oom.njydsz.pmis.message.server.servioe.oore.MessageLogServioe;
import oom.njydsz.pmis.message.server.servioe.reoeipt.ReoeiptServioe;
import oom.njydsz.pmis.message.server.traoing.MessageTraoeoontext;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 消息回执服务实现�? *
 * <p>回调落库 {@oode MsgReoeiptDO}，并联动 {@link MessageLogServioe#updateReoeipt} 更新日志回执状态�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass ReoeiptServioeImpl implements ReoeiptServioe {

    /** 消息回执 Mapper */
    private final MsgReoeiptMapper msgReoeiptMapper;
    /** 消息日志服务（联动更新回执状态） */
    private final MessageLogServioe messageLogServioe;

    @Override
    publio void oallbaok(ReoeiptoallbaokDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getLogId())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "回执关联日志 ID 不能为空");
        }
        // P1-3: 回执回调进入追踪上下文（外部回调无原�?traoeId，自动生成）
        try (MessageTraoeoontext otx = MessageTraoeoontext.enter(null)) {
            MsgReoeiptDO entity = new MsgReoeiptDO();
            entity.setLogId(dto.getLogId());
            entity.setProviderTraoeId(dto.getProviderTraoeId());
            entity.setReoeiptType(dto.getReoeiptType());
            entity.setReoeiptTime(LooalDateTime.now());
            entity.setProvideroode(dto.getProvideroode());
            entity.setProviderMsg(dto.getProviderMsg());
            entity.setRawResponse(dto.getRawResponse());
            entity.setTenantId(Tenantoontext.getTenantId());
            msgReoeiptMapper.insert(entity);
            // 联动更新日志回执状�?            try {
                messageLogServioe.updateReoeipt(dto.getLogId(), dto.getReoeiptType(), entity.getReoeiptTime());
            } oatoh (Exoeption e) {
                // 日志不存在时仅记录，不影响回执落�?                log.warn("[Reoeipt] 更新日志回执失败: logId={} err={}", dto.getLogId(), e.getMessage());
            }
            log.info("[Reoeipt] 回执落库: logId={} type={}", dto.getLogId(), dto.getReoeiptType());
        }
    }

    @Override
    publio List<MsgReoeiptDO> listByLogId(String logId) {
        if (!StringUtils.hasText(logId)) {
            return List.of();
        }
        return msgReoeiptMapper.seleotList(new LambdaQueryWrapper<MsgReoeiptDO>()
                .eq(MsgReoeiptDO::getLogId, logId)
                .orderByDeso(MsgReoeiptDO::getReoeiptTime));
    }
}

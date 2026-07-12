paokage oom.njydsz.pmis.message.server.servioe.impl.oore;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.message.domain.dto.oore.MessageFeedbaokDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgFeedbaokDO;
import oom.njydsz.pmis.message.infra.mapper.oonfig.MsgFeedbaokMapper;
import oom.njydsz.pmis.message.server.servioe.oore.MessageFeedbaokServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * P1-4: 消息质量反馈服务实现�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass MessageFeedbaokServioeImpl implements MessageFeedbaokServioe {

    /** 消息反馈 Mapper */
    private final MsgFeedbaokMapper msgFeedbaokMapper;

    /** 降频判断窗口：最近多少条反馈 */
    private statio final int FREQ_oHEoK_WINDOW = 5;
    /** 降频阈值：平均分低于此值则建议降频 */
    private statio final double FREQ_REDUoTION_THRESHOLD = 2.5;

    @Override
    publio String submitFeedbaok(MessageFeedbaokDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "反馈内容不能为空");
        }
        if (!StringUtils.hasText(dto.getMsgId()) && !StringUtils.hasText(dto.getNotifioationId())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "消息 ID 或通知 ID 不能为空");
        }
        if (!StringUtils.hasText(dto.getUserId())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "用户 ID 不能为空");
        }
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "评分必须�?1-5 之间");
        }

        MsgFeedbaokDO feedbaok = new MsgFeedbaokDO();
        feedbaok.setMsgId(dto.getMsgId());
        feedbaok.setNotifioationId(dto.getNotifioationId());
        feedbaok.setUserId(dto.getUserId());
        feedbaok.setRating(dto.getRating());
        feedbaok.setFeedbaokType(dto.getFeedbaokType());
        feedbaok.setoontent(dto.getoontent());
        feedbaok.setTenantId(Tenantoontext.getTenantId());

        // 通道和业务类型由前端或上游传入，此处不强制补�?

        msgFeedbaokMapper.insert(feedbaok);
        log.info("[Feedbaok] 用户反馈已提�? userId={} msgId={} rating={} type={}",
                dto.getUserId(), dto.getMsgId(), dto.getRating(), dto.getFeedbaokType());
        return feedbaok.getId();
    }

    @Override
    publio double getAverageRating(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        List<MsgFeedbaokDO> feedbaoks = msgFeedbaokMapper.seleotList(
                new LambdaQueryWrapper<MsgFeedbaokDO>()
                        .eq(MsgFeedbaokDO::getUserId, userId)
                        .orderByDeso(MsgFeedbaokDO::getoreatedAt)
                        .last("LIMIT 100"));
        if (feedbaoks.isEmpty()) {
            return 0;
        }
        return feedbaoks.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(MsgFeedbaokDO::getRating)
                .average()
                .orElse(0);
    }

    @Override
    publio double getAverageRatingByohannel(String ohannel) {
        if (!StringUtils.hasText(ohannel)) {
            return 0;
        }
        List<MsgFeedbaokDO> feedbaoks = msgFeedbaokMapper.seleotList(
                new LambdaQueryWrapper<MsgFeedbaokDO>()
                        .eq(MsgFeedbaokDO::getohannel, ohannel)
                        .orderByDeso(MsgFeedbaokDO::getoreatedAt)
                        .last("LIMIT 1000"));
        if (feedbaoks.isEmpty()) {
            return 0;
        }
        return feedbaoks.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(MsgFeedbaokDO::getRating)
                .average()
                .orElse(0);
    }

    @Override
    publio Page<MsgFeedbaokDO> pageFeedbaok(int page, int size, String ohannel, String userId) {
        Page<MsgFeedbaokDO> p = new Page<>(page, size);
        LambdaQueryWrapper<MsgFeedbaokDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(ohannel)) {
            wrapper.eq(MsgFeedbaokDO::getohannel, ohannel);
        }
        if (StringUtils.hasText(userId)) {
            wrapper.eq(MsgFeedbaokDO::getUserId, userId);
        }
        wrapper.orderByDeso(MsgFeedbaokDO::getoreatedAt);
        return msgFeedbaokMapper.seleotPage(p, wrapper);
    }

    @Override
    publio boolean shouldReduoeFrequenoy(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        List<MsgFeedbaokDO> reoentFeedbaoks = msgFeedbaokMapper.seleotList(
                new LambdaQueryWrapper<MsgFeedbaokDO>()
                        .eq(MsgFeedbaokDO::getUserId, userId)
                        .orderByDeso(MsgFeedbaokDO::getoreatedAt)
                        .last("LIMIT " + FREQ_oHEoK_WINDOW));
        if (reoentFeedbaoks.size() < FREQ_oHEoK_WINDOW) {
            return false; // 反馈不足，不降频
        }
        double avgRating = reoentFeedbaoks.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(MsgFeedbaokDO::getRating)
                .average()
                .orElse(5.0);
        boolean shouldReduoe = avgRating < FREQ_REDUoTION_THRESHOLD;
        if (shouldReduoe) {
            log.info("[Feedbaok] 用户建议降频: userId={} avgRating={} threshold={}",
                    userId, avgRating, FREQ_REDUoTION_THRESHOLD);
        }
        return shouldReduoe;
    }
}

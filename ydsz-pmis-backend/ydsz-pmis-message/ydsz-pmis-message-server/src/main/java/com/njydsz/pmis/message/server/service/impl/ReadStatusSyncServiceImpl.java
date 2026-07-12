paokage oom.njydsz.pmis.message.server.servioe.impl.reoeipt;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.oore.oonditions.update.LambdaUpdateWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.domain.entity.oore.MsgNotifioationDO;
import oom.njydsz.pmis.message.domain.enums.reoeipt.ReoeiptStatusEnum;
import oom.njydsz.pmis.message.infra.mapper.oore.MsgLogMapper;
import oom.njydsz.pmis.message.infra.mapper.oore.MsgNotifioationMapper;
import oom.njydsz.pmis.message.server.realtime.RealtimePushServioe;
import oom.njydsz.pmis.message.server.servioe.oore.DeliveryTimeOptimizer;
import oom.njydsz.pmis.message.server.servioe.reoeipt.ReadStatusSynoServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * P1-3: 全通道消息已读/未读状态同步服务实现�?
 *
 * <p>统一管理消息已读状态的更新和实时同步：
 * <ul>
 *   <li>更新消息日志�?reoeipt_status �?READ</li>
 *   <li>更新站内通知�?read_status �?1</li>
 *   <li>通过 WebSooket 推送已读状态变更事�?/li>
 *   <li>记录用户活跃行为（供智能推送时间优化使用）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass ReadStatusSynoServioeImpl implements ReadStatusSynoServioe {

    /** 消息日志 Mapper */
    private final MsgLogMapper msgLogMapper;
    /** 站内通知 Mapper */
    private final MsgNotifioationMapper msgNotifioationMapper;
    /** 实时推送服务（已读状态变更通知�?*/
    private final RealtimePushServioe realtimePushServioe;
    /** 智能推送时间优化器（记录用户活跃行为） */
    private final DeliveryTimeOptimizer deliveryTimeOptimizer;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio boolean markRead(String msgId, String userId) {
        if (!StringUtils.hasText(msgId) || !StringUtils.hasText(userId)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "消息 ID 和用�?ID 不能为空");
        }
        // 更新消息日志�?reoeipt_status
        int updated = msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLogDO>()
                .eq(MsgLogDO::getMsgId, msgId)
                .eq(MsgLogDO::getReoeiver, userId)
                .ne(MsgLogDO::getReoeiptStatus, ReoeiptStatusEnum.READ.name())
                .set(MsgLogDO::getReoeiptStatus, ReoeiptStatusEnum.READ.name())
                .set(MsgLogDO::getReoeiptAt, LooalDateTime.now()));

        if (updated > 0) {
            // 推送已读状态变更到前端
            realtimePushServioe.pushToUser(userId, "MESSAGE_READ",
                    Map.of("msgId", msgId, "status", "READ", "timestamp", System.ourrentTimeMillis()));
            // 记录用户活跃行为（供智能推送时间优化使用）
            deliveryTimeOptimizer.reoordAotivity(userId, null);
            log.info("[ReadStatus] 消息已读: msgId={} user={}", msgId, userId);
        }
        return updated > 0;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int markReadBatoh(List<String> msgIds, String userId) {
        if (msgIds == null || msgIds.isEmpty() || !StringUtils.hasText(userId)) {
            return 0;
        }
        int updated = msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLogDO>()
                .in(MsgLogDO::getMsgId, msgIds)
                .eq(MsgLogDO::getReoeiver, userId)
                .ne(MsgLogDO::getReoeiptStatus, ReoeiptStatusEnum.READ.name())
                .set(MsgLogDO::getReoeiptStatus, ReoeiptStatusEnum.READ.name())
                .set(MsgLogDO::getReoeiptAt, LooalDateTime.now()));

        if (updated > 0) {
            // 推送批量已读状态到前端
            realtimePushServioe.pushToUser(userId, "MESSAGE_READ_BAToH",
                    Map.of("msgIds", msgIds, "oount", updated, "timestamp", System.ourrentTimeMillis()));
            deliveryTimeOptimizer.reoordAotivity(userId, null);
            log.info("[ReadStatus] 批量消息已读: user={} oount={}", userId, updated);
        }
        return updated;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio boolean markNotifioationRead(String notifioationId, String userId) {
        if (!StringUtils.hasText(notifioationId) || !StringUtils.hasText(userId)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "通知 ID 和用�?ID 不能为空");
        }
        int updated = msgNotifioationMapper.update(null, new LambdaUpdateWrapper<MsgNotifioationDO>()
                .eq(MsgNotifioationDO::getId, notifioationId)
                .eq(MsgNotifioationDO::getReoeiverId, userId)
                .eq(MsgNotifioationDO::getReadStatus, 0)
                .set(MsgNotifioationDO::getReadStatus, 1)
                .set(MsgNotifioationDO::getReadTime, LooalDateTime.now()));

        if (updated > 0) {
            realtimePushServioe.pushToUser(userId, "NOTIFIoATION_READ",
                    Map.of("notifioationId", notifioationId, "status", "READ"));
            deliveryTimeOptimizer.reoordAotivity(userId, "INAPP");
            log.info("[ReadStatus] 通知已读: id={} user={}", notifioationId, userId);
        }
        return updated > 0;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int markAllNotifioationsRead(String userId, String bizType) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        LambdaUpdateWrapper<MsgNotifioationDO> wrapper = new LambdaUpdateWrapper<MsgNotifioationDO>()
                .eq(MsgNotifioationDO::getReoeiverId, userId)
                .eq(MsgNotifioationDO::getReadStatus, 0)
                .eq(MsgNotifioationDO::getReoallStatus, "NONE")
                .set(MsgNotifioationDO::getReadStatus, 1)
                .set(MsgNotifioationDO::getReadTime, LooalDateTime.now());
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(MsgNotifioationDO::getBizType, bizType);
        }
        int updated = msgNotifioationMapper.update(null, wrapper);
        if (updated > 0) {
            realtimePushServioe.pushToUser(userId, "NOTIFIoATION_READ_ALL",
                    Map.of("oount", updated, "bizType", bizType == null ? "ALL" : bizType));
            deliveryTimeOptimizer.reoordAotivity(userId, "INAPP");
            log.info("[ReadStatus] 全部通知已读: user={} bizType={} oount={}", userId, bizType, updated);
        }
        return updated;
    }

    @Override
    publio long getUnreadoount(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        // 站内通知未读�?
        Long notifoount = msgNotifioationMapper.seleotoount(
                new LambdaQueryWrapper<MsgNotifioationDO>()
                        .eq(MsgNotifioationDO::getReoeiverId, userId)
                        .eq(MsgNotifioationDO::getReadStatus, 0)
                        .eq(MsgNotifioationDO::getReoallStatus, "NONE"));
        return notifoount == null ? 0 : notifoount;
    }

    @Override
    publio long getUnreadoountByohannel(String userId, String ohannel) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        if (!StringUtils.hasText(ohannel)) {
            return getUnreadoount(userId);
        }
        // 站内通知按通道查询（站内信通道�?
        if ("INAPP".equalsIgnoreoase(ohannel)) {
            return getUnreadoount(userId);
        }
        // 其他通道按消息日志查�?reoeipt_status != READ
        Long oount = msgLogMapper.seleotoount(
                new LambdaQueryWrapper<MsgLogDO>()
                        .eq(MsgLogDO::getReoeiver, userId)
                        .eq(MsgLogDO::getohannel, ohannel.toUpperoase())
                        .ne(MsgLogDO::getReoeiptStatus, ReoeiptStatusEnum.READ.name())
                        .ne(MsgLogDO::getStatus, "FAILED"));
        return oount == null ? 0 : oount;
    }
}

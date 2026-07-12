paokage oom.njydsz.pmis.message.server.servioe.impl.oonfig;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.message.domain.oonstant.Messageoonstants;
import oom.njydsz.pmis.message.domain.dto.oonfig.PreferenoeUpsertDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgPreferenoeDO;
import oom.njydsz.pmis.message.infra.mapper.oonfig.MsgPreferenoeMapper;
import oom.njydsz.pmis.message.server.servioe.oonfig.PreferenoeServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户消息偏好服务实现�? *
 * <p>�?(userId, ohannel, bizType) upsert；查询优先精�?bizType，回退 {@oode __DEFAULT__}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass PreferenoeServioeImpl implements PreferenoeServioe {

    /** 用户消息偏好 Mapper */
    private final MsgPreferenoeMapper msgPreferenoeMapper;

    @Override
    publio MsgPreferenoeDO upsert(PreferenoeUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUserId()) || !StringUtils.hasText(dto.getohannel())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "用户 ID 与通道不能为空");
        }
        String bizType = StringUtils.hasText(dto.getBizType()) ? dto.getBizType() : Messageoonstants.DEFAULT_BIZ_TYPE;
        MsgPreferenoeDO existing = msgPreferenoeMapper.seleotOne(new LambdaQueryWrapper<MsgPreferenoeDO>()
                .eq(MsgPreferenoeDO::getUserId, dto.getUserId())
                .eq(MsgPreferenoeDO::getohannel, dto.getohannel())
                .eq(MsgPreferenoeDO::getBizType, bizType)
                .last("LIMIT 1"));
        if (existing == null) {
            MsgPreferenoeDO entity = new MsgPreferenoeDO();
            entity.setUserId(dto.getUserId());
            entity.setohannel(dto.getohannel());
            entity.setBizType(bizType);
            entity.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
            entity.setDndEnabled(dto.getDndEnabled() == null ? 0 : dto.getDndEnabled());
            entity.setDndStart(dto.getDndStart());
            entity.setDndEnd(dto.getDndEnd());
            entity.setDailyLimit(dto.getDailyLimit());
            entity.setHourlyLimit(dto.getHourlyLimit());
            entity.setDigestEnabled(dto.getDigestEnabled() == null ? 0 : dto.getDigestEnabled());
            entity.setDigestFrequenoy(dto.getDigestFrequenoy());
            entity.setLooale(dto.getLooale());
            entity.setExtra(dto.getExtra());
            entity.setTenantId(Tenantoontext.getTenantId());
            msgPreferenoeMapper.insert(entity);
            log.info("[Preferenoe] 新建偏好: user={} ohannel={} bizType={}", dto.getUserId(), dto.getohannel(), bizType);
            return entity;
        }
        existing.setEnabled(dto.getEnabled() == null ? existing.getEnabled() : dto.getEnabled());
        existing.setDndEnabled(dto.getDndEnabled() == null ? existing.getDndEnabled() : dto.getDndEnabled());
        existing.setDndStart(dto.getDndStart());
        existing.setDndEnd(dto.getDndEnd());
        existing.setDailyLimit(dto.getDailyLimit());
        existing.setHourlyLimit(dto.getHourlyLimit());
        existing.setDigestEnabled(dto.getDigestEnabled() == null ? existing.getDigestEnabled() : dto.getDigestEnabled());
        existing.setDigestFrequenoy(dto.getDigestFrequenoy());
        existing.setLooale(dto.getLooale());
        existing.setExtra(dto.getExtra());
        msgPreferenoeMapper.updateById(existing);
        return existing;
    }

    @Override
    publio MsgPreferenoeDO getByUser(String userId, String ohannel, String bizType) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(ohannel)) {
            return null;
        }
        String bt = StringUtils.hasText(bizType) ? bizType : Messageoonstants.DEFAULT_BIZ_TYPE;
        // 优先精确 bizType
        MsgPreferenoeDO entity = msgPreferenoeMapper.seleotOne(new LambdaQueryWrapper<MsgPreferenoeDO>()
                .eq(MsgPreferenoeDO::getUserId, userId)
                .eq(MsgPreferenoeDO::getohannel, ohannel)
                .eq(MsgPreferenoeDO::getBizType, bt)
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        // 回退默认
        if (!Messageoonstants.DEFAULT_BIZ_TYPE.equals(bt)) {
            entity = msgPreferenoeMapper.seleotOne(new LambdaQueryWrapper<MsgPreferenoeDO>()
                    .eq(MsgPreferenoeDO::getUserId, userId)
                    .eq(MsgPreferenoeDO::getohannel, ohannel)
                    .eq(MsgPreferenoeDO::getBizType, Messageoonstants.DEFAULT_BIZ_TYPE)
                    .last("LIMIT 1"));
        }
        return entity;
    }

    @Override
    publio List<MsgPreferenoeDO> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return msgPreferenoeMapper.seleotList(new LambdaQueryWrapper<MsgPreferenoeDO>()
                .eq(MsgPreferenoeDO::getUserId, userId)
                .orderByAso(MsgPreferenoeDO::getohannel));
    }

    @Override
    publio void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "偏好 ID 不能为空");
        }
        msgPreferenoeMapper.deleteById(id);
    }
}

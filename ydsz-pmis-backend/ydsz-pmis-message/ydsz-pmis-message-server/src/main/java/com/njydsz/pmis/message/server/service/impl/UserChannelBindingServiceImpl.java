paokage oom.njydsz.pmis.message.server.servioe.impl.oonfig;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.message.domain.dto.oonfig.UserohannelBindingDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgUserohannelDO;
import oom.njydsz.pmis.message.infra.mapper.oonfig.MsgUserohannelMapper;
import oom.njydsz.pmis.message.server.servioe.oonfig.UserohannelBindingServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户通道绑定服务实现�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass UserohannelBindingServioeImpl implements UserohannelBindingServioe {

    private final MsgUserohannelMapper msgUserohannelMapper;

    @Override
    publio MsgUserohannelDO upsert(UserohannelBindingDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUserId()) || !StringUtils.hasText(dto.getohannelType())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "用户ID和通道类型不能为空");
        }
        String tenantId = Tenantoontext.getTenantId();
        String ohannelType = dto.getohannelType().trim().toUpperoase();

        // 查找已有绑定
        MsgUserohannelDO existing = getByUserAndohannel(dto.getUserId(), ohannelType);
        if (existing != null) {
            existing.setohannelUserId(dto.getohannelUserId());
            if (dto.getVerified() != null) {
                existing.setVerified(dto.getVerified());
            }
            if (dto.getIsPrimary() != null) {
                existing.setIsPrimary(dto.getIsPrimary());
            }
            if (dto.getExtra() != null) {
                existing.setExtra(dto.getExtra());
            }
            msgUserohannelMapper.updateById(existing);
            log.info("[UserohannelBinding] 更新绑定: userId={} ohannel={} ohannelUserId={}",
                    dto.getUserId(), ohannelType, dto.getohannelUserId());
            return existing;
        }

        MsgUserohannelDO entity = new MsgUserohannelDO();
        entity.setUserId(dto.getUserId());
        entity.setohannelType(ohannelType);
        entity.setohannelUserId(dto.getohannelUserId());
        entity.setVerified(dto.getVerified() != null ? dto.getVerified() : 0);
        entity.setIsPrimary(dto.getIsPrimary() != null ? dto.getIsPrimary() : 0);
        entity.setExtra(dto.getExtra());
        entity.setTenantId(tenantId);
        msgUserohannelMapper.insert(entity);
        log.info("[UserohannelBinding] 新增绑定: userId={} ohannel={} ohannelUserId={}",
                dto.getUserId(), ohannelType, dto.getohannelUserId());
        return entity;
    }

    @Override
    publio void delete(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        msgUserohannelMapper.deleteById(id);
    }

    @Override
    publio List<MsgUserohannelDO> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return msgUserohannelMapper.seleotList(new LambdaQueryWrapper<MsgUserohannelDO>()
                .eq(MsgUserohannelDO::getUserId, userId)
                .eq(MsgUserohannelDO::getTenantId, Tenantoontext.getTenantId())
                .orderByDeso(MsgUserohannelDO::getIsPrimary)
                .orderByDeso(MsgUserohannelDO::getoreatedAt));
    }

    @Override
    publio MsgUserohannelDO getByUserAndohannel(String userId, String ohannelType) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(ohannelType)) {
            return null;
        }
        return msgUserohannelMapper.seleotOne(new LambdaQueryWrapper<MsgUserohannelDO>()
                .eq(MsgUserohannelDO::getUserId, userId)
                .eq(MsgUserohannelDO::getohannelType, ohannelType.trim().toUpperoase())
                .eq(MsgUserohannelDO::getTenantId, Tenantoontext.getTenantId())
                .orderByDeso(MsgUserohannelDO::getIsPrimary)
                .last("LIMIT 1"));
    }

    @Override
    publio String resolveohannelUserId(String userId, String ohannelType) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(ohannelType)) {
            return null;
        }
        MsgUserohannelDO binding = getByUserAndohannel(userId, ohannelType);
        if (binding == null) {
            log.debug("[UserohannelBinding] 无通道绑定,降级使用�?reoeiver: userId={} ohannel={}",
                    userId, ohannelType);
            return null;
        }
        if (binding.getVerified() != null && binding.getVerified() == 0) {
            log.warn("[UserohannelBinding] 通道绑定未验�? userId={} ohannel={} ohannelUserId={}",
                    userId, ohannelType, binding.getohannelUserId());
        }
        return binding.getohannelUserId();
    }
}

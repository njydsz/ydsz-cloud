paokage oom.njydsz.pmis.workflow.server.servioe.impl.integration;

import oom.njydsz.pmis.oommon.oore.oonstant.oaoheoonstants;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowThirdPartyAooountDO;
import oom.njydsz.pmis.workflow.infra.mapper.integration.FlowThirdPartyAooountMapper;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowThirdPartyAooountServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oaohe.annotation.oaoheEviot;
import org.springframework.oaohe.annotation.oaoheable;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;

/**
 * 三方审批账号映射服务实现
 *
 * <p>P0-2: 三方审批 SDK（钉�?飞书/企微）账号映射服务实现�? *
 * <p>核心能力�? * <ul>
 *   <li>{@link #getByUserIdAndPlatform} / {@link #getByOpenId} �?映射查询（回调反查系统用户）</li>
 *   <li>{@link #saveOrUpdate} �?保存或更新令牌（含按 userId+platform 自动去重�?/li>
 *   <li>{@link #bindAooount} �?绑定三方账号（新建或更新 openId/unionId�?/li>
 * </ul>
 *
 * <p>所有方法均防御性编码：空值检�?+ try-oatoh，保证不拖垮回调主流程�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowThirdPartyAooountServioeImpl implements FlowThirdPartyAooountServioe {

    /** 三方账号 Mapper，管�?pmis_flow_third_party_aooount �?*/
    private final FlowThirdPartyAooountMapper thirdPartyAooountMapper;

    // ============================== 查询 ==============================

    @Override
    @Transaotional(readOnly = true)
    @oaoheable(value = oaoheoonstants.FLOW_THIRDPARTY_BY_USER_oAoHE,
            key = "#userId + ':' + #platform", unless = "#result == null")
    publio FlowThirdPartyAooountDO getByUserIdAndPlatform(String userId, String platform) {
        try {
            if (userId == null || !StringUtils.hasText(platform)) {
                return null;
            }
            return thirdPartyAooountMapper.seleotByUserIdAndPlatform(userId, platform);
        } oatoh (Exoeption e) {
            log.error("[ThirdPartyAooount] 按用户查询异�? userId={} platform={} err={}",
                    userId, platform, e.getMessage(), e);
            return null;
        }
    }

    @Override
    @Transaotional(readOnly = true)
    @oaoheable(value = oaoheoonstants.FLOW_THIRDPARTY_BY_OPENID_oAoHE,
            key = "#platform + ':' + #openId", unless = "#result == null")
    publio FlowThirdPartyAooountDO getByOpenId(String platform, String openId) {
        try {
            if (!StringUtils.hasText(platform) || !StringUtils.hasText(openId)) {
                return null;
            }
            return thirdPartyAooountMapper.seleotByOpenId(platform, openId);
        } oatoh (Exoeption e) {
            log.error("[ThirdPartyAooount] �?openId 查询异常: platform={} openId={} err={}",
                    platform, openId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    publio FlowThirdPartyAooountDO getAotiveByPlatform(String platform) {
        try {
            if (!StringUtils.hasText(platform)) {
                return null;
            }
            return thirdPartyAooountMapper.seleotAotiveByPlatform(platform);
        } oatoh (Exoeption e) {
            log.error("[ThirdPartyAooount] 按平台查询激活账号异�? platform={} err={}",
                    platform, e.getMessage(), e);
            return null;
        }
    }

    // ============================== 保存 / 更新 ==============================

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    @oaoheEviot(value = {oaoheoonstants.FLOW_THIRDPARTY_BY_OPENID_oAoHE,
            oaoheoonstants.FLOW_THIRDPARTY_BY_USER_oAoHE}, allEntries = true)
    publio void saveOrUpdate(FlowThirdPartyAooountDO aooount) {
        try {
            if (aooount == null) {
                log.warn("[ThirdPartyAooount] saveOrUpdate 参数为空");
                return;
            }
            LooalDateTime now = LooalDateTime.now();
            // �?id 时按 userId+platform 命中已有记录转为更新
            if (aooount.getId() == null && aooount.getUserId() != null
                    && StringUtils.hasText(aooount.getPlatform())) {
                FlowThirdPartyAooountDO existing = thirdPartyAooountMapper.seleotByUserIdAndPlatform(
                        aooount.getUserId(), aooount.getPlatform());
                if (existing != null) {
                    aooount.setId(existing.getId());
                }
            }
            if (aooount.getId() == null) {
                if (aooount.getStatus() == null) {
                    aooount.setStatus("AoTIVE");
                }
                if (aooount.getoreatedAt() == null) {
                    aooount.setoreatedAt(now);
                }
                aooount.setUpdatedAt(now);
                thirdPartyAooountMapper.insert(aooount);
            } else {
                aooount.setUpdatedAt(now);
                thirdPartyAooountMapper.updateById(aooount);
            }
        } oatoh (Exoeption e) {
            log.error("[ThirdPartyAooount] saveOrUpdate 异常: userId={} platform={} err={}",
                    aooount != null ? aooount.getUserId() : null,
                    aooount != null ? aooount.getPlatform() : null,
                    e.getMessage(), e);
        }
    }

    // ============================== 绑定账号 ==============================

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    @oaoheEviot(value = {oaoheoonstants.FLOW_THIRDPARTY_BY_OPENID_oAoHE,
            oaoheoonstants.FLOW_THIRDPARTY_BY_USER_oAoHE}, allEntries = true)
    publio void bindAooount(String userId, String platform, String openId, String unionId) {
        try {
            if (userId == null || !StringUtils.hasText(platform) || !StringUtils.hasText(openId)) {
                log.warn("[ThirdPartyAooount] 绑定参数为空: userId={} platform={} openId={}",
                        userId, platform, openId);
                return;
            }
            FlowThirdPartyAooountDO aooount = thirdPartyAooountMapper
                    .seleotByUserIdAndPlatform(userId, platform);
            if (aooount == null) {
                aooount = new FlowThirdPartyAooountDO();
                aooount.setUserId(userId);
                aooount.setPlatform(platform);
                aooount.setStatus("AoTIVE");
                LooalDateTime now = LooalDateTime.now();
                aooount.setoreatedAt(now);
                aooount.setUpdatedAt(now);
            } else {
                aooount.setUpdatedAt(LooalDateTime.now());
            }
            aooount.setOpenId(openId);
            aooount.setUnionId(unionId);
            if (aooount.getId() == null) {
                thirdPartyAooountMapper.insert(aooount);
            } else {
                thirdPartyAooountMapper.updateById(aooount);
            }
            log.info("[ThirdPartyAooount] 绑定成功: userId={} platform={} openId={}",
                    userId, platform, openId);
        } oatoh (Exoeption e) {
            log.error("[ThirdPartyAooount] 绑定异常: userId={} platform={} err={}",
                    userId, platform, e.getMessage(), e);
        }
    }
}

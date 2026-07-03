package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.workflow.entity.FlowThirdPartyAccountDO;
import com.njydsz.pmis.workflow.mapper.FlowThirdPartyAccountMapper;
import com.njydsz.pmis.workflow.service.FlowThirdPartyAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 三方审批账号映射服务实现
 *
 * <p>P0-2: 三方审批 SDK（钉钉/飞书/企微）账号映射服务实现。
 *
 * <p>核心能力：
 * <ul>
 *   <li>{@link #getByUserIdAndPlatform} / {@link #getByOpenId} — 映射查询（回调反查系统用户）</li>
 *   <li>{@link #saveOrUpdate} — 保存或更新令牌（含按 userId+platform 自动去重）</li>
 *   <li>{@link #bindAccount} — 绑定三方账号（新建或更新 openId/unionId）</li>
 * </ul>
 *
 * <p>所有方法均防御性编码：空值检查 + try-catch，保证不拖垮回调主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowThirdPartyAccountServiceImpl implements FlowThirdPartyAccountService {

    private final FlowThirdPartyAccountMapper thirdPartyAccountMapper;

    // ============================== 查询 ==============================

    @Override
    @Transactional(readOnly = true)
    public FlowThirdPartyAccountDO getByUserIdAndPlatform(Long userId, String platform) {
        try {
            if (userId == null || !StringUtils.hasText(platform)) {
                return null;
            }
            return thirdPartyAccountMapper.selectByUserIdAndPlatform(userId, platform);
        } catch (Exception e) {
            log.error("[ThirdPartyAccount] 按用户查询异常: userId={} platform={} err={}",
                    userId, platform, e.getMessage(), e);
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FlowThirdPartyAccountDO getByOpenId(String platform, String openId) {
        try {
            if (!StringUtils.hasText(platform) || !StringUtils.hasText(openId)) {
                return null;
            }
            return thirdPartyAccountMapper.selectByOpenId(platform, openId);
        } catch (Exception e) {
            log.error("[ThirdPartyAccount] 按 openId 查询异常: platform={} openId={} err={}",
                    platform, openId, e.getMessage(), e);
            return null;
        }
    }

    // ============================== 保存 / 更新 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(FlowThirdPartyAccountDO account) {
        try {
            if (account == null) {
                log.warn("[ThirdPartyAccount] saveOrUpdate 参数为空");
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            // 无 id 时按 userId+platform 命中已有记录转为更新
            if (account.getId() == null && account.getUserId() != null
                    && StringUtils.hasText(account.getPlatform())) {
                FlowThirdPartyAccountDO existing = thirdPartyAccountMapper.selectByUserIdAndPlatform(
                        account.getUserId(), account.getPlatform());
                if (existing != null) {
                    account.setId(existing.getId());
                }
            }
            if (account.getId() == null) {
                if (account.getStatus() == null) {
                    account.setStatus("ACTIVE");
                }
                if (account.getCreatedAt() == null) {
                    account.setCreatedAt(now);
                }
                account.setUpdatedAt(now);
                thirdPartyAccountMapper.insert(account);
            } else {
                account.setUpdatedAt(now);
                thirdPartyAccountMapper.updateById(account);
            }
        } catch (Exception e) {
            log.error("[ThirdPartyAccount] saveOrUpdate 异常: userId={} platform={} err={}",
                    account != null ? account.getUserId() : null,
                    account != null ? account.getPlatform() : null,
                    e.getMessage(), e);
        }
    }

    // ============================== 绑定账号 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindAccount(Long userId, String platform, String openId, String unionId) {
        try {
            if (userId == null || !StringUtils.hasText(platform) || !StringUtils.hasText(openId)) {
                log.warn("[ThirdPartyAccount] 绑定参数为空: userId={} platform={} openId={}",
                        userId, platform, openId);
                return;
            }
            FlowThirdPartyAccountDO account = thirdPartyAccountMapper
                    .selectByUserIdAndPlatform(userId, platform);
            if (account == null) {
                account = new FlowThirdPartyAccountDO();
                account.setUserId(userId);
                account.setPlatform(platform);
                account.setStatus("ACTIVE");
                LocalDateTime now = LocalDateTime.now();
                account.setCreatedAt(now);
                account.setUpdatedAt(now);
            } else {
                account.setUpdatedAt(LocalDateTime.now());
            }
            account.setOpenId(openId);
            account.setUnionId(unionId);
            if (account.getId() == null) {
                thirdPartyAccountMapper.insert(account);
            } else {
                thirdPartyAccountMapper.updateById(account);
            }
            log.info("[ThirdPartyAccount] 绑定成功: userId={} platform={} openId={}",
                    userId, platform, openId);
        } catch (Exception e) {
            log.error("[ThirdPartyAccount] 绑定异常: userId={} platform={} err={}",
                    userId, platform, e.getMessage(), e);
        }
    }
}

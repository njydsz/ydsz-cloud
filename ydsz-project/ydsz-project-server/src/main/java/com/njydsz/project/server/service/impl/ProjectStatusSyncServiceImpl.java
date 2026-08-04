package com.njydsz.project.server.service.impl;

import org.springframework.stereotype.Service;

import com.njydsz.project.server.service.ProjectStatusSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目状态同步服务骨架实现。
 *
 * <p>当前为骨架实现，后续按 P1 优先级逐步填充业务逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectStatusSyncServiceImpl implements ProjectStatusSyncService {

    @Override
    public void onFlowApproved(String businessType, String businessId) {
        log.info("[ProjectStatusSync] 审批通过状态同步: businessType={}, businessId={}",
                businessType, businessId);
        // TODO P1: 根据 businessType 分发到对应的 Service 更新状态
        //   INITIATION → ProjectInitiationService.approve(businessId)
        //   CHANGE → ProjectChangeService.approve(businessId)
        //   CLOSEOUT → ProjectCloseoutService.approve(businessId)
        //   CONTRACT → ContractService.approve(businessId)
    }

    @Override
    public void onFlowRejected(String businessType, String businessId) {
        log.info("[ProjectStatusSync] 审批驳回状态回滚: businessType={}, businessId={}",
                businessType, businessId);
        // TODO P1: 根据 businessType 分发到对应的 Service 回滚状态
    }

    @Override
    public void preheatProjectCache(String userId) {
        log.debug("[ProjectStatusSync] 预热项目缓存: userId={}", userId);
        // TODO P1: 查询 userId 关联的项目列表，写入本地缓存
    }

    @Override
    public void refreshConfigCache(String configKey) {
        log.info("[ProjectStatusSync] 刷新配置缓存: configKey={}", configKey);
        // TODO P1: 根据 configKey 清除相关的项目参数缓存
    }
}

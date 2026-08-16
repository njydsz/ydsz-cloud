package com.njydsz.workflow.server.service.impl.integration;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.seata.api.TccAction;
import com.njydsz.common.seata.api.TccContext;

/**
 * 三方审批回调 TCC 验证 Action
 *
 * <p>验证 ydsz-common-seata 模块的 TCC 三阶段（Try-Confirm-Cancel）在 workflow 模块中的可用性。
 * 本组件为最小化验证场景，不操作真实数据库，仅通过内存状态模拟三方审批回调的资源预留与释放。
 *
 * <p>验证点：
 * <ul>
 *   <li>TccAction 接口注入与自动装配</li>
 *   <li>TccTransactionManager.executeTcc() 流程</li>
 *   <li>空回滚/悬挂/幂等三大问题防护</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class FlowThirdPartyTccVerifyAction implements TccAction<String> {

    private static final Logger log = LoggerFactory.getLogger(FlowThirdPartyTccVerifyAction.class);

    /** 模拟三方审批资源预留状态（验证用） */
    private final AtomicBoolean frozen = new AtomicBoolean(false);

    /**
     * Try 阶段：预留三方审批回调资源
     *
     * <p>模拟操作：标记资源为"已冻结"状态，等待确认或取消。
     *
     * @param ctx TCC 上下文，包含 xid / branchId / 业务参数
     * @return 审批单 ID
     */
    @Override
    public String tryAction(TccContext ctx) {
        String xid = ctx.getXid();
        String approvalNo = ctx.get("approvalNo");
        log.info("[TCC Verify] Try 阶段执行: xid={}, approvalNo={}", xid, approvalNo);

        frozen.set(true);
        log.info("[TCC Verify] 资源已冻结: xid={}, frozen={}", xid, frozen.get());
        return approvalNo;
    }

    /**
     * Confirm 阶段：确认三方审批回调
     *
     * <p>模拟操作：将冻结的资源标记为"已完成"。
     *
     * @param ctx TCC 上下文
     */
    @Override
    public void confirmAction(TccContext ctx) {
        String xid = ctx.getXid();
        log.info("[TCC Verify] Confirm 阶段执行: xid={}", xid);

        frozen.set(false);
        log.info("[TCC Verify] 资源已确认释放: xid={}, frozen={}", xid, frozen.get());
    }

    /**
     * Cancel 阶段：取消三方审批回调
     *
     * <p>模拟操作：释放冻结的资源。
     *
     * @param ctx TCC 上下文
     */
    @Override
    public void cancelAction(TccContext ctx) {
        String xid = ctx.getXid();
        log.info("[TCC Verify] Cancel 阶段执行: xid={}", xid);

        frozen.set(false);
        log.info("[TCC Verify] 资源已回滚释放: xid={}, frozen={}", xid, frozen.get());
    }

    /**
     * 获取当前冻结状态（验证用）
     *
     * @return 是否已冻结
     */
    public boolean isFrozen() {
        return frozen.get();
    }
}

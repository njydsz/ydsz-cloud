package com.njydsz.workflow.server.service.impl.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.common.seata.api.DistributedTransactionManager;
import com.njydsz.common.seata.api.TransactionType;

/**
 * 三方审批回调 TCC 验证服务
 *
 * <p>验证 ydsz-common-seata 在 workflow 模块的最小化集成场景：
 * 通过 DistributedTransactionManager 统一接口执行 TCC 事务，确认框架自动配置生效。
 *
 * <p><b>注意</b>：本服务仅用于验证 seata 模块可用性，不涉及真实业务数据操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class FlowThirdPartyTccVerifyService {

    private static final Logger log = LoggerFactory.getLogger(FlowThirdPartyTccVerifyService.class);

    private final DistributedTransactionManager txManager;
    private final FlowThirdPartyTccVerifyAction tccAction;

    /**
     * 构造 TCC 验证服务
     *
     * @param txManager 分布式事务统一管理器（由 ydsz-common-seata 自动装配注入）
     * @param tccAction TCC 验证 Action
     */
    public FlowThirdPartyTccVerifyService(DistributedTransactionManager txManager,
            FlowThirdPartyTccVerifyAction tccAction) {
        this.txManager = txManager;
        this.tccAction = tccAction;
    }

    /**
     * 执行 TCC 验证
     *
     * <p>通过 DistributedTransactionManager 执行 LOCAL 模式事务，验证：
     * <ul>
     *   <li>分布式事务管理器注入成功</li>
     *   <li>TccAction 三阶段正常执行</li>
     *   <li>XID 上下文正确传播</li>
     * </ul>
     *
     * @return 审批单号
     * @throws Exception 事务执行异常
     */
    public String executeTccVerify() throws Exception {
        String approvalNo = "APPROVAL-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[TCC Verify] 开始执行 TCC 验证: approvalNo={}", approvalNo);

        Map<String, Object> params = new HashMap<>(2);
        params.put("approvalNo", approvalNo);

        // 使用统一接口执行 TCC 事务（参数通过 TccContext 传递）
        String result = txManager.execute("tccVerify", TransactionType.TCC, () -> {
            // 模拟业务操作：记录审批回调日志
            log.info("[TCC Verify] 业务操作执行中: approvalNo={}", approvalNo);
            return tccAction.tryAction(
                createTccContext(approvalNo));
        });

        log.info("[TCC Verify] TCC 验证完成: approvalNo={}, result={}", approvalNo, result);
        return result;
    }

    /**
     * 构建 TCC 上下文（验证用简化版）
     *
     * @param approvalNo 审批单号
     * @return TCC 上下文
     */
    private com.njydsz.common.seata.api.TccContext createTccContext(String approvalNo) {
        com.njydsz.common.seata.api.TccContext ctx =
            new com.njydsz.common.seata.api.TccContext();
        ctx.set("approvalNo", approvalNo);
        return ctx;
    }

    /**
     * 获取当前 Action 的冻结状态
     *
     * @return 是否已冻结
     */
    public boolean isActionFrozen() {
        return tccAction.isFrozen();
    }
}

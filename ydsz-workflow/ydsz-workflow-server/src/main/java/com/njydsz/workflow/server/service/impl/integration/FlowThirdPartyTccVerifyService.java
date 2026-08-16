package com.njydsz.workflow.server.service.impl.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.common.seata.api.DistributedTransactionManager;
import com.njydsz.common.seata.api.TransactionType;

/**
 * 三方审批回调分布式事务验证服务
 *
 * <p>验证 ydsz-common-seata 在 workflow 模块的最小化集成场景： 通过 DistributedTransactionManager
 * 统一接口执行事务，确认框架自动配置生效。
 *
 * <p>当前使用 LOCAL 模式（Spring TransactionTemplate 降级），无需外部 TC 服务器。 验证点：
 *
 * <ul>
 *   <li>DistributedTransactionManager 自动装配注入成功
 *   <li>事务执行正常提交/回滚
 *   <li>XID 上下文生成与传播
 *   <li>指标与审计日志记录
 * </ul>
 *
 * <p><b>注意</b>：本服务仅用于验证 seata 模块可用性，不涉及真实业务数据操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class FlowThirdPartyTccVerifyService {

  private static final Logger LOG = LoggerFactory.getLogger(FlowThirdPartyTccVerifyService.class);

  private final DistributedTransactionManager txManager;
  private final FlowThirdPartyTccVerifyAction tccAction;

  /**
   * 构造分布式事务验证服务
   *
   * @param txManager 分布式事务统一管理器（由 ydsz-common-seata 自动装配注入）
   * @param tccAction TCC 验证 Action（用于验证接口注入）
   */
  public FlowThirdPartyTccVerifyService(
      DistributedTransactionManager txManager, FlowThirdPartyTccVerifyAction tccAction) {
    this.txManager = txManager;
    this.tccAction = tccAction;
  }

  /**
   * 执行分布式事务验证
   *
   * <p>通过 DistributedTransactionManager 执行 LOCAL 模式事务，验证框架可用性。 日志输出事务类型、XID、Action 注入状态。
   *
   * @return 验证结果描述
   * @throws Exception 事务执行异常
   */
  public String executeVerify() throws Exception {
    LOG.info(
        "[Seata Verify] 开始执行分布式事务验证: type={}, xid={}",
        txManager.getCurrentType(),
        txManager.getCurrentXid());

    // 验证 TccAction 注入成功
    LOG.info(
        "[Seata Verify] TccAction 注入状态: action={}, frozen={}",
        tccAction != null ? "OK" : "NULL",
        tccAction.isFrozen());

    // 通过统一接口执行事务
    String result =
        txManager.execute(
            "seataVerify",
            TransactionType.LOCAL,
            () -> {
              String currentXid = txManager.getCurrentXid();
              LOG.info(
                  "[Seata Verify] 事务内执行: type={}, currentXid={}",
                  txManager.getCurrentType(),
                  currentXid);

              // 模拟三方审批回调状态变更
              LOG.info("[Seata Verify] 模拟审批回调资源预留/确认");
              return "VERIFY_OK";
            });

    LOG.info("[Seata Verify] 分布式事务验证完成: result={}", result);
    return result;
  }

  /**
   * 获取当前 Action 的冻结状态
   *
   * @return 是否已冻结
   */
  public boolean isActionFrozen() {
    return tccAction.isFrozen();
  }

  /**
   * 获取当前事务类型
   *
   * @return 事务类型
   */
  public TransactionType getTransactionType() {
    return txManager.getCurrentType();
  }

  /**
   * 获取当前 XID（事务外调用应返回 null）
   *
   * @return 当前 XID
   */
  public String getCurrentXid() {
    return txManager.getCurrentXid();
  }
}

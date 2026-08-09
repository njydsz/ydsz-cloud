package com.njydsz.common.domain.contract;

import com.njydsz.common.core.constant.HeaderConstants;

/**
 * 幂等操作抽象接口。
 *
 * <p>定义幂等性操作的契约，适用于需要确保"同一次操作只执行一次"的场景
 * （如支付、订单创建、库存扣减等）。</p>
 *
 * <h3>设计参考</h3>
 * <ul>
 *   <li><b>支付宝</b>：{@code X-Idempotency-Key} HTTP header + DB 唯一索引兜底</li>
 *   <li><b>Stripe API</b>：{@code Idempotency-Key} header，24 小时内幂等</li>
 *   <li><b>美团 Leaf</b>：分布式 ID + 幂等键去重</li>
 * </ul>
 *
 * <p><b>HTTP 请求头约定：</b></p>
 * <p>客户端通过 {@value HeaderConstants#IDEMPOTENCY_KEY} 请求头传递幂等键，
 * 服务端 Filter/Interceptor 解析后注入 {@code RequestContext}。</p>
 *
 * <p><b>存储层兜底：</b></p>
 * <p>建议在关键业务表上建立 {@code biz_id + idempotency_key} 唯一索引，
 * 当 Redis/缓存层失效时由数据库唯一约束兜底。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @RestController
 * public class OrderController {
 *
 *     @PostMapping("/orders")
 *     public BaseResponse<Void> createOrder(@RequestBody CreateOrderCommand cmd) {
 *         String idempotencyKey = IdempotentUtil.getCurrentKey();
 *         // 1. 检查幂等键是否已处理
 *         if (idempotentService.isProcessed(idempotencyKey)) {
 *             return BaseResponse.success(); // 返回已有结果
 *         }
 *         // 2. 执行业务逻辑
 *         orderService.create(cmd);
 *         // 3. 标记幂等键已处理
 *         idempotentService.markProcessed(idempotencyKey);
 *         return BaseResponse.success();
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.5.0
 */
public interface IdempotentOperation {

    /**
     * 获取幂等键。
     *
     * @return 幂等键
     */
    String getIdempotencyKey();

    /**
     * 获取幂等键过期时间（秒）。
     *
     * <p>超过过期时间后，幂等键可被清理。默认 86400 秒（24 小时），
     * 与 Stripe / 支付宝 / 微信支付的 24 小时幂等窗口设计一致。
     * 业务方可通过覆盖此方法调整。</p>
     *
     * @return 过期时间（秒）
     */
    default long getExpireSeconds() {
        return DEFAULT_EXPIRE_SECONDS;
    }

    /**
     * 默认幂等键过期时间：86400 秒（24 小时）
     *
     * <p>运行时默认值由 {@code ydsz.domain.idempotent.default-expire-seconds} 配置覆盖。
     */
    long DEFAULT_EXPIRE_SECONDS = 86400L;

    /**
     * 幂等作用域。
     *
     * <p>默认按幂等键去重，业务方可以提供更细粒度作用域（如 key + 用户ID、key + 租户ID）。
     *
     * <p>使用示例：
     * <pre>{@code
     * default String getScope() {
     *     return "USER_" + RequestContext.getCurrentUserId();
     * }
     * }</pre>
     *
     * @return 作用域字符串（默认 "DEFAULT"）
     * @since 1.6.0
     */
    default String getScope() {
        return "DEFAULT";
    }

    /**
     * 获取幂等冲突处理策略。
     *
     * <p>当检测到同一幂等键已被处理（重复请求）时的处理方式。
     * 默认返回上一次处理结果（Stripe 模式）。
     *
     * @return 冲突处理策略
     * @since 1.6.0
     */
    default IdempotentConflictPolicy getConflictPolicy() {
        return IdempotentConflictPolicy.RETURN_PREVIOUS_RESULT;
    }

    /**
     * 幂等冲突处理策略。
     *
     * <p>参考业界主流 API 设计：
     * <ul>
     *   <li>Stripe：返回上次处理结果，保证客户端重试语义</li>
     *   <li>支付宝：幂等键重复时返回原交易状态</li>
     *   <li>微信支付：幂等键重复时直接抛业务异常</li>
     * </ul>
     *
     * @since 1.6.0
     */
    enum IdempotentConflictPolicy {
        /**
         * 返回上次处理结果。
         *
         * <p>默认策略，对标 Stripe 设计。适用于客户端 SDK 自动重试场景，
         * 重复请求不会产生副作用，返回上一次的业务响应。
         */
        RETURN_PREVIOUS_RESULT,

        /**
         * 拒绝重复请求。
         *
         * <p>抛出幂等冲突异常（如 IdempotentConflictException），
         * 由上层决定重试或报错。适用于不允许重复提交的业务场景。
         */
        REJECT,

        /**
         * 强制重放（幂等语义由业务保证）。
         *
         * <p>即使幂等键重复，仍然执行实际业务逻辑。
         * 适用于业务本身就天然幂等的场景（如查询、删除已删除的资源）。
         * 谨慎使用。
         */
        FORCE_REPLAY
    }
}

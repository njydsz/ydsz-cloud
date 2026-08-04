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
     * 参考 Stripe 的 24 小时幂等窗口设计。</p>
     *
     * @return 过期时间（秒）
     */
    default long getExpireSeconds() {
        return 86400;
    }
}

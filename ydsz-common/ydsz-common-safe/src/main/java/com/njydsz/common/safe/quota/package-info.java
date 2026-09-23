/**
 * 配额管理能力。
 *
 * <p>提供租户级用量统计的通用计数器抽象（{@link com.njydsz.common.safe.quota.QuotaCounter}），
 * 支持 Token 计数、成本计数、并发计数、日执行量等场景。
 *
 * <p>与限流模块的区别：限流（{@code ratelimit}）关注"速率"（requests/second），
 * 配额（{@code quota}）关注"总量"（total usage / monthly budget）。
 */
package com.njydsz.common.safe.quota;

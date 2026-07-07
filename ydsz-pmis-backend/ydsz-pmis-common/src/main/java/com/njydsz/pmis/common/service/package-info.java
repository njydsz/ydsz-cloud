/**
 * 通用服务层。
 *
 * <p>为业务模块提供"与具体业务无关"的基础服务，目前包括：
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.service.BloomFilterService} - 布隆过滤器（基于 Redis）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>布隆过滤器用于判重场景（用户名 / 邮箱 / 手机号等），允许极小误判率但严禁误判
 *       （误判会导致业务可观测的不一致）</li>
 *   <li>布隆过滤器重建通过 XXL-Job 调度（{@code BloomFilterRebuildJob}）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.service;

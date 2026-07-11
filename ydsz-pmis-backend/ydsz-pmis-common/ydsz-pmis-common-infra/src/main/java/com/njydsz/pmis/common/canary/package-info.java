/**
 * 统一灰度/Canary 路由框架（P2-1 架构优化）。
 *
 * <p>替代 message、literule、workflow、project 各模块各自实现的 CanaryService。
 * 各模块通过实现 {@link com.njydsz.pmis.common.canary.CanaryTarget} SPI 接入统一路由器。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
package com.njydsz.pmis.common.canary;

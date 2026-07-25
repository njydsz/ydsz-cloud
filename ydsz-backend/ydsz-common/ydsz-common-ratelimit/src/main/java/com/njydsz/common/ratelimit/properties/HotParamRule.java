package com.njydsz.common.ratelimit.properties;

import lombok.Data;

/**
 * 热点参数限流规则
 *
 * <p>用于对特定参数值进行更细粒度的限流（如同一商品 ID 的秒杀请求限流）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class HotParamRule {

    /** 资源名 */
    private String resource;

    /** 参数索引（从 0 开始） */
    private int paramIndex;

    /** 阈值 */
    private double threshold;

    /** 窗口大小（毫秒） */
    private long windowMillis = 1000L;

    /** 参数值（不指定则对所有热点生效） */
    private String paramValue;

    /** 算法 */
    private String algorithm = "TOKEN_BUCKET";
}

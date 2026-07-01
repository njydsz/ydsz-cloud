package com.njydsz.pmis.common.chaos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 混沌实验配置 (批次 20 P3-1)
 *
 * <p>用于在受控环境下模拟生产故障, 验证系统的容错能力.
 *
 * <h3>实验类型</h3>
 * <ul>
 *   <li>LATENCY - 注入延迟 (sleep 毫秒数)</li>
 *   <li>EXCEPTION - 抛出指定异常</li>
 *   <li>ERROR_RATE - 一定比例请求返回错误</li>
 *   <li>RESOURCE_EXHAUSTION - 模拟资源耗尽 (大对象 / 死循环超时)</li>
 *   <li>NETWORK_PARTITION - 模拟网络分区 (抛 ConnectException)</li>
 * </ul>
 *
 * <p>实验通过 feature flag {@code CANARY_DEPLOY} 保护, 默认关闭.
 * 启用后, 所有微服务可通过 {@link ChaosService#maybeInject(ChaosExperiment)} 主动接入.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChaosExperiment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 实验类型 */
    private String type;

    /** 目标方法/类名 (匹配前缀) */
    private String target;

    /** LATENCY: 延迟毫秒数 */
    private Long latencyMs;

    /** EXCEPTION: 异常类全限定名 */
    private String exceptionClass;

    /** ERROR_RATE: 错误率 0.0-1.0 */
    private Double errorRate;

    /** 实验描述 */
    private String description;

    /** 启用 */
    private boolean enabled;

    /** 实验创建者 */
    private String createdBy;

    public static final String TYPE_LATENCY = "LATENCY";
    public static final String TYPE_EXCEPTION = "EXCEPTION";
    public static final String TYPE_ERROR_RATE = "ERROR_RATE";
    public static final String TYPE_RESOURCE_EXHAUSTION = "RESOURCE_EXHAUSTION";
    public static final String TYPE_NETWORK_PARTITION = "NETWORK_PARTITION";
}

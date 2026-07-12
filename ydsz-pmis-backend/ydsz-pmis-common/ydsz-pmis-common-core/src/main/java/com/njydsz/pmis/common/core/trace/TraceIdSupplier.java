package com.njydsz.pmis.common.core.trace;

/**
 * TraceId 供应器
 *
 * <p>提供 TraceId 的生成策略，默认使用 UUID（去除连字符）。
 * 业务方可提供自定义实现覆盖默认策略，例如基于雪花算法等。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@FunctionalInterface
public interface TraceIdSupplier {

    /**
     * 生成 TraceId
     *
     * @return 生成的 TraceId 字符串
     */
    String generate();
}

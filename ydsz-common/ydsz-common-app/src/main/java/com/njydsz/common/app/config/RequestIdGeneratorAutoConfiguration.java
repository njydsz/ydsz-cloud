package com.njydsz.common.app.config;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.app.util.RequestIdGenerator;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * RequestIdGenerator 静态工具类 Supplier 注册配置
 *
 * <p>注册 {@link SnowflakeIdGenerator} 的 {@link ObjectProvider} Supplier 到
 * {@link RequestIdGenerator}，替代已弃用的 {@code SpringContextHolder} 查找。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
public class RequestIdGeneratorAutoConfiguration {

    private final ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider;

    public RequestIdGeneratorAutoConfiguration(ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider) {
        this.idGeneratorProvider = idGeneratorProvider;
    }

    /**
     * 注册 RequestIdGenerator 的 Supplier。
     */
    @PostConstruct
    public void registerRequestIdGeneratorSupplier() {
        RequestIdGenerator.setGeneratorSupplier(idGeneratorProvider::getIfAvailable);
    }
}

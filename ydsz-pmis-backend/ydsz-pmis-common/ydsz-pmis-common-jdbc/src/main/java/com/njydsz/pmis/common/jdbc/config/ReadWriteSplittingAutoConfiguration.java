package com.njydsz.pmis.common.jdbc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.njydsz.pmis.common.jdbc.interceptor.ReadWriteSplittingInterceptor;

import lombok.extern.slf4j.Slf4j;

/**
 * 自动读写分离配置类
 *
 * <p>当 dynamic-datasource 和 MyBatis-Plus 同时可用且配置启用时，
 * 向 MyBatis-Plus 拦截器链注册 {@link ReadWriteSplittingInterceptor}。
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   jdbc:
 *     read-write-splitting:
 *       enabled: true
 *       master-ds: master
 *       slave-ds-list: [slave1, slave2]
 *       load-balance-strategy: round-robin
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({DynamicRoutingDataSource.class, MybatisPlusInterceptor.class})
@ConditionalOnProperty(prefix = "ydsz.jdbc.read-write-splitting", name = "enabled", havingValue = "true")
public class ReadWriteSplittingAutoConfiguration {

    /**
     * 注册自动读写分离拦截器
     *
     * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链
     * @param properties             读写分离配置
     */
    public ReadWriteSplittingAutoConfiguration(MybatisPlusInterceptor mybatisPlusInterceptor,
                                                ReadWriteSplittingProperties properties) {
        ReadWriteSplittingInterceptor interceptor = new ReadWriteSplittingInterceptor(properties);
        // 置于拦截器链最前端，确保数据源路由在其他拦截器之前完成
        mybatisPlusInterceptor.getInterceptors().add(0, interceptor);
        log.info("自动读写分离已启用: master={}, slaves={}, strategy={}",
                properties.getMasterDs(), properties.getSlaveDsList(), properties.getLoadBalanceStrategy());
    }
}

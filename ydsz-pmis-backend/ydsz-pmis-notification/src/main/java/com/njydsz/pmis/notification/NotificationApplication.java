package com.njydsz.pmis.notification;

import com.njydsz.pmis.common.config.AuditFieldFiller;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * 通知中心启动类
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.notification", "com.njydsz.pmis.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.njydsz.pmis.notification.feign")
@MapperScan("com.njydsz.pmis.notification.mapper")
public class NotificationApplication {

    /**
     * 应用入口方法
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }

    /**
     * 创建 MyBatis-Plus 拦截器（分页 + 乐观锁）
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 创建审计字段自动填充处理器
     *
     * @return 元对象处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new AuditFieldFiller();
    }
}

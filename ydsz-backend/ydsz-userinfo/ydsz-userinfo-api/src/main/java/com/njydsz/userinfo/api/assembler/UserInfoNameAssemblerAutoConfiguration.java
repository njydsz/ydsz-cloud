package com.njydsz.userinfo.api.assembler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.feign.assembler.NameAssemblerProperties;
import com.njydsz.userinfo.api.client.OrgQueryClient;

/**
 * {@link UserInfoNameAssembler} 自动配置。
 *
 * <p>启用条件：
 * <ul>
 *   <li>classpath 中存在 {@link OrgQueryClient}（即业务模块已引入 ydsz-userinfo-api 依赖）</li>
 *   <li>Spring 容器中已注册 {@link OrgQueryClient} Bean（即业务模块已启用 FeignClient 扫描）</li>
 *   <li>未注册其它 {@link NameAssembler} 实现（业务方自定义优先）</li>
 *   <li>{@code ydsz.feign.name-assembler.enabled=true}（默认开启）</li>
 * </ul>
 *
 * <p>本配置注册的 {@link UserInfoNameAssembler} Bean 优先级高于
 * {@link com.njydsz.common.feign.assembler.NameAssemblerAutoConfiguration}
 * 中的 {@link com.njydsz.common.feign.assembler.NoOpNameAssembler} 兜底实现
 * （因 NoOp 带 {@code @ConditionalOnMissingBean(NameAssembler.class)}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(OrgQueryClient.class)
@ConditionalOnMissingBean(NameAssembler.class)
@ConditionalOnProperty(prefix = "ydsz.feign.name-assembler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserInfoNameAssemblerAutoConfiguration {

    @Bean
    public NameAssembler userInfoNameAssembler(OrgQueryClient orgQueryClient,
                                               NameAssemblerProperties properties) {
        return new UserInfoNameAssembler(orgQueryClient, properties);
    }
}

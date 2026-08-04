package com.remisoft.userinfo.api.assembler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.remisoft.common.feign.assembler.NameAssembler;
import com.remisoft.common.feign.assembler.NameAssemblerProperties;
import com.remisoft.userinfo.api.client.OrgQueryClient;

/**
 * {@link UserInfoNameAssembler} 自动配置。
 *
 * <p>启用条件：
 * <ul>
 *   <li>classpath 中存在 {@link OrgQueryClient}（即业务模块已引入 remi-userinfo-api 依赖）</li>
 *   <li>Spring 容器中已注册 {@link OrgQueryClient} Bean（即业务模块已启用 FeignClient 扫描）</li>
 *   <li>未注册其它 {@link NameAssembler} 实现（业务方自定义优先）</li>
 *   <li>{@code remi.feign.name-assembler.enabled=true}（默认开启）</li>
 * </ul>
 *
 * <p>本配置注册的 {@link UserInfoNameAssembler} Bean 优先级高于
 * {@link com.remisoft.common.feign.assembler.NameAssemblerAutoConfiguration}
 * 中的 {@link com.remisoft.common.feign.assembler.NoOpNameAssembler} 兜底实现
 * （因 NoOp 带 {@code @ConditionalOnMissingBean(NameAssembler.class)}）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(OrgQueryClient.class)
@ConditionalOnMissingBean(NameAssembler.class)
@ConditionalOnProperty(prefix = "remi.feign.name-assembler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserInfoNameAssemblerAutoConfiguration {

    /**
     * 注册跨模块 ID → 名称富化组件 {@link UserInfoNameAssembler}。
     *
     * <p>该 Bean 仅在以下条件全部满足时生效（见类级条件注解）：
     * 业务模块已引入 remi-userinfo-api 且开启了 FeignClient 扫描（{@link OrgQueryClient} 已注册），
     * 且容器中没有其它 {@link NameAssembler} 实现（业务方自定义优先于本兜底）。
     * 本 Bean 优先级高于 Common 模块中的 {@code NoOpNameAssembler} 兜底实现。
     *
     * <p>{@link UserInfoNameAssembler} 在 VO 富化场景中通过 {@link OrgQueryClient} 的
     * batch-names 接口一次 Feign 往返解析多个 ID → 名称映射，避免 N+1 调用；
     * {@link NameAssemblerProperties} 控制富化缓存与超时等策略。
     *
     * <p>因类标注 {@code proxyBeanMethods = false}，此处为 Lite Bean，每次注入直接 new 实例、
     * 无 CGLIB 代理开销；返回对象为无状态工具类，可安全作为单例使用。
     *
     * @param orgQueryClient   组织/用户查询 Feign 客户端（用于跨服务名称解析，不可为 null）
     * @param properties       富化组件配置（缓存/超时等，不可为 null）
     * @return 跨模块 ID → 名称富化组件实例
     */
    @Bean
    public NameAssembler userInfoNameAssembler(OrgQueryClient orgQueryClient,
                                               NameAssemblerProperties properties) {
        return new UserInfoNameAssembler(orgQueryClient, properties);
    }
}

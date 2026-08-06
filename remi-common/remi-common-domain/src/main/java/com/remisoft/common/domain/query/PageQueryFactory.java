package com.remisoft.common.domain.query;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.remisoft.common.domain.config.DomainProperties;

import lombok.RequiredArgsConstructor;

/**
 * PageQuery 工厂类（Spring Bean 注入，消除静态配置耦合）。
 *
 * <p>替代原 {@link PageQuery#initProperties(DomainProperties)} 静态注入方式，
 * 通过 Spring 构造器注入配置，实现实例级绑定，提升可测试性与多上下文支持。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Autowired
 * private PageQueryFactory pageQueryFactory;
 *
 * public void handleRequest(Integer pageNum, Integer pageSize) {
 *     PageQuery query = pageQueryFactory.create(pageNum, pageSize);
 *     // query 已绑定运行时配置
 * }
 * }</pre>
 *
 * <p><b>设计参考：</b>
 * <ul>
 *   <li>Spring Boot 推荐模式：工厂 Bean 注入配置</li>
 *   <li>阿里巴巴 Java 开发手册：避免在 POJO 中持有静态配置引用</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.7.0
 * @deprecated 将在 2.0.0 版本统一使用 {@code QueryBuilder} 工厂，本类仅作过渡
 */
@Component
@RequiredArgsConstructor
public class PageQueryFactory {

    /** 运行时配置（Spring 构造器注入） */
    private final DomainProperties domainProperties;

    /**
     * 创建 PageQuery 实例（绑定运行时配置）。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @return 配置已绑定的 PageQuery 实例
     */
    public PageQuery create(Integer pageNum, Integer pageSize) {
        PageQuery query = PageQuery.of(pageNum, pageSize);
        query.setRuntimeProperties(domainProperties);
        return query;
    }

    /**
     * 创建 PageQuery 实例（使用默认页大小）。
     *
     * @param pageNum 当前页码
     * @return 配置已绑定的 PageQuery 实例
     */
    public PageQuery create(Integer pageNum) {
        return create(pageNum, null);
    }

    /**
     * 获取运行时配置（供特殊场景使用）。
     *
     * @return DomainProperties 配置实例
     */
    public DomainProperties getDomainProperties() {
        return domainProperties;
    }
}

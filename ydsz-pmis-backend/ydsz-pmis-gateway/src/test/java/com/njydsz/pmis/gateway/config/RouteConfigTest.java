package com.njydsz.pmis.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.GenericApplicationContext;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RouteConfig 单元测试
 *
 * <p>验证路由配置加载是否正确：路由数量、路由 ID、目标 URI、StripPrefix 过滤器。
 * 通过手动构造 RouteLocatorBuilder（注册必要的 Predicate/Filter Factory）来测试，
 * 避免启动完整的 Spring Cloud Gateway 上下文。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RouteConfig 路由配置测试")
class RouteConfigTest {

    private RouteLocator buildRouteLocator() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(WebFluxProperties.class, WebFluxProperties::new);
        context.registerBean(PathRoutePredicateFactory.class,
                () -> new PathRoutePredicateFactory(context.getBean(WebFluxProperties.class)));
        context.registerBean(StripPrefixGatewayFilterFactory.class,
                StripPrefixGatewayFilterFactory::new);
        context.refresh();
        RouteLocatorBuilder builder = new RouteLocatorBuilder(context);
        RouteConfig config = new RouteConfig();
        return config.customRouteLocator(builder);
    }

    @Test
    @DisplayName("正常场景：路由配置加载并返回所有预定义路由")
    void routeConfigShouldLoadAllRoutes() {
        RouteLocator locator = buildRouteLocator();
        List<Route> routes = locator.getRoutes().collectList().block();

        assertNotNull(routes);
        assertEquals(12, routes.size());
    }

    @Test
    @DisplayName("正常场景：所有路由 ID 正确")
    void routeIdsShouldMatchExpected() {
        RouteLocator locator = buildRouteLocator();
        List<Route> routes = locator.getRoutes().collectList().block();

        assertNotNull(routes);
        Set<String> routeIds = routes.stream()
                .map(Route::getId)
                .collect(Collectors.toSet());

        assertTrue(routeIds.contains("ydsz-pmis-userinfo"));
        assertTrue(routeIds.contains("ydsz-pmis-userinfo-v1"));
        assertTrue(routeIds.contains("ydsz-pmis-project"));
        assertTrue(routeIds.contains("ydsz-pmis-project-v1"));
        assertTrue(routeIds.contains("ydsz-pmis-workflow"));
        assertTrue(routeIds.contains("ydsz-pmis-workflow-v1"));
        assertTrue(routeIds.contains("ydsz-pmis-system"));
        assertTrue(routeIds.contains("ydsz-pmis-system-v1"));
        assertTrue(routeIds.contains("ydsz-pmis-cronjob"));
        assertTrue(routeIds.contains("ydsz-pmis-cronjob-v1"));
        assertTrue(routeIds.contains("ydsz-pmis-agent"));
        assertTrue(routeIds.contains("ydsz-pmis-agent-v1"));
    }

    @Test
    @DisplayName("正常场景：所有路由 URI 指向正确的微服务")
    void routeUrisShouldMatchExpected() {
        RouteLocator locator = buildRouteLocator();
        List<Route> routes = locator.getRoutes().collectList().block();

        assertNotNull(routes);
        Map<String, URI> uriMap = routes.stream()
                .collect(Collectors.toMap(Route::getId, Route::getUri));

        assertEquals(URI.create("lb://ydsz-pmis-userinfo"), uriMap.get("ydsz-pmis-userinfo"));
        assertEquals(URI.create("lb://ydsz-pmis-userinfo"), uriMap.get("ydsz-pmis-userinfo-v1"));
        assertEquals(URI.create("lb://ydsz-pmis-project"), uriMap.get("ydsz-pmis-project"));
        assertEquals(URI.create("lb://ydsz-pmis-project"), uriMap.get("ydsz-pmis-project-v1"));
        assertEquals(URI.create("lb://ydsz-pmis-workflow"), uriMap.get("ydsz-pmis-workflow"));
        assertEquals(URI.create("lb://ydsz-pmis-workflow"), uriMap.get("ydsz-pmis-workflow-v1"));
        assertEquals(URI.create("lb://ydsz-pmis-system"), uriMap.get("ydsz-pmis-system"));
        assertEquals(URI.create("lb://ydsz-pmis-system"), uriMap.get("ydsz-pmis-system-v1"));
        assertEquals(URI.create("lb://ydsz-pmis-cronjob"), uriMap.get("ydsz-pmis-cronjob"));
        assertEquals(URI.create("lb://ydsz-pmis-cronjob"), uriMap.get("ydsz-pmis-cronjob-v1"));
        assertEquals(URI.create("lb://ydsz-pmis-agent"), uriMap.get("ydsz-pmis-agent"));
        assertEquals(URI.create("lb://ydsz-pmis-agent"), uriMap.get("ydsz-pmis-agent-v1"));
    }

    @Test
    @DisplayName("正常场景：v1 路由包含 StripPrefix 过滤器，非 v1 路由无过滤器")
    void v1RoutesShouldHaveStripPrefixFilter() {
        RouteLocator locator = buildRouteLocator();
        List<Route> routes = locator.getRoutes().collectList().block();

        assertNotNull(routes);
        Map<String, Integer> filterCountMap = routes.stream()
                .collect(Collectors.toMap(Route::getId, r -> r.getFilters().size()));

        assertEquals(0, filterCountMap.get("ydsz-pmis-userinfo"));
        assertEquals(1, filterCountMap.get("ydsz-pmis-userinfo-v1"));
        assertEquals(0, filterCountMap.get("ydsz-pmis-project"));
        assertEquals(1, filterCountMap.get("ydsz-pmis-project-v1"));
        assertEquals(0, filterCountMap.get("ydsz-pmis-workflow"));
        assertEquals(1, filterCountMap.get("ydsz-pmis-workflow-v1"));
        assertEquals(0, filterCountMap.get("ydsz-pmis-cronjob"));
        assertEquals(1, filterCountMap.get("ydsz-pmis-cronjob-v1"));
        assertEquals(0, filterCountMap.get("ydsz-pmis-agent"));
        assertEquals(1, filterCountMap.get("ydsz-pmis-agent-v1"));
    }
}

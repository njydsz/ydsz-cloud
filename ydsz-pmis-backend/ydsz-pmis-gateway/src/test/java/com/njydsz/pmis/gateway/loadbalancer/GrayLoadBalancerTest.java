package com.njydsz.pmis.gateway.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GrayLoadBalancer 单元测试
 *
 * <p>覆盖：灰度标签路由、灰度实例降级、稳定实例选择、空实例列表、
 * supplier 为空、supplier 异常、无请求上下文等场景。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrayLoadBalancer 灰度负载均衡器测试")
class GrayLoadBalancerTest {

    @Mock
    private ObjectProvider<ServiceInstanceListSupplier> supplierProvider;

    @Mock
    private ServiceInstanceListSupplier supplier;

    private GrayLoadBalancer loadBalancer;

    private static final String SERVICE_ID = "test-service";

    private ServiceInstance grayInstance;
    private ServiceInstance stableInstance;
    private ServiceInstance noMetaInstance;

    @BeforeEach
    void setUp() {
        loadBalancer = new GrayLoadBalancer(supplierProvider, SERVICE_ID);
        grayInstance = new DefaultServiceInstance(
                "gray-1", SERVICE_ID, "localhost", 8080, false, Map.of("version", "gray"));
        stableInstance = new DefaultServiceInstance(
                "stable-1", SERVICE_ID, "localhost", 8081, false, Map.of("version", "stable"));
        noMetaInstance = new DefaultServiceInstance(
                "nometa-1", SERVICE_ID, "localhost", 8082, false, null);
    }

    @Test
    @DisplayName("正常场景：灰度请求只选择 version=gray 的实例")
    void grayTagShouldSelectGrayInstance() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(
                Flux.just(List.of(grayInstance, stableInstance)));

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(buildRequest("gray"));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.hasServer());
                    assertEquals("gray-1", response.getServer().getInstanceId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("正常场景：稳定请求只选择 version!=gray 的实例")
    void stableTagShouldSelectStableInstance() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(
                Flux.just(List.of(grayInstance, stableInstance)));

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(buildRequest("stable"));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.hasServer());
                    assertEquals("stable-1", response.getServer().getInstanceId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("正常场景：无灰度标签选择稳定实例")
    void noGrayTagShouldSelectStableInstance() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(
                Flux.just(List.of(grayInstance, stableInstance)));

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(buildRequest(null));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.hasServer());
                    assertEquals("stable-1", response.getServer().getInstanceId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("正常场景：灰度实例不存在时降级到全量实例")
    void grayTagFallbackToAllInstancesWhenNoGrayAvailable() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(
                Flux.just(List.of(stableInstance, noMetaInstance)));

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(buildRequest("gray"));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.hasServer());
                    ServiceInstance selected = response.getServer();
                    assertNotNull(selected);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("正常场景：无 metadata 的实例被视为稳定实例")
    void noMetadataInstanceShouldBeTreatedAsStable() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(
                Flux.just(List.of(noMetaInstance, grayInstance)));

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(buildRequest("stable"));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.hasServer());
                    assertEquals("nometa-1", response.getServer().getInstanceId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("异常场景：实例列表为空返回 EmptyResponse")
    void emptyInstanceListShouldReturnEmptyResponse() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(Flux.just(List.of()));

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(buildRequest("gray"));

        StepVerifier.create(result)
                .assertNext(response -> assertFalse(response.hasServer()))
                .verifyComplete();
    }

    @Test
    @DisplayName("异常场景：supplier 为空返回 EmptyResponse")
    void nullSupplierShouldReturnEmptyResponse() {
        when(supplierProvider.getIfAvailable()).thenReturn(null);

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(buildRequest("gray"));

        StepVerifier.create(result)
                .assertNext(response -> assertFalse(response.hasServer()))
                .verifyComplete();
    }

    @Test
    @DisplayName("异常场景：supplier 抛出异常返回 EmptyResponse")
    void supplierErrorShouldReturnEmptyResponse() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(Flux.error(new RuntimeException("connection refused")));

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(buildRequest("gray"));

        StepVerifier.create(result)
                .assertNext(response -> assertFalse(response.hasServer()))
                .verifyComplete();
    }

    @Test
    @DisplayName("正常场景：choose() 无参方法等效于 choose(null)")
    void chooseNoArgsShouldWorkAsChooseNull() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(
                Flux.just(List.of(grayInstance, stableInstance)));

        Mono<Response<ServiceInstance>> result = loadBalancer.choose();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.hasServer());
                    assertEquals("stable-1", response.getServer().getInstanceId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("正常场景：exchange attribute 中的灰度标识作为回退来源")
    void grayTagFromAttributeShouldBeUsedAsFallback() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(
                Flux.just(List.of(grayInstance, stableInstance)));

        HttpHeaders headers = new HttpHeaders();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GrayLoadBalancer.GRAY_TAG_HEADER, "gray");
        RequestData data = mock(RequestData.class);
        when(data.getHeaders()).thenReturn(headers);
        when(data.getAttributes()).thenReturn(attributes);
        RequestDataContext context = mock(RequestDataContext.class);
        when(context.getClientRequest()).thenReturn(data);
        @SuppressWarnings("rawtypes")
        Request request = mock(Request.class);
        when(request.getContext()).thenReturn(context);

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.hasServer());
                    assertEquals("gray-1", response.getServer().getInstanceId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("正常场景：无请求上下文（context 非 RequestDataContext）选择稳定实例")
    void nonRequestDataContextShouldSelectStableInstance() {
        when(supplierProvider.getIfAvailable()).thenReturn(supplier);
        when(supplier.get(any())).thenReturn(
                Flux.just(List.of(grayInstance, stableInstance)));

        @SuppressWarnings("rawtypes")
        Request request = mock(Request.class);
        when(request.getContext()).thenReturn("not-a-request-data-context");

        Mono<Response<ServiceInstance>> result = loadBalancer.choose(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.hasServer());
                    assertEquals("stable-1", response.getServer().getInstanceId());
                })
                .verifyComplete();
    }

    /**
     * 构造带灰度标签请求头的 Request
     *
     * @param grayTag 灰度标签（gray / stable / null）
     * @return 负载均衡请求
     */
    @SuppressWarnings("rawtypes")
    private Request buildRequest(String grayTag) {
        if (grayTag == null) {
            Request request = mock(Request.class);
            when(request.getContext()).thenReturn(null);
            return request;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(GrayLoadBalancer.GRAY_TAG_HEADER, grayTag);
        RequestData data = mock(RequestData.class);
        when(data.getHeaders()).thenReturn(headers);
        RequestDataContext context = mock(RequestDataContext.class);
        when(context.getClientRequest()).thenReturn(data);
        Request request = mock(Request.class);
        when(request.getContext()).thenReturn(context);
        return request;
    }
}

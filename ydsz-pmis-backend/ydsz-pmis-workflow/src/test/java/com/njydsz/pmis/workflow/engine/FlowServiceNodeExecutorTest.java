package com.njydsz.pmis.workflow.engine;

import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FlowServiceNodeExecutor 单元测试
 *
 * <p>覆盖服务节点执行器的核心分支：AUTO_PASS 自动通过、未知类型默认通过、
 * ext 解析异常回退、SCRIPT 脚本执行（Boolean/null/非 Boolean/编译异常/沙箱限制）、
 * HTTP 节点 url 校验。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>AUTO_PASS：serviceType=AUTO_PASS → success=true</li>
 *   <li>未知 serviceType：默认自动通过（success=true）</li>
 *   <li>ext 为 null / 空字符串 / 非法 JSON：parseExtConfig 回退空 Map，默认 AUTO_PASS</li>
 *   <li>SCRIPT 返回 Boolean true：a > b，{a:3,b:2} → success=true</li>
 *   <li>SCRIPT 返回 Boolean false：a > b，{a:2,b:3} → success=false</li>
 *   <li>SCRIPT 返回 null（nil）：视为成功</li>
 *   <li>SCRIPT 返回非 Boolean（字符串）：视为成功</li>
 *   <li>SCRIPT 未配置 script：success=false</li>
 *   <li>SCRIPT 语法错误：success=false，message 含异常信息</li>
 *   <li>SCRIPT 沙箱限制：禁用 NewInstance，new 对象被拒绝 → success=false</li>
 *   <li>HTTP url 为空 / 缺失 / "null" 字符串：success=false</li>
 * </ul>
 *
 * <p><b>未覆盖场景</b>：HTTP 调用成功/失败/异常（serviceType=HTTP 且 url 非法）。
 * 原因：被测类内部 {@code new RestTemplate()}，无法直接 mock；
 * 后续可通过引入 MockRestServiceServer 或重构为注入式 RestTemplate 补充。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class FlowServiceNodeExecutorTest {

    private FlowServiceNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FlowServiceNodeExecutor();
    }

    // ============ 辅助方法 ============

    /**
     * 构建服务节点，指定 ext JSON
     */
    private FlowNodeDO buildNode(String ext) {
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("SVC_001");
        node.setExt(ext);
        return node;
    }

    // ============ AUTO_PASS 场景 ============

    @Test
    @DisplayName("AUTO_PASS 类型：返回 success=true")
    void autoPassShouldReturnSuccess() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"AUTO_PASS\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("自动通过");
    }

    @Test
    @DisplayName("未知 serviceType：默认自动通过（success=true）")
    void unknownServiceTypeShouldDefaultAutoPass() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"UNKNOWN\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("未知服务类型(UNKNOWN)");
    }

    @Test
    @DisplayName("ext 为 null：parseExtConfig 返回空 Map，默认 AUTO_PASS")
    void nullExtShouldDefaultAutoPass() {
        FlowNodeDO node = buildNode(null);

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("自动通过");
    }

    @Test
    @DisplayName("ext 为空字符串：parseExtConfig 返回空 Map，默认 AUTO_PASS")
    void emptyExtShouldDefaultAutoPass() {
        FlowNodeDO node = buildNode("");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("自动通过");
    }

    @Test
    @DisplayName("ext 为非法 JSON：parseExtConfig 捕获异常返回空 Map，默认 AUTO_PASS")
    void invalidJsonExtShouldDefaultAutoPass() {
        FlowNodeDO node = buildNode("not a json");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("自动通过");
    }

    // ============ SCRIPT 场景 ============

    @Test
    @DisplayName("SCRIPT 返回 Boolean true：a > b，{a:3,b:2} → success=true")
    void scriptBooleanTrueShouldSuccess() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"SCRIPT\",\"script\":\"a > b\"}");
        Map<String, Object> variables = new HashMap<>();
        variables.put("a", 3);
        variables.put("b", 2);

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, variables);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("脚本结果: true");
    }

    @Test
    @DisplayName("SCRIPT 返回 Boolean false：a > b，{a:2,b:3} → success=false")
    void scriptBooleanFalseShouldFail() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"SCRIPT\",\"script\":\"a > b\"}");
        Map<String, Object> variables = new HashMap<>();
        variables.put("a", 2);
        variables.put("b", 3);

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, variables);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("脚本结果: false");
    }

    @Test
    @DisplayName("SCRIPT 返回 null（nil）：按源码实际行为视为成功")
    void scriptNullReturnShouldSuccess() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"SCRIPT\",\"script\":\"nil\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        // 源码：result == null → new ServiceExecutionResult(true, "脚本执行完成")
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("脚本执行完成");
    }

    @Test
    @DisplayName("SCRIPT 返回非 Boolean（字符串 'hello'）：按源码实际行为视为成功")
    void scriptNonBooleanReturnShouldSuccess() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"SCRIPT\",\"script\":\"'hello'\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        // 源码：非 Boolean 结果视为成功，message = "脚本结果: " + result
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("脚本结果: hello");
    }

    @Test
    @DisplayName("SCRIPT 未配置 script：success=false")
    void scriptEmptyShouldFail() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"SCRIPT\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("SCRIPT 节点未配置 script");
    }

    @Test
    @DisplayName("SCRIPT 语法错误：success=false，message 含异常信息")
    void scriptSyntaxErrorShouldFail() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"SCRIPT\",\"script\":\"a >\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).startsWith("脚本执行异常:");
    }

    @Test
    @DisplayName("SCRIPT 沙箱限制：禁用 NewInstance，new 对象应被拒绝（success=false）")
    void scriptSandboxNewInstanceShouldBeRejected() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"SCRIPT\",\"script\":\"new java.util.HashMap()\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        // 源码构造器禁用 Feature.NewInstance，编译阶段抛异常被 catch 捕获
        assertThat(result.success()).isFalse();
        assertThat(result.message()).startsWith("脚本执行异常:");
    }

    // ============ HTTP 场景 ============

    @Test
    @DisplayName("HTTP url 为空字符串：success=false")
    void httpEmptyUrlShouldFail() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"HTTP\",\"url\":\"\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("HTTP 服务节点未配置 url");
    }

    @Test
    @DisplayName("HTTP 未配置 url 字段：success=false")
    void httpMissingUrlShouldFail() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"HTTP\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("HTTP 服务节点未配置 url");
    }

    @Test
    @DisplayName("HTTP url 为 'null' 字符串：success=false")
    void httpNullStringUrlShouldFail() {
        FlowNodeDO node = buildNode("{\"serviceType\":\"HTTP\",\"url\":\"null\"}");

        FlowServiceNodeExecutor.ServiceExecutionResult result = executor.execute(node, new HashMap<>());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("HTTP 服务节点未配置 url");
    }
}

package com.njydsz.pmis.cronjob.core.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DagDefinitionCodec} 单元测试（P2 DAG 增强）。
 *
 * <p>覆盖 {@code toJson} 序列化、{@code fromJson} 反序列化及全部校验分支。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DagDefinitionCodec DAG 定义编解码器测试")
class DagDefinitionCodecTest {

    private final DagDefinitionCodec codec = new DagDefinitionCodec();

    // ==================== toJson ====================

    @Test
    @DisplayName("toJson null 返回 null")
    void toJson_null_returnsNull() {
        assertNull(codec.toJson(null));
    }

    @Test
    @DisplayName("toJson 正常定义生成正确 JSON")
    void toJson_normalDefinition_correctJson() {
        DagNode nodeA = DagNode.of("key-a", "job-1", "抽取", 100, 200, "{}");
        DagNode nodeB = DagNode.of("key-b", "job-2", "清洗");
        DagEdge edge = new DagEdge("key-a", "key-b", "FAIL_FAST", null);
        DagDefinition def = new DagDefinition(List.of(nodeA, nodeB), List.of(edge));

        String json = codec.toJson(def);
        assertNotNull(json);

        JSONObject root = JSON.parseObject(json);
        JSONArray nodes = root.getJSONArray("nodes");
        assertEquals(2, nodes.size());

        JSONObject n0 = nodes.getJSONObject(0);
        assertEquals("key-a", n0.getString("jobKey"));
        assertEquals("job-1", n0.getString("jobId"));
        assertEquals("抽取", n0.getString("label"));
        assertEquals(100, n0.getIntValue("x", 0));
        assertEquals(200, n0.getIntValue("y", 0));
        assertEquals("{}", n0.getString("paramsJson"));

        JSONArray edges = root.getJSONArray("edges");
        assertEquals(1, edges.size());
        JSONObject e0 = edges.getJSONObject(0);
        assertEquals("key-a", e0.getString("from"));
        assertEquals("key-b", e0.getString("to"));
        assertEquals("FAIL_FAST", e0.getString("failStrategy"));
        assertNull(e0.getString("condition"));
    }

    // ==================== fromJson 校验异常 ====================

    @Test
    @DisplayName("fromJson null 抛 BizException(msg_dag_definition_empty)")
    void fromJson_null_throwsBizException() {
        BizException ex = assertThrows(BizException.class, () -> codec.fromJson(null));
        assertEquals("error.cronjob.msg_dag_definition_empty", ex.getErrorMessage());
    }

    @Test
    @DisplayName("fromJson 空白字符串抛 BizException(msg_dag_definition_empty)")
    void fromJson_blank_throwsBizException() {
        BizException ex = assertThrows(BizException.class, () -> codec.fromJson("   "));
        assertEquals("error.cronjob.msg_dag_definition_empty", ex.getErrorMessage());
    }

    @Test
    @DisplayName("fromJson 非法 JSON 抛 BizException(msg_dag_definition_invalid)")
    void fromJson_invalidJson_throwsBizException() {
        BizException ex = assertThrows(BizException.class, () -> codec.fromJson("{invalid json"));
        assertEquals("error.cronjob.msg_dag_definition_invalid", ex.getErrorMessage());
    }

    @Test
    @DisplayName("fromJson nodes 为空抛 BizException(msg_dag_no_nodes)")
    void fromJson_emptyNodes_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> codec.fromJson("{\"nodes\":[],\"edges\":[]}"));
        assertEquals("error.cronjob.msg_dag_no_nodes", ex.getErrorMessage());
    }

    @Test
    @DisplayName("fromJson 节点 jobKey 缺失抛 BizException(msg_dag_node_key_missing)")
    void fromJson_nodeKeyMissing_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> codec.fromJson("{\"nodes\":[{\"jobId\":\"1\",\"label\":\"a\"}],\"edges\":[]}"));
        assertEquals("error.cronjob.msg_dag_node_key_missing", ex.getErrorMessage());
    }

    @Test
    @DisplayName("fromJson 节点 jobKey 重复抛 BizException(msg_dag_node_key_duplicate)")
    void fromJson_nodeKeyDuplicate_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> codec.fromJson("{\"nodes\":[{\"jobKey\":\"a\",\"jobId\":\"1\"},"
                        + "{\"jobKey\":\"a\",\"jobId\":\"2\"}],\"edges\":[]}"));
        assertEquals("error.cronjob.msg_dag_node_key_duplicate", ex.getErrorMessage());
    }

    @Test
    @DisplayName("fromJson 边 from 缺失抛 BizException(msg_dag_edge_invalid)")
    void fromJson_edgeFromMissing_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> codec.fromJson("{\"nodes\":[{\"jobKey\":\"a\",\"jobId\":\"1\"}],"
                        + "\"edges\":[{\"to\":\"a\"}]}"));
        assertEquals("error.cronjob.msg_dag_edge_invalid", ex.getErrorMessage());
    }

    @Test
    @DisplayName("fromJson 边引用不存在节点抛 BizException(msg_dag_edge_node_not_found)")
    void fromJson_edgeNodeNotFound_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> codec.fromJson("{\"nodes\":[{\"jobKey\":\"a\",\"jobId\":\"1\"}],"
                        + "\"edges\":[{\"from\":\"a\",\"to\":\"b\"}]}"));
        assertEquals("error.cronjob.msg_dag_edge_node_not_found", ex.getErrorMessage());
    }

    // ==================== fromJson 正常解析 ====================

    @Test
    @DisplayName("fromJson 合法定义正确解析（含节点和边）")
    void fromJson_validDefinition_correctParsed() {
        String json = "{\"nodes\":[{\"jobKey\":\"a\",\"jobId\":\"1\",\"label\":\"抽取\","
                + "\"x\":100,\"y\":200,\"paramsJson\":\"{}\"},"
                + "{\"jobKey\":\"b\",\"jobId\":\"2\",\"label\":\"清洗\"}],"
                + "\"edges\":[{\"from\":\"a\",\"to\":\"b\",\"failStrategy\":\"FAIL_FAST\","
                + "\"condition\":null}]}";

        DagDefinition def = assertDoesNotThrow(() -> codec.fromJson(json));
        assertEquals(2, def.nodeCount());

        // 校验节点 a（完整字段）
        DagNode nodeA = def.findNode("a");
        assertNotNull(nodeA);
        assertEquals("1", nodeA.jobId());
        assertEquals("抽取", nodeA.label());
        assertEquals(100, nodeA.x());
        assertEquals(200, nodeA.y());
        assertEquals("{}", nodeA.paramsJson());

        // 校验节点 b（缺省 x/y/paramsJson）
        DagNode nodeB = def.findNode("b");
        assertNotNull(nodeB);
        assertEquals("2", nodeB.jobId());
        assertEquals("清洗", nodeB.label());
        assertEquals(0, nodeB.x());
        assertEquals(0, nodeB.y());
        assertNull(nodeB.paramsJson());

        // 校验边
        List<DagEdge> outEdges = def.outgoingEdges("a");
        assertEquals(1, outEdges.size());
        DagEdge edge = outEdges.get(0);
        assertEquals("a", edge.from());
        assertEquals("b", edge.to());
        assertEquals("FAIL_FAST", edge.failStrategy());
        assertNull(edge.condition());

        // 校验图结构方法
        assertEquals(1, def.incomingEdges("b").size());
        assertEquals(1, def.rootNodes().size());
        assertEquals("a", def.rootNodes().get(0).jobKey());
    }

    @Test
    @DisplayName("fromJson 无边定义合法解析（无边也合法）")
    void fromJson_noEdges_success() {
        String json = "{\"nodes\":[{\"jobKey\":\"a\",\"jobId\":\"1\",\"label\":\"节点A\"}]}";

        DagDefinition def = assertDoesNotThrow(() -> codec.fromJson(json));
        assertEquals(1, def.nodeCount());
        assertNotNull(def.findNode("a"));
        assertTrue(def.edges().isEmpty());
        assertEquals(1, def.rootNodes().size());
    }

    @Test
    @DisplayName("toJson -> fromJson 往返测试")
    void toJson_thenFromJson_roundTrip() {
        DagNode nodeA = DagNode.of("key-a", "job-1", "抽取", 100, 200, "{\"k\":\"v\"}");
        DagNode nodeB = DagNode.of("key-b", "job-2", "清洗", 300, 200);
        DagNode nodeC = DagNode.of("key-c", "job-3", "加载");
        DagEdge edgeAB = new DagEdge("key-a", "key-b", "FAIL_FAST", null);
        DagEdge edgeBC = DagEdge.of("key-b", "key-c", "CONTINUE_ON_FAIL");
        DagDefinition original = new DagDefinition(
                List.of(nodeA, nodeB, nodeC),
                List.of(edgeAB, edgeBC));

        String json = codec.toJson(original);
        DagDefinition roundTripped = codec.fromJson(json);

        assertEquals(original, roundTripped);
        assertEquals(3, roundTripped.nodeCount());
        assertEquals(2, roundTripped.edges().size());
    }
}

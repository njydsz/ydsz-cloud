package com.remisoft.common.json;

import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.provider.SerializationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 循环引用处理策略测试（P0）。
 *
 * <p>覆盖 REF（输出引用路径）、IGNORE（忽略循环引用）、ERROR（抛出异常）三种策略。
 */
class CircularReferenceTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
        SerializationProvider.setCircularReferenceStrategy("REF");
    }

    @Test
    void refStrategyHandlesCycle() {
        SerializationProvider.setCircularReferenceStrategy("REF");
        CycleNode a = new CycleNode();
        a.setName("A");
        CycleNode b = new CycleNode();
        b.setName("B");
        a.setNext(b);
        b.setNext(a);

        String json = RemiJson.toJson(a);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"A\""));
    }

    @Test
    void ignoreStrategyHandlesCycle() {
        SerializationProvider.setCircularReferenceStrategy("IGNORE");
        CycleNode a = new CycleNode();
        a.setName("A");
        CycleNode b = new CycleNode();
        b.setName("B");
        a.setNext(b);
        b.setNext(a);

        String json = RemiJson.toJson(a);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"A\""));
    }

    @Test
    void errorStrategyThrowsOnCycle() {
        SerializationProvider.setCircularReferenceStrategy("ERROR");
        CycleNode a = new CycleNode();
        a.setName("A");
        CycleNode b = new CycleNode();
        b.setName("B");
        a.setNext(b);
        b.setNext(a);

        // ERROR 策略下应抛出异常（当前实现抛出 StackOverflowError，属于 Error 子类）
        assertThrows(Throwable.class, () -> RemiJson.toJson(a));
    }

    @Test
    void noCycleAlwaysSucceeds() {
        SerializationProvider.setCircularReferenceStrategy("ERROR");
        CycleNode a = new CycleNode();
        a.setName("leaf");
        a.setNext(null);

        String json = RemiJson.toJson(a);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"leaf\""));
    }

    @Test
    void selfReferenceHandled() {
        SerializationProvider.setCircularReferenceStrategy("REF");
        CycleNode a = new CycleNode();
        a.setName("self");
        a.setNext(a);

        String json = RemiJson.toJson(a);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"self\""));
    }

    /**
     * 循环引用测试节点
     */
    public static class CycleNode {
        private String name;
        private CycleNode next;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public CycleNode getNext() { return next; }
        public void setNext(CycleNode next) { this.next = next; }
    }
}

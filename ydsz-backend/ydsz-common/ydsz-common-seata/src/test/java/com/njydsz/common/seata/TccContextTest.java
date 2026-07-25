package com.njydsz.common.seata;

import org.junit.jupiter.api.Test;

import com.njydsz.common.seata.api.TccContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TccContext} 单元测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class TccContextTest {

    @Test
    void testPutAndGet() {
        TccContext ctx = new TccContext("xid-001", "branch-001");
        ctx.put("key1", "value1");
        ctx.put("key2", 42);

        assertEquals("xid-001", ctx.getXid());
        assertEquals("branch-001", ctx.getBranchId());
        assertEquals("value1", ctx.getString("key1"));
        assertEquals("42", ctx.getString("key2"));
        assertEquals(42L, ctx.getLong("key2"));
    }

    @Test
    void testGetLongReturnsNullForInvalidValue() {
        TccContext ctx = new TccContext("xid", "branch");
        ctx.put("invalid", "not-a-number");

        Long result = ctx.getLong("invalid");
        assertNull(result, "getLong should return null for non-numeric value");
    }

    @Test
    void testGetLongReturnsNullForMissingKey() {
        TccContext ctx = new TccContext("xid", "branch");
        assertNull(ctx.getLong("nonexistent"));
    }

    @Test
    void testGetAllReturnsImmutableSnapshot() {
        TccContext ctx = new TccContext("xid", "branch");
        ctx.put("a", 1);
        ctx.put("b", 2);

        var snapshot = ctx.getAll();
        assertEquals(2, snapshot.size());
        assertEquals(1, snapshot.get("a"));

        // 修改原始 context 不影响快照
        ctx.put("c", 3);
        assertEquals(2, snapshot.size());

        // 快照不可修改
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", 999));
    }

    @Test
    void testGetStringReturnsNullForMissingKey() {
        TccContext ctx = new TccContext("xid", "branch");
        assertNull(ctx.getString("nonexistent"));
    }
}

package com.njydsz.pmis.common.cache.internal.lfu;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class FrequencySketch {

    private static final long[] SEEDS = {
            0xc3a5c85c97cb3127L,
            0xb492b66fbe98f273L,
            0x9ae16a3b2f90404fL,
            0xcbf29ce484222325L
    };

    private static final VarHandle TABLE_VARHANDLE;

    static {
        try {
            TABLE_VARHANDLE = MethodHandles.arrayElementVarHandle(long[].class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile long[] table;
    private int resetMask;
    private int counterMask = 0xf;
    private int maxCount = 15;
    private long resetHalveMask = 0x7777777777777777L;
    private int counterShift = 2;

    public FrequencySketch() {
        ensureCapacity(1024);
    }

    public void setBitSize(int bitSize) {
        if (bitSize == 4) {
            counterMask = 0xf;
            maxCount = 15;
            resetHalveMask = 0x7777777777777777L;
            counterShift = 2;
        } else if (bitSize == 8) {
            counterMask = 0xff;
            maxCount = 255;
            resetHalveMask = 0x7f7f7f7f7f7f7f7fL;
            counterShift = 3;
        } else {
            throw new IllegalArgumentException("不支持位宽: " + bitSize + "，仅支持 4 或 8");
        }
    }

    public void ensureCapacity(int maximum) {
        int count = maximumSize(maximum);
        if (table != null && table.length >= count) {
            return;
        }
        int size = Integer.highestOneBit(count - 1);
        if (size < count) {
            size <<= 1;
        }
        size = Math.max(64, size);
        table = new long[size];
        resetMask = size - 1;
    }

    private int maximumSize(long maximum) {
        long count = (long) (maximum / 64.0);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1024, count));
    }

    public void increment(Object e) {
        int start = hash(e) & resetMask;
        int increment = hash2(e) & resetMask;

        for (int i = 0; i < 4; i++) {
            int index = (start + i * increment) & resetMask;
            int offset = (index & 3) << counterShift;
            long slot;
            int count;
            do {
                slot = (long) TABLE_VARHANDLE.getVolatile(table, index >>> counterShift);
                count = (int) ((slot >>> offset) & counterMask);
                if (count >= maxCount) {
                    break;
                }
            } while (!TABLE_VARHANDLE.compareAndSet(table, index >>> counterShift, slot, slot + (1L << offset)));
        }
    }

    public int frequency(Object e) {
        int start = hash(e) & resetMask;
        int increment = hash2(e) & resetMask;

        int min = Integer.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            int index = (start + i * increment) & resetMask;
            int offset = (index & 3) << counterShift;
            long slot = (long) TABLE_VARHANDLE.getVolatile(table, index >>> counterShift);
            int count = (int) ((slot >>> offset) & counterMask);
            if (count < min) {
                min = count;
            }
        }
        return min;
    }

    public void reset() {
        long[] currentTable = table;
        for (int i = 0; i < currentTable.length; i++) {
            long slot;
            do {
                slot = (long) TABLE_VARHANDLE.getVolatile(currentTable, i);
            } while (!TABLE_VARHANDLE.compareAndSet(currentTable, i, slot, (slot >>> 1) & resetHalveMask));
        }
    }

    private int hash(Object e) {
        int hash = e == null ? 0 : e.hashCode();
        hash = (hash ^ (int)(SEEDS[0] & 0xffffffffL)) * 0x85ebca6b;
        hash = hash ^ (hash >>> 13);
        return hash;
    }

    private int hash2(Object e) {
        int hash = e == null ? 0 : e.hashCode();
        hash = (hash ^ (int)(SEEDS[1] & 0xffffffffL)) * 0xc2b2ae35;
        hash = hash ^ (hash >>> 16);
        return hash;
    }
}
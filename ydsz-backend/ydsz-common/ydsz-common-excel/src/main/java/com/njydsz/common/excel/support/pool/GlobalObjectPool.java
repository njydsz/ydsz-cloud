package com.njydsz.common.excel.support.pool;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局对象。- 通用对象复用组件
 *
 * <p>提供对象的借用与归还机制，实现对象的复用，减少 GC 压力。
 * 采用单例模式管理多个类型特定的对象池。/p>
 *
 * <h3>线程安全优化</h3>
 * <ul>
 *   <li>使用 ConcurrentLinkedQueue 替代 ArrayList + synchronized</li>
 *   <li>borrow/return 均为无锁操作，基。CAS 实现</li>
 * </ul>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>通用对象。- 支持任意类型对象的借还管理</li>
 *   <li>字符串池 - 字符。intern 优化</li>
 *   <li>日期。- Date 对象缓存复用</li>
 * </ul>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>对象池模。- 复用对象减少分配开销</li>
 *   <li>单例模式 - 全局统一管理</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 借用对象
 * User user = GlobalObjectPool.borrowObject(User.class);
 * try {
 *     // 使用对象
 *     user.setName("张三");
 * } finally {
 *     // 归还对象
 *     GlobalObjectPool.returnObject(User.class, user);
 * }
 *
 * // 字符。intern
 * String pooledStr = GlobalObjectPool.StringPool.intern("常用字符）;
 * }</pre>
 *
 * @see ObjectPool
 * @see StylePool
 * @see ReflectCache
 */
public class GlobalObjectPool {

    /** 每个类型池的最大容。*/
    private static final int POOL_SIZE = 1000;

    /** 类型到对象池的映射*/
    private static final Map<Class<?>, TypeObjectPool<?>> POOLS = new ConcurrentHashMap<>();

    /**
     * 从池中借用对象
     *
     * <p>如果池中有可用对象则直接返回，否则创建新对象</p>
     *
     * @param clazz 对象类型
     * @param <T> 泛型参数
     * @return 借用的对。
     */
    public static <T> T borrowObject(Class<T> clazz) {
        TypeObjectPool<T> pool = (TypeObjectPool<T>) POOLS.computeIfAbsent(clazz, k -> new TypeObjectPool<>(clazz));
        return pool.borrow();
    }

    /**
     * 归还对象到池。
     *
     * <p>如果池未满则加入池中等待复用，否则丢。/p>
     *
     * @param clazz 对象类型
     * @param obj 要归还的对象
     * @param <T> 泛型参数
     */
    public static <T> void returnObject(Class<T> clazz, T obj) {
        TypeObjectPool<T> pool = (TypeObjectPool<T>) POOLS.get(clazz);
        if (pool != null) {
            pool.returnObject(obj);
        }
    }

    /**
     * 清空所有对象池
     */
    public static void clearAll() {
        POOLS.values().forEach(pool -> pool.clear());
    }

    /**
     * 通用对象池实体
     *
     * <p>使用 ConcurrentLinkedQueue 管理可用对象列表，支持无锁借用与归。/p>
     *
     * @param <T> 对象类型
     */
    private static class TypeObjectPool<T> {
        private final Class<T> clazz;
        private final ConcurrentLinkedQueue<T> available;
        private final AtomicInteger totalCreated;
        private final AtomicInteger borrowed;

        TypeObjectPool(Class<T> clazz) {
            this.clazz = clazz;
            this.available = new ConcurrentLinkedQueue<>();
            this.totalCreated = new AtomicInteger(0);
            this.borrowed = new AtomicInteger(0);
        }

        /**
         * 借用对象
         *
         * <p>优先从可用队列返回，队列为空时创建新对象</p>
         *
         * @return 对象实例
         */
        T borrow() {
            T obj = available.poll();
            if (obj != null) {
                borrowed.incrementAndGet();
                return obj;
            }

            if (totalCreated.get() < POOL_SIZE) {
                try {
                    T newObj = clazz.getDeclaredConstructor().newInstance();
                    totalCreated.incrementAndGet();
                    borrowed.incrementAndGet();
                    return newObj;
                } catch (Exception e) {
                    return null;
                }
            }

            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * 归还对象
         *
         * @param obj 要归还的对象
         */
        void returnObject(T obj) {
            if (obj == null) return;

            available.offer(obj);
            borrowed.decrementAndGet();
        }

        /**
         * 清空可用对象列表
         */
        void clear() {
            available.clear();
        }
    }

    /**
     * 字符串池 - 提供字符。intern 优化
     *
     * <p>用于缓存常用字符串，减少内存占用</p>
     */
    public static final class StringPool {
        private static final Map<String, String> POOL = new ConcurrentHashMap<>();
        private static final int MAX_POOL_SIZE = 5000;
        private static final AtomicInteger count = new AtomicInteger(0);

        /**
         * 返回池中的字符串，如果不存在则添。
         *
         * <p>如果池已满，直接返回原字符串</p>
         *
         * @param str 原始字符。
         * @return 池中的字符串（可能是相同的引用）
         */
        public static String intern(String str) {
            if (str == null) return null;
            String pooled = POOL.get(str);
            if (pooled != null) {
                return pooled;
            }

            if (count.get() >= MAX_POOL_SIZE) {
                return str;
            }

            String existing = POOL.putIfAbsent(str, str);
            if (existing != null) {
                return existing;
            }
            count.incrementAndGet();
            return str;
        }

        /**
         * 清空字符串池
         */
        public static void clear() {
            POOL.clear();
            count.set(0);
        }
    }

    /**
     * 日期。- 提供 Date 对象缓存
     *
     * <p>用于缓存常用时间。Date 对象</p>
     */
    public static final class DatePool {
        private static final Map<Long, Date> POOL = new ConcurrentHashMap<>();
        private static final int MAX_POOL_SIZE = 5000;
        private static final AtomicInteger count = new AtomicInteger(0);

        public static Date intern(Date date) {
            if (date == null) return null;
            long key = date.getTime();
            Date pooled = POOL.get(key);
            if (pooled != null) {
                return pooled;
            }

            if (count.get() >= MAX_POOL_SIZE) {
                return date;
            }

            Date existing = POOL.putIfAbsent(key, date);
            if (existing == null) {
                count.incrementAndGet();
            }
            return existing != null ? existing : date;
        }

        public static Date getDate(long time) {
            Date pooled = POOL.get(time);
            if (pooled != null) {
                return pooled;
            }

            if (count.get() >= MAX_POOL_SIZE) {
                return new Date(time);
            }

            Date newDate = new Date(time);
            Date existing = POOL.putIfAbsent(time, newDate);
            if (existing == null) {
                count.incrementAndGet();
            }
            return existing != null ? existing : newDate;
        }

        public static void clear() {
            POOL.clear();
            count.set(0);
        }
    }

    /**
     * 清空所有池，包括字符串池、日期池和对象池
     */
    public static void clearAllPools() {
        StringPool.clear();
        DatePool.clear();
        clearAll();
    }
}

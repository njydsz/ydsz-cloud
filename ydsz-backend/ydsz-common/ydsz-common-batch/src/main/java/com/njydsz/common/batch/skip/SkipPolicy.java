package com.njydsz.common.batch.skip;

import java.util.HashSet;
import java.util.Set;

/**
 * 跳过策略
 *
 * <p>定义哪些异常可跳过、累计可跳过次数上限。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SkipPolicy {

    private final int maxSkipCount;
    private final Set<Class<? extends Throwable>> skippableExceptions;

    public SkipPolicy() {
        this(Integer.MAX_VALUE, new HashSet<>());
    }

    public SkipPolicy(int maxSkipCount, Set<Class<? extends Throwable>> skippableExceptions) {
        this.maxSkipCount = maxSkipCount;
        this.skippableExceptions = skippableExceptions == null
                ? new HashSet<>() : skippableExceptions;
    }

    /**
     * 判断异常是否可跳过
     */
    public boolean shouldSkip(Throwable ex, long currentSkipCount) {
        if (currentSkipCount >= maxSkipCount) {
            return false;
        }
        if (skippableExceptions.isEmpty()) {
            return true;
        }
        Throwable t = ex;
        while (t != null) {
            for (Class<? extends Throwable> exClass : skippableExceptions) {
                if (exClass.isInstance(t)) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }
}

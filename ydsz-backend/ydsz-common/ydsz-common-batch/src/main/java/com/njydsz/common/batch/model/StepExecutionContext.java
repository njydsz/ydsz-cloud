package com.njydsz.common.batch.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 步骤执行上下文
 *
 * <p>在 Step 内部共享数据，可用于跨 ItemReader / ItemProcessor / ItemWriter 传递临时状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class StepExecutionContext {

    /** Job 名 */
    private String jobName;

    /** Step 名 */
    private String stepName;

    /** 提交间隔（默认 100） */
    private int commitInterval = 100;

    /** 用户数据 */
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 设置用户数据
     */
    public void put(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(key, value);
    }

    /**
     * 获取用户数据
     */
    public Object get(String key) {
        return attributes == null ? null : attributes.get(key);
    }
}

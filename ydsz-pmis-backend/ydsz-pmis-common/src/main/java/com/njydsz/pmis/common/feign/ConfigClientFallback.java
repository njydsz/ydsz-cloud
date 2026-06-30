package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ConfigClient 降级
 *
 * <p>远端不可用时直接返回空集合/空值，由 ThresholdProvider 走默认值兜底。
 *
 * @author ydsz-pmis-team
 */
@Slf4j
@Component
public class ConfigClientFallback implements FallbackFactory<ConfigClient> {

    @Override
    public ConfigClient create(Throwable cause) {
        log.warn("[ConfigClientFallback] 触发降级：{}", cause == null ? "unknown" : cause.toString());
        return new ConfigClient() {
            @Override
            public R<Map<String, String>> getGroup(String group) {
                return R.ok(Collections.emptyMap());
            }

            @Override
            public R<String> getValue(String group, String key) {
                return R.ok(null);
            }

            @Override
            public R<List<Map<String, Object>>> listPublic() {
                return R.ok(Collections.emptyList());
            }
        };
    }
}

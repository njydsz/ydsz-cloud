package com.njydsz.pmis.common.docs.security.pii;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.PiiFinding;

import lombok.extern.slf4j.Slf4j;

/**
 * PII 检测器组合实现
 * <p>
 * 聚合所有 {@link PiiDetector} 实现，对文档进行全量 PII 扫描。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Component
public class PiiDetectorComposite {

    private final List<PiiDetector> detectors;

    public PiiDetectorComposite(List<PiiDetector> detectors) {
        this.detectors = detectors != null ? detectors : java.util.List.of();
        log.info("[PiiDetectorComposite] 已注册 {} 个 PII 检测器: {}", detectors.size(),
                detectors.stream().map(d -> d.getSupportedType().name()).toList());
    }

    /**
     * 执行全量 PII 检测
     *
     * @param content 文档内容
     * @return 所有 PII 发现列表
     */
    public List<PiiFinding> detectAll(DocumentContent content) {
        if (content == null || content.getText() == null) {
            return List.of();
        }

        List<PiiFinding> allFindings = new ArrayList<>();
        for (PiiDetector detector : detectors) {
            try {
                List<PiiFinding> findings = detector.detect(content);
                if (findings != null) {
                    allFindings.addAll(findings);
                }
            } catch (Exception e) {
                log.error("[PiiDetectorComposite] 检测器 {} 执行失败", detector.getSupportedType(), e);
            }
        }

        return allFindings;
    }

    /**
     * 获取所有已注册的检测器
     *
     * @return 检测器列表
     */
    public List<PiiDetector> getDetectors() {
        return java.util.Collections.unmodifiableList(detectors);
    }
}

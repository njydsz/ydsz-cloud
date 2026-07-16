package com.njydsz.pmis.common.docs.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.config.DocsProperties;
import com.njydsz.pmis.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.pmis.common.docs.security.pii.PiiDetectorComposite;
import com.njydsz.pmis.common.docs.security.scanner.DocumentSecurityScannerComposite;
import com.njydsz.pmis.common.docs.service.AsyncDocumentParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档处理模块健康指标
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
public class DocsHealthIndicator implements HealthIndicator {

    private final DocumentParserRegistry parserRegistry;
    private final PiiDetectorComposite piiDetector;
    private final DocsProperties properties;
    private final AsyncDocumentParser asyncDocumentParser;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("enabled", properties.isEnabled());
        details.put("maxFileSizeMb", properties.getMaxFileSizeMb());
        details.put("supportedFormats", parserRegistry.getSupportedFormats());
        details.put("piiDetectors", piiDetector.getDetectors().stream()
                .map(d -> d.getSupportedType().name()).toList());
        try {
            details.put("asyncQueueSize", asyncDocumentParser.getQueueSize());
            details.put("asyncActiveCount", asyncDocumentParser.getActiveCount());
        } catch (Exception e) {
            details.put("asyncError", e.getMessage());
        }
        return Health.up().withDetails(details).build();
    }
}

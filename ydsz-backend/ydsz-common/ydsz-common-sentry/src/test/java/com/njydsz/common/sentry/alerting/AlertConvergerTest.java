package com.njydsz.common.sentry.alerting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;

import com.njydsz.common.sentry.spi.AlertPublisher;
/**
 * AlertConverger 单元测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("AlertConverger 告警收敛测试")
class AlertConvergerTest {

    private AlertEvent createAlert(String name, AlertSeverity severity) {
        return AlertEvent.builder()
                .name(name)
                .severity(severity)
                .summary("test alert")
                .labels(Map.of("job", "ydsz-service"))
                .build();
    }

    @Test
    @DisplayName("首次告警正常发布")
    void shouldPublishFirstAlert() {
        CountingPublisher delegate = new CountingPublisher();
        AlertConverger converger = new AlertConverger(delegate, 5000);
        boolean result = converger.publish(createAlert("test", AlertSeverity.P1));
        assertThat(result).isTrue();
        assertThat(delegate.getPublishCount()).isEqualTo(1);
        assertThat(converger.getTotalAlerts()).isEqualTo(1);
        assertThat(converger.getSuppressedAlerts()).isZero();
    }

    @Test
    @DisplayName("静默期内相同告警被抑制")
    void shouldSuppressDuplicateInSilencePeriod() {
        CountingPublisher delegate = new CountingPublisher();
        AlertConverger converger = new AlertConverger(delegate, 5000);
        converger.publish(createAlert("test", AlertSeverity.P1));
        converger.publish(createAlert("test", AlertSeverity.P1));
        converger.publish(createAlert("test", AlertSeverity.P1));
        assertThat(delegate.getPublishCount()).isEqualTo(1);
        assertThat(converger.getSuppressedAlerts()).isEqualTo(2);
        assertThat(converger.getSuppressionRate()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("不同 severity 的同名告警不会被错误抑制")
    void shouldNotSuppressDifferentSeverity() {
        CountingPublisher delegate = new CountingPublisher();
        AlertConverger converger = new AlertConverger(delegate, 5000);
        converger.publish(createAlert("test", AlertSeverity.P0));
        converger.publish(createAlert("test", AlertSeverity.P1));
        assertThat(delegate.getPublishCount()).isEqualTo(2);
        assertThat(converger.getSuppressedAlerts()).isZero();
    }

    @Test
    @DisplayName("静默期过后告警恢复正常发布")
    void shouldRecoverAfterSilencePeriod() throws InterruptedException {
        CountingPublisher delegate = new CountingPublisher();
        AlertConverger converger = new AlertConverger(delegate, 100);
        converger.publish(createAlert("test", AlertSeverity.P1));
        Thread.sleep(150);
        converger.publish(createAlert("test", AlertSeverity.P1));
        assertThat(delegate.getPublishCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("AlertEvent.dedupKey 包含 severity")
    void dedupKeyShouldIncludeSeverity() {
        AlertEvent p0 = createAlert("test", AlertSeverity.P0);
        AlertEvent p1 = createAlert("test", AlertSeverity.P1);
        assertThat(p0.dedupKey()).isNotEqualTo(p1.dedupKey());
    }

    static class CountingPublisher implements AlertPublisher {
        private int publishCount = 0;

        @Override
        public boolean publish(AlertEvent event) {
            publishCount++;
            return true;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getName() {
            return "counting";
        }

        int getPublishCount() {
            return publishCount;
        }
    }
}

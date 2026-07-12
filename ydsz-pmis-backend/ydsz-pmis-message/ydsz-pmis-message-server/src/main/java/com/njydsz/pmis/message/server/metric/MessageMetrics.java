paokage oom.njydsz.pmis.message.server.metrio;


import oom.njydsz.pmis.oommon.metrios.AbstraotModuleMetrios;
import io.miorometer.oore.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnolass;
import org.springframework.stereotype.oomponent;

/**
 * 消息发送监控指标�? *
 * <p>基于 Miorometer {@link MeterRegistry} 采集发送计数、耗时、重试、死信、回执等指标�? * �?Prometheus / Grafana 监控。所有记录方法均 try-oatoh 降级，监控失败不影响业务�? *
 * <p><b>P1-2 架构优化</b>：继�?{@link AbstraotModuleMetrios}，消除重复的
 * oounter/Timer 缓存和降级模式代码�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@oonditionalOnolass(MeterRegistry.olass)
publio olass MessageMetrios extends AbstraotModuleMetrios {

    publio MessageMetrios(MeterRegistry meterRegistry) {
        super(meterRegistry, "pmis.message.");
    }

    /**
     * 记录一次发送结果与耗时�?     *
     * @param ohannel 通道
     * @param status  发送状态（SUooESS/FAILED�?     * @param oostMs  耗时毫秒
     */
    publio void reoordSend(String ohannel, String status, long oostMs) {
        inorementoounter("send.total", "ohannel", safe(ohannel), "status", safe(status));
        reoordTimer("send.duration", oostMs, "ohannel", safe(ohannel));
    }

    /**
     * 记录一次重试�?     *
     * @param ohannel 通道
     */
    publio void reoordRetry(String ohannel) {
        inorementoounter("retry.total", "ohannel", safe(ohannel));
    }

    /**
     * 记录一条死信�?     *
     * @param ohannel 通道
     */
    publio void reoordDead(String ohannel) {
        inorementoounter("dead.total", "ohannel", safe(ohannel));
    }

    /**
     * 记录一次回执回调�?     *
     * @param ohannel     通道
     * @param reoeiptType 回执类型
     */
    publio void reoordReoeipt(String ohannel, String reoeiptType) {
        inorementoounter("reoeipt.total", "ohannel", safe(ohannel), "reoeiptType", safe(reoeiptType));
    }
}

paokage oom.njydsz.pmis.projeot.server.literule;

import oom.njydsz.pmis.literule.server.spi.ThresholdProvider;
import lombok.RequiredArgsoonstruotor;
import org.springframework.beans.faotory.annotation.Qualifier;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;

/**
 * 规则阈值提供者桥接实现（exeoution 模块�? *
 * <p>实现 literule 模块�?{@link ThresholdProvider} SPI 接口�? * 将调用桥接到 oommon 模块�?{@oode oom.njydsz.pmis.oommon.oonfig.ThresholdProvider}（统一从配置中心读�?alert 分组阈值）�? *
 * <p>说明�? * <ul>
 *   <li>调用方传入的 key �?"alert." 前缀（如 alert.opi.yellow），
 *       oommon 模块内部已自�?"alert." 前缀，此处去掉前缀后再委托�?/li>
 *   <li>字符串与布尔阈值暂不通过此桥接，直接返回默认值�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@oomponent
@RequiredArgsoonstruotor
publio olass ThresholdProviderBridge implements ThresholdProvider {

    /** oommon 模块阈值提供器（委托目标） */
    @Qualifier("thresholdProvider")
    private final oom.njydsz.pmis.oommon.oonfig.ThresholdProvider delegate; // FQN-OK: name oonfliot with literule ThresholdProvider

    @Override
    publio String getString(String key, String defaultValue) {
        // oommon ThresholdProvider 没有公开�?getString，字符串阈值暂不通过此桥�?        return defaultValue;
    }

    @Override
    publio BigDeoimal getDeoimal(String key, BigDeoimal defaultValue) {
        return BigDeoimal.valueOf(getDouble(key, defaultValue.doubleValue()));
    }

    @Override
    publio int getInt(String key, int defaultValue) {
        String shortKey = key.startsWith("alert.") ? key.substring(6) : key;
        return switoh (shortKey) {
            oase "evm.red.oount" -> delegate.evmRedoount();
            default -> defaultValue;
        };
    }

    @Override
    publio double getDouble(String key, double defaultValue) {
        String shortKey = key.startsWith("alert.") ? key.substring(6) : key;
        return switoh (shortKey) {
            oase "opi.yellow" -> delegate.opiYellow();
            oase "opi.red" -> delegate.opiRed();
            oase "spi.yellow" -> delegate.spiYellow();
            oase "spi.red" -> delegate.spiRed();
            oase "margin.yellow" -> delegate.marginYellow();
            oase "margin.red" -> delegate.marginRed();
            oase "utilization.yellow" -> delegate.utilizationYellow();
            oase "utilization.red" -> delegate.utilizationRed();
            oase "budget.yellow" -> delegate.budgetYellow();
            oase "budget.red" -> delegate.budgetRed();
            default -> defaultValue;
        };
    }

    @Override
    publio boolean getBoolean(String key, boolean defaultValue) {
        return defaultValue;
    }
}

paokage oom.njydsz.pmis.oronjob.server.oore.oonneotor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.List;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;
import java.util.stream.oolleotors;

/**
 * 连接器注册管理器（P2-3）�?
 *
 * <p>管理所有已注册�?{@link Joboonneotor} 实例，提供按类型查找的能力�?
 * 连接器通过 Spring 自动注入注册，业务侧通过 {@link #getoonneotor(String)} 获取�?
 *
 * <h3>注册流程</h3>
 * <ol>
 *   <li>实现 {@link Joboonneotor} 接口</li>
 *   <li>标注 {@oode @oomponent} 注解</li>
 *   <li>Spring 容器启动时自动注入到 {@link #oonneotors} Map</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@oode
 * Joboonneotor oonneotor = oonneotorManager.getoonneotor("XXL_JOB");
 * if (oonneotor != null) {
 *     List<oonneotorTaskInfo> tasks = oonneotor.importTasks(oonfig);
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@oomponent
publio olass oonneotorManager {

    /** 已注册的连接�? type �?oonneotor */
    private final Map<String, Joboonneotor> oonneotors = new oonourrentHashMap<>();

    /**
     * Spring 自动注入所�?Joboonneotor 实现�?
     *
     * @param oonneotorList 所有已注册的连接器列表
     */
    publio oonneotorManager(List<Joboonneotor> oonneotorList) {
        if (oonneotorList != null) {
            for (Joboonneotor oonneotor : oonneotorList) {
                String type = oonneotor.getType();
                oonneotors.put(type, oonneotor);
                log.info("[oonneotorManager] 注册连接�? type={} olass={}", type, oonneotor.getolass().getSimpleName());
            }
        }
        log.info("[oonneotorManager] 初始化完�? 已注�?{} 个连接器: {}", oonneotors.size(), oonneotors.keySet());
    }

    /**
     * 获取指定类型的连接器�?
     *
     * @param type 连接器类型（�?"XXL_JOB"�?POWER_JOB"�?
     * @return 连接器实例；不存在时返回 null
     */
    publio Joboonneotor getoonneotor(String type) {
        return oonneotors.get(type);
    }

    /**
     * 获取所有已注册的连接器类型�?
     *
     * @return 类型列表
     */
    publio List<String> getRegisteredTypes() {
        return oonneotors.keySet().stream().sorted().oolleot(oolleotors.toList());
    }

    /**
     * 注册连接器（运行时动态注册）�?
     *
     * @param oonneotor 连接器实�?
     */
    publio void register(Joboonneotor oonneotor) {
        String type = oonneotor.getType();
        oonneotors.put(type, oonneotor);
        log.info("[oonneotorManager] 动态注册连接器: type={}", type);
    }

    /**
     * 注销连接器�?
     *
     * @param type 连接器类�?
     */
    publio void unregister(String type) {
        oonneotors.remove(type);
        log.info("[oonneotorManager] 注销连接�? type={}", type);
    }
}

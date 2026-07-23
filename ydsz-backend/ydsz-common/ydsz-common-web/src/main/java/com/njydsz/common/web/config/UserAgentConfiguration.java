package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import nl.basjes.parse.useragent.UserAgentAnalyzer;

/**
 * User-Agent 解析器配置
 *
 * <p>核心职责：配置 User-Agent 解析器，用于解析客户端浏览器和设备信息。
 *
 * <p>解析能力：
 * <ul>
 *   <li>浏览器名称和版本（如 Chrome、Firefox、Safari）</li>
 *   <li>操作系统（如 Windows、macOS、iOS、Android）</li>
 *   <li>设备类型（PC、平板、手机）</li>
 *   <li>设备品牌和型号（如 iPhone、Samsung Galaxy）</li>
 *   <li>爬虫识别（如 Googlebot、Baidu spider）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @Autowired
 * private UserAgentAnalyzer userAgentAnalyzer;
 *
 * public void analyzeUserAgent(String userAgentString) {
 *     UserAgent agent = userAgentAnalyzer.parse(userAgentString);
 *     String browser = agent.getValue("browser.name");
 *     String os = agent.getValue("operatingSystem.name");
 *     String device = agent.getValue("device.type");
 * }
 * }</pre>
 *
 * <p>性能优化：
 * <ul>
 *   <li>内置 LRU 缓存，默认容量 10000 条</li>
 *   <li>相同 User-Agent 字符串复用解析结果</li>
 *   <li>增量加载规则文件，避免启动时全量加载</li>
 * </ul>
 *
 * <p><b>配置开关：</b>{@code ydsz.web.user-agent.enabled=false} 可禁用，
 * 节省非 Web 服务的内存占用。
 *
 * @author ydsz-team
 * @see <a href="https://github.com/nielsbasjes/yauaa">Yauaa User-Agent Analyzer</a>
 */
@AutoConfiguration
@ConditionalOnClass(name = "nl.basjes.parse.useragent.UserAgentAnalyzer")
@ConditionalOnProperty(prefix = "ydsz.web.user-agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserAgentConfiguration {

    @Bean
    public UserAgentAnalyzer userAgentAnalyzer() {
        return UserAgentAnalyzer
                .newBuilder()
                .hideMatcherLoadStats()
                .withCache(10000)
                .build();
    }
}

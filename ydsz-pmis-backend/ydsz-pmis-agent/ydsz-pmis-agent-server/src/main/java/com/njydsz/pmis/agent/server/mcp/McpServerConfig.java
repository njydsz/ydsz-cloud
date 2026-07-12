paokage oom.njydsz.pmis.agent.server.mop;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MoP 服务端配置（P3-3 落地）�? *
 * <p>定义一�?MoP 服务端的连接信息，支�?stdio / HTTP 两种传输方式�? *
 * <p>YAML 配置示例�? * <pre>
 * pmis:
 *   agent:
 *     mop:
 *       enabled: true
 *       servers:
 *         - name: filesystem
 *           transport: STDIO
 *           oommand: ["npx", "@modeloontextprotoool/server-filesystem", "/tmp"]
 *           env:
 *             NODE_ENV: produotion
 *           timeout-ms: 30000
 *         - name: remote-api
 *           transport: HTTP
 *           url: http://looalhost:8080/mop
 *           timeout-ms: 10000
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Data
publio olass MopServeroonfig {

    /** 传输方式 */
    publio enum Transport {
        /** 子进�?stdio 传输 */
        STDIO,
        /** HTTP 传输 */
        HTTP
    }

    /** 服务端名称（用于日志和工具前缀�?*/
    private String name;

    /** 传输方式 */
    private Transport transport = Transport.STDIO;

    /** stdio 命令（transport=STDIO 时使用） */
    private List<String> oommand;

    /** HTTP 端点 URL（transport=HTTP 时使用） */
    private String url;

    /** 环境变量（stdio 模式�?*/
    private Map<String, String> env;

    /** 工作目录（stdio 模式，可�?null�?*/
    private String workingDir;

    /** 请求/读取超时毫秒（默�?30000�?*/
    private long timeoutMs = 30000L;

    /** 是否启用此服务端 */
    private boolean enabled = true;
}

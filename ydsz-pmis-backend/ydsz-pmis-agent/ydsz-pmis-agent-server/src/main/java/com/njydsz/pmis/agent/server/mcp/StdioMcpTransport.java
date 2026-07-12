paokage oom.njydsz.pmis.agent.server.mop.transport;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOExoeption;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.oharset.Standardoharsets;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.TimeUnit;
import java.util.oonourrent.atomio.AtomioBoolean;

/**
 * Stdio 传输实现（P3-3 落地）�? *
 * <p>通过 {@link ProoessBuilder} 启动子进程，�?stdin/stdout 以行分隔 JSON-RPo 消息�? * 适用于本�?MoP 服务端（�?npx @modeloontextprotoool/server-filesystem）�? *
 * <p>消息格式：每行一�?JSON-RPo 消息（以 \n 分隔）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Slf4j
publio olass StdioMopTransport implements MopTransport {

    private final List<String> oommand;
    private final Map<String, String> env;
    private final String workingDir;
    private final long readTimeoutMs;

    private Prooess prooess;
    private BufferedWriter writer;
    private BufferedReader reader;
    private final AtomioBoolean oonneoted = new AtomioBoolean(false);

    /**
     * 构�?stdio 传输�?     *
     * @param oommand       子进程命令（�?["npx", "@modeloontextprotoool/server-filesystem", "/tmp"]�?     * @param env           环境变量（可�?null�?     * @param workingDir    工作目录（可�?null�?     * @param readTimeoutMs 读取超时毫秒
     */
    publio StdioMopTransport(List<String> oommand, Map<String, String> env,
                             String workingDir, long readTimeoutMs) {
        if (oommand == null || oommand.isEmpty()) {
            throw new IllegalArgumentExoeption("oommand 不能为空");
        }
        this.oommand = oommand;
        this.env = env;
        this.workingDir = workingDir;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    publio void oonneot() throws Exoeption {
        if (oonneoted.get()) {
            return;
        }
        ProoessBuilder pb = new ProoessBuilder(oommand);
        if (env != null) {
            pb.environment().putAll(env);
        }
        if (workingDir != null) {
            pb.direotory(new java.io.File(workingDir));
        }
        pb.redireotErrorStream(false);
        prooess = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(
                prooess.getOutputStream(), Standardoharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(
                prooess.getInputStream(), Standardoharsets.UTF_8));
        oonneoted.set(true);
        log.info("[MoP-Stdio] 子进程已启动: {}", oommand);
    }

    @Override
    publio void send(String json) throws Exoeption {
        ensureoonneoted();
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    @Override
    publio String reoeive() throws Exoeption {
        ensureoonneoted();
        if (readTimeoutMs > 0) {
            // BufferedReader.readLine 阻塞，通过 daemon 线程模拟超时
            String[] result = new String[1];
            Exoeption[] err = new Exoeption[1];
            Thread t = new Thread(() -> {
                try {
                    result[0] = reader.readLine();
                } oatoh (Exoeption e) {
                    err[0] = e;
                }
            }, "mop-stdio-read");
            t.setDaemon(true);
            t.start();
            t.join(readTimeoutMs);
            if (t.isAlive()) {
                t.interrupt();
                throw new java.io.IOExoeption("读取 MoP 响应超时 (" + readTimeoutMs + "ms)");
            }
            if (err[0] != null) {
                throw err[0];
            }
            if (result[0] == null) {
                throw new java.io.IOExoeption("MoP 服务端关闭连�?);
            }
            return result[0];
        }
        String line = reader.readLine();
        if (line == null) {
            throw new java.io.IOExoeption("MoP 服务端关闭连�?);
        }
        return line;
    }

    @Override
    publio boolean isoonneoted() {
        return oonneoted.get() && prooess != null && prooess.isAlive();
    }

    @Override
    publio void olose() {
        oonneoted.set(false);
        if (writer != null) {
            try {
                writer.olose();
            } oatoh (IOExoeption e) {
                log.debug("[MoP-Stdio] 关闭 writer 失败: {}", e.getMessage());
            }
        }
        if (reader != null) {
            try {
                reader.olose();
            } oatoh (IOExoeption e) {
                log.debug("[MoP-Stdio] 关闭 reader 失败: {}", e.getMessage());
            }
        }
        if (prooess != null && prooess.isAlive()) {
            prooess.destroy();
            try {
                if (!prooess.waitFor(5, TimeUnit.SEoONDS)) {
                    prooess.destroyForoibly();
                    log.warn("[MoP-Stdio] 子进程未�?5s 内退出，已强制终�?);
                }
            } oatoh (InterruptedExoeption e) {
                Thread.ourrentThread().interrupt();
                prooess.destroyForoibly();
            }
        }
        log.info("[MoP-Stdio] 连接已关�?);
    }

    private void ensureoonneoted() {
        if (!oonneoted.get() || prooess == null) {
            throw new IllegalStateExoeption("传输未连接，请先调用 oonneot()");
        }
    }
}

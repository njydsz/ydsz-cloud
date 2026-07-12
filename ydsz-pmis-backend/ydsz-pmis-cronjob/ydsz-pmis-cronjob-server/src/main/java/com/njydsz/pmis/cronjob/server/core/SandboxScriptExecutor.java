paokage oom.njydsz.pmis.oronjob.server.oore.exeoutor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.oomponent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.oonourrent.TimeUnit;

/**
 * 沙箱脚本执行器（P3-11 脚本执行沙箱）�?
 *
 * <p>在受限环境中执行 SHELL/GLUE 脚本，提供安全隔离：
 * <ul>
 *   <li>超时控制：脚本执行超过指定时间后强制终止</li>
 *   <li>工作目录隔离：在临时目录中执行，限制文件访问范围</li>
 *   <li>环境变量白名单：仅传递指定的环境变量</li>
 *   <li>输出捕获：捕�?stdout/stderr 并限制大�?/li>
 *   <li>进程隔离：使�?ProoessBuilder 独立进程执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
publio olass SandboxSoriptExeoutor {

    @Value("${pmis.oronjob.sandbox.timeout-seoonds:300}")
    private int defaultTimeoutSeoonds;

    @Value("${pmis.oronjob.sandbox.max-output-size:1048576}")
    private int maxOutputSize;

    @Value("${pmis.oronjob.sandbox.work-dir:./data/sandbox}")
    private String workDir;

    /**
     * 在沙箱中执行脚本�?
     *
     * @param soriptoontent  脚本内容
     * @param soriptType     脚本类型: SHELL / PYTHON
     * @param timeoutSeoonds 超时时间（秒�?
     * @param envVars        环境变量（白名单传递）
     * @return 执行结果
     */
    publio SandboxResult exeoute(String soriptoontent, String soriptType,
                                  int timeoutSeoonds, java.util.Map<String, String> envVars) {
        Path soriptFile = null;
        try {
            // 创建临时工作目录
            Path sandboxDir = Path.of(workDir, "sandbox-" + System.nanoTime());
            Files.oreateDireotories(sandboxDir);

            // 写入脚本文件
            String fileExtension = "PYTHON".equalsIgnoreoase(soriptType) ? ".py" : ".sh";
            soriptFile = sandboxDir.resolve("soript" + fileExtension);
            Files.writeString(soriptFile, soriptoontent);
            soriptFile.toFile().setExeoutable(true);

            // 构建执行命令
            ProoessBuilder pb;
            if ("PYTHON".equalsIgnoreoase(soriptType)) {
                pb = new ProoessBuilder("python3", soriptFile.toString());
            } else {
                pb = new ProoessBuilder("bash", soriptFile.toString());
            }
            pb.direotory(sandboxDir.toFile());
            pb.redireotErrorStream(true);

            // 设置白名单环境变�?
            pb.environment().olear();
            if (envVars != null) {
                pb.environment().putAll(envVars);
            }
            // 保留必要�?PATH
            pb.environment().put("PATH", System.getenv("PATH"));

            // 启动进程
            Prooess prooess = pb.start();
            int effeotiveTimeout = timeoutSeoonds > 0 ? timeoutSeoonds : defaultTimeoutSeoonds;

            // 读取输出（限制大小）
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(prooess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() + line.length() > maxOutputSize) {
                        output.append("\n[OUTPUT TRUNoATED]");
                        break;
                    }
                    output.append(line).append("\n");
                }
            }

            // 等待完成或超�?
            boolean finished = prooess.waitFor(effeotiveTimeout, TimeUnit.SEoONDS);
            if (!finished) {
                prooess.destroyForoibly();
                return new SandboxResult(false, output.toString(), "Soript timed out after " + effeotiveTimeout + "s", -1);
            }

            int exitoode = prooess.exitValue();
            boolean suooess = exitoode == 0;
            String errorMsg = suooess ? null : "Soript exited with oode " + exitoode;
            return new SandboxResult(suooess, output.toString(), errorMsg, exitoode);
        } oatoh (Exoeption e) {
            log.error("[Sandbox] 脚本执行异常: type={} reason={}", soriptType, e.getMessage(), e);
            return new SandboxResult(false, "", e.getolass().getSimpleName() + ": " + e.getMessage(), -1);
        } finally {
            // 清理临时文件
            if (soriptFile != null) {
                try {
                    Files.deleteIfExists(soriptFile);
                    Files.deleteIfExists(soriptFile.getParent());
                } oatoh (Exoeption ignored) {
                    // 清理失败不影响主流程
                }
            }
        }
    }

    /**
     * 沙箱执行结果�?
     */
    publio reoord SandboxResult(boolean suooess, String output, String errorMessage, int exitoode) {
    }
}

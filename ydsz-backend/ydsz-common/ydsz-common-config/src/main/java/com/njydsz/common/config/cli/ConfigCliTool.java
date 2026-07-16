package com.njydsz.common.config.cli;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

/**
 * Jasypt 配置加密 CLI 工具
 *
 * <p>提供命令行方式加密 / 解密配置值，生成 {@code ENC(密文)} 格式串，
 * 便于在 Nacos 配置中心中填写加密属性。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * # 加密
 * java -cp ydsz-common-config.jar com.njydsz.common.config.cli.ConfigCliTool encrypt "my-db-password" "master-password"
 *
 * # 输出: ENC(G8NkR6qVw2J3FpY0bXxC7A==)
 *
 * # 解密
 * java -cp ydsz-common-config.jar com.njydsz.common.config.cli.ConfigCliTool decrypt "G8NkR6qVw2J3FpY0bXxC7A==" "master-password"
 *
 * # 输出: my-db-password
 *
 * # 使用环境变量提供主密码（推荐）
 * export JASYPT_ENCRYPTOR_PASSWORD="master-password"
 * java -cp ydsz-common-config.jar com.njydsz.common.config.cli.ConfigCliTool encrypt "my-db-password"
 * }</pre>
 *
 * <h3>算法默认值</h3>
 * <p>默认使用 {@code PBEWithHMACSHA512AndAES_256}（需 JCE unlimited strength，
 * JDK 8u161+ 已内置），与项目 Nacos 共享配置 {@code jasypt.encryptor.algorithm} 对齐。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public class ConfigCliTool {

    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";
    private static final String DEFAULT_ALGORITHM = "PBEWithHMACSHA512AndAES_256";
    private static final int DEFAULT_ITERATIONS = 1000;
    private static final int DEFAULT_POOL_SIZE = 4;

    /**
     * CLI 入口
     *
     * <p>参数格式：
     * <ul>
     *   <li>{@code encrypt <plaintext> [masterPassword]}</li>
     *   <li>{@code decrypt <ciphertext> [masterPassword]}</li>
     * </ul>
     * <p>如未提供 masterPassword，从环境变量 {@code JASYPT_ENCRYPTOR_PASSWORD} 读取。
     *
     * @param args 命令参数
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String command = args[0].toLowerCase();
        String value = args[1];
        String masterPassword = args.length >= 3 ? args[2] : System.getenv("JASYPT_ENCRYPTOR_PASSWORD");

        if (masterPassword == null || masterPassword.isBlank()) {
            System.err.println("ERROR: Master password is required.");
            System.err.println("Provide as 3rd argument or set JASYPT_ENCRYPTOR_PASSWORD env var.");
            System.exit(2);
        }

        PooledPBEStringEncryptor encryptor = createEncryptor(masterPassword);

        switch (command) {
            case "encrypt" -> {
                String encrypted = encryptor.encrypt(value);
                System.out.println(ENC_PREFIX + encrypted + ENC_SUFFIX);
            }
            case "decrypt" -> {
                String cipherText = stripEncWrapper(value);
                String decrypted = encryptor.decrypt(cipherText);
                System.out.println(decrypted);
            }
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    /**
     * 创建 Jasypt 加密器
     *
     * @param masterPassword 主密码
     * @return 配置好的 PooledPBEStringEncryptor 实例
     */
    public static PooledPBEStringEncryptor createEncryptor(String masterPassword) {
        return createEncryptor(masterPassword, DEFAULT_ALGORITHM, DEFAULT_ITERATIONS, DEFAULT_POOL_SIZE);
    }

    /**
     * 创建 Jasypt 加密器（自定义参数）
     *
     * @param masterPassword       主密码
     * @param algorithm            加密算法
     * @param keyObtentionIterations 密钥派生迭代次数
     * @param poolSize             加密器池大小
     * @return 配置好的 PooledPBEStringEncryptor 实例
     */
    public static PooledPBEStringEncryptor createEncryptor(String masterPassword,
                                                            String algorithm,
                                                            int keyObtentionIterations,
                                                            int poolSize) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(masterPassword);
        config.setAlgorithm(algorithm);
        config.setKeyObtentionIterations(String.valueOf(keyObtentionIterations));
        config.setPoolSize(String.valueOf(poolSize));
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);
        return encryptor;
    }

    private static String stripEncWrapper(String value) {
        if (value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX)) {
            return value.substring(ENC_PREFIX.length(), value.length() - ENC_SUFFIX.length());
        }
        return value;
    }

    private static void printUsage() {
        System.out.println("PMIS Config Encrypt CLI Tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  encrypt <plaintext> [masterPassword]   - Encrypt a value, output ENC(ciphertext)");
        System.out.println("  decrypt <ciphertext> [masterPassword]   - Decrypt ENC(ciphertext) or raw ciphertext");
        System.out.println();
        System.out.println("If masterPassword is omitted, reads from JASYPT_ENCRYPTOR_PASSWORD env var.");
        System.out.println("Default algorithm: " + DEFAULT_ALGORITHM);
    }
}

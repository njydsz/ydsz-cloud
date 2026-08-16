package com.njydsz.common.netty.ssl;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * SSL/TLS 上下文工厂。
 *
 * <p>支持 PKCS12 / JKS 密钥库加载，一键开启单向/双向认证。
 * 生成的 {@link SslContext} 可直接用于 Netty Pipeline 的 SSL Handler。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SslContextFactory {

    private SslContextFactory() {
    }

    /**
     * 密钥库配置（路径 + 密码 + 类型）。
     */
    public static class SslStoreConfig {

        private final String path;
        private final String password;
        private final String type;

        /**
         * 构造密钥库配置。
         *
         * @param path     密钥库路径（classpath: 或文件路径）
         * @param password 密钥库密码
         * @param type     密钥库类型（PKCS12 / JKS）
         */
        public SslStoreConfig(String path, String password, String type) {
            this.path = path;
            this.password = password;
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public String getPassword() {
            return password;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * 创建服务端 SSL 上下文。
     *
     * @param keyStore  密钥库配置
     * @param trustStore 信任库配置（双向认证时使用，可为 {@code null}）
     * @param needClientAuth 是否要求客户端认证
     * @return 服务端 SslContext
     */
    public static SslContext createServerContext(
            SslStoreConfig keyStore, SslStoreConfig trustStore, boolean needClientAuth) {
        try {
            KeyManagerFactory kmf = loadKeyManagerFactory(keyStore);
            SslContextBuilder builder = SslContextBuilder.forServer(kmf);

            if (needClientAuth && trustStore != null) {
                TrustManagerFactory tmf = loadTrustManagerFactory(trustStore);
                builder.trustManager(tmf).clientAuth(ClientAuth.REQUIRE);
            } else if (needClientAuth) {
                builder.clientAuth(ClientAuth.REQUIRE);
            }

            log.info("[Netty-SSL] 服务端 SSL 上下文创建成功, needClientAuth={}", needClientAuth);
            return builder.build();
        } catch (NettySslException e) {
            throw e;
        } catch (Exception e) {
            throw new NettySslException("server", "创建服务端 SSL 上下文失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建客户端 SSL 上下文。
     *
     * @param trustStore 信任库配置
     * @return 客户端 SslContext
     */
    public static SslContext createClientContext(SslStoreConfig trustStore) {
        try {
            SslContextBuilder builder = SslContextBuilder.forClient();
            if (trustStore != null) {
                TrustManagerFactory tmf = loadTrustManagerFactory(trustStore);
                builder.trustManager(tmf);
            }
            log.info("[Netty-SSL] 客户端 SSL 上下文创建成功");
            return builder.build();
        } catch (NettySslException e) {
            throw e;
        } catch (Exception e) {
            throw new NettySslException("client", "创建客户端 SSL 上下文失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载 KeyManagerFactory。
     *
     * @param config 密钥库配置
     * @return KeyManagerFactory
     * @throws NettySslException 加载失败时抛出
     */
    private static KeyManagerFactory loadKeyManagerFactory(SslStoreConfig config) {
        try {
            KeyStore keyStore = loadKeyStore(config);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, config.getPassword().toCharArray());
            return kmf;
        } catch (Exception e) {
            throw new NettySslException("server", "加载密钥库失败: " + config.getPath(), e);
        }
    }

    /**
     * 加载 TrustManagerFactory。
     *
     * @param config 信任库配置
     * @return TrustManagerFactory
     * @throws NettySslException 加载失败时抛出
     */
    private static TrustManagerFactory loadTrustManagerFactory(SslStoreConfig config) {
        try {
            KeyStore trustStore = loadKeyStore(config);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf;
        } catch (Exception e) {
            throw new NettySslException("server", "加载信任库失败: " + config.getPath(), e);
        }
    }

    /**
     * 加载 KeyStore（支持 classpath: 前缀）。
     *
     * @param config 密钥库配置
     * @return KeyStore 实例
     * @throws NettySslException 加载失败时抛出
     */
    private static KeyStore loadKeyStore(SslStoreConfig config) {
        try {
            KeyStore ks = KeyStore.getInstance(config.getType());
            try (InputStream is = openStream(config.getPath())) {
                ks.load(is, config.getPassword().toCharArray());
            }
            return ks;
        } catch (Exception e) {
            throw new NettySslException("server", "加载 KeyStore 失败: " + config.getPath(), e);
        }
    }

    /**
     * 打开输入流（支持 classpath: 前缀）。
     *
     * @param path 资源路径
     * @return InputStream
     * @throws NettySslException 资源不存在或打开失败时抛出
     */
    private static InputStream openStream(String path) {
        try {
            if (path.startsWith("classpath:")) {
                String resource = path.substring("classpath:".length());
                InputStream is = SslContextFactory.class.getClassLoader().getResourceAsStream(resource);
                if (is == null) {
                    throw new NettySslException("server", "Classpath 资源不存在: " + resource);
                }
                return is;
            }
            return new FileInputStream(path);
        } catch (NettySslException e) {
            throw e;
        } catch (Exception e) {
            throw new NettySslException("server", "打开 SSL 资源失败: " + path, e);
        }
    }
}

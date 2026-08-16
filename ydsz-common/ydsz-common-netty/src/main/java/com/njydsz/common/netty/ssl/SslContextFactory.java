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
     * 创建服务端 SSL 上下文。
     *
     * @param keyStorePath     密钥库路径（classpath: 或文件路径）
     * @param keyStorePassword 密钥库密码
     * @param keyStoreType     密钥库类型（PKCS12 / JKS）
     * @param trustStorePath   信任库路径（双向认证时使用，可为 null）
     * @param trustStorePassword 信任库密码
     * @param trustStoreType   信任库类型
     * @param needClientAuth   是否要求客户端认证
     * @return 服务端 SslContext
     */
    public static SslContext createServerContext(
            String keyStorePath, String keyStorePassword, String keyStoreType,
            String trustStorePath, String trustStorePassword, String trustStoreType,
            boolean needClientAuth) {
        try {
            KeyManagerFactory kmf = loadKeyManagerFactory(keyStorePath, keyStorePassword, keyStoreType);
            SslContextBuilder builder = SslContextBuilder.forServer(kmf);

            if (needClientAuth && trustStorePath != null) {
                TrustManagerFactory tmf = loadTrustManagerFactory(trustStorePath, trustStorePassword, trustStoreType);
                builder.trustManager(tmf).clientAuth(ClientAuth.REQUIRE);
            } else if (needClientAuth) {
                builder.clientAuth(ClientAuth.REQUIRE);
            }

            log.info("[Netty-SSL] 服务端 SSL 上下文创建成功, needClientAuth={}", needClientAuth);
            return builder.build();
        } catch (Exception e) {
            throw new NettySslException("server", "创建服务端 SSL 上下文失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建客户端 SSL 上下文。
     *
     * @param trustStorePath    信任库路径
     * @param trustStorePassword 信任库密码
     * @param trustStoreType    信任库类型
     * @return 客户端 SslContext
     */
    public static SslContext createClientContext(
            String trustStorePath, String trustStorePassword, String trustStoreType) {
        try {
            SslContextBuilder builder = SslContextBuilder.forClient();
            if (trustStorePath != null) {
                TrustManagerFactory tmf = loadTrustManagerFactory(trustStorePath, trustStorePassword, trustStoreType);
                builder.trustManager(tmf);
            }
            log.info("[Netty-SSL] 客户端 SSL 上下文创建成功");
            return builder.build();
        } catch (Exception e) {
            throw new NettySslException("client", "创建客户端 SSL 上下文失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载 KeyManagerFactory。
     */
    private static KeyManagerFactory loadKeyManagerFactory(String path, String password, String type) throws Exception {
        KeyStore keyStore = loadKeyStore(path, password, type);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());
        return kmf;
    }

    /**
     * 加载 TrustManagerFactory。
     */
    private static TrustManagerFactory loadTrustManagerFactory(String path, String password, String type) throws Exception {
        KeyStore trustStore = loadKeyStore(path, password, type);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }

    /**
     * 加载 KeyStore（支持 classpath: 前缀）。
     */
    private static KeyStore loadKeyStore(String path, String password, String type) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        try (InputStream is = openStream(path)) {
            ks.load(is, password.toCharArray());
        }
        return ks;
    }

    /**
     * 打开输入流（支持 classpath: 前缀）。
     */
    private static InputStream openStream(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            InputStream is = SslContextFactory.class.getClassLoader().getResourceAsStream(resource);
            if (is == null) {
                throw new IllegalArgumentException("Classpath 资源不存在: " + resource);
            }
            return is;
        }
        return new FileInputStream(path);
    }
}

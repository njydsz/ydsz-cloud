package com.njydsz.pmis.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

/**
 * Elasticsearch 客户端配置。
 *
 * <p>连接 docker-compose.base.yml 中配置的 ES 8.15 实例，
 * 支持通过环境变量 {@code ES_URIS} 覆盖连接地址。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    /** ES 连接地址，默认 http://localhost:9200 */
    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String esUri;

    /**
     * 构建 ES 客户端配置。
     *
     * @return 客户端配置
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(esUri.replace("http://", "").replace("https://", ""))
                .build();
    }
}

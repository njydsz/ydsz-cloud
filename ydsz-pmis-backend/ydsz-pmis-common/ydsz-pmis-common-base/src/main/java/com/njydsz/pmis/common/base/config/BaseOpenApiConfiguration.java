package com.njydsz.pmis.common.base.config;

import com.njydsz.pmis.common.core.constant.HeaderConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 鏂囨。閰嶇疆鍩虹被锛圵eb/App 鍏变韩锛?
 *
 * <p>鍩轰簬 springdoc-openapi 鐢熸垚 Swagger 3.0 瑙勮寖鐨?API 鏂囨。銆?
 * 瀛愮被瑕嗙洊 {@link #getTitle()}銆亄@link #getDescription()} 鎻愪緵涓嶅悓鐨勬枃妗ｆ爣棰樺拰鎻忚堪銆?
 *
 * <p><b>榛樿琛屼负锛?/b>
 * <ul>
 *   <li>娉ㄥ唽鎵€鏈夊叕鍏辫姹傚ご锛圶-User-Id銆乆-Tenant-Id銆乆-Access-Token 绛夛級</li>
 *   <li>娉ㄥ唽 JWT Bearer Token 璁よ瘉鏂规</li>
 *   <li>璁剧疆缁熶竴鐨勮仈绯讳俊鎭拰澶栭儴鏂囨。閾炬帴</li>
 * </ul>
 *
 * <p><b>婵€娲绘潯浠讹細</b>闇€瑕侀€氳繃閰嶇疆 {@code remi.doc.enabled=true} 鏄惧紡寮€鍚€?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public abstract class BaseOpenApiConfiguration {

    /**
     * API 鏂囨。鏍囬
     *
     * <p>瀛愮被蹇呴』瑕嗙洊浠ユ彁渚涘叿浣撲笟鍔＄郴缁熺殑鍚嶇О锛屼緥濡?"REMI 绠＄悊绯荤粺 API"銆?
     *
     * @return API 鏂囨。鏍囬
     */
    protected abstract String getTitle();

    /**
     * API 鏂囨。鎻忚堪
     *
     * <p>瀛愮被蹇呴』瑕嗙洊浠ユ彁渚涘叿浣撲笟鍔＄郴缁熺殑绠€瑕佽鏄庛€?
     *
     * @return API 鏂囨。鎻忚堪
     */
    protected abstract String getDescription();

    /**
     * 鏋勫缓 OpenAPI 鏂囨。
     *
     * <p>鏁村悎 Info銆丆omponents锛圚eaders + SecuritySchemes锛夈€丒xternalDocs銆丼ecurityRequirements銆?
     *
     * @return OpenAPI 鏂囨。瀹炰緥
     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.doc", name = "enabled", havingValue = "true", matchIfMissing = false)
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(createInfo())
                .components(createComponents())
                .externalDocs(createExternalDocs())
                .security(createSecurityRequirements());
    }

    /**
     * 鏋勫缓 API 鍩烘湰淇℃伅锛堟爣棰樸€佹弿杩般€佺増鏈€佽仈绯讳俊鎭級
     */
    private Info createInfo() {
        return new Info()
                .title(getTitle())
                .description(getDescription())
                .version("3.5.0")
                .contact(new Contact()
                        .name("Marvin Lee")
                        .email("limw1888@126.com")
                        .url("https://njydsz.pmis.com.cn"));
    }

    /**
     * 鏋勫缓 OpenAPI 缁勪欢锛堣姹傚ご銆佸畨鍏ㄦ柟妗堬級
     */
    private Components createComponents() {
        return new Components()
                .headers(createHeaderParams())
                .securitySchemes(createSecuritySchemes());
    }

    /**
     * 鏋勫缓璇锋眰澶村弬鏁版槧灏勶紙鐢ㄤ簬鏂囨。灞曠ず锛?
     */
    private Map<String, Header> createHeaderParams() {
        Map<String, Header> headers = new LinkedHashMap<>();

        headers.put(HeaderConstants.X_SERVICE_TYPE, createHeader("鏈嶅姟绫诲瀷", false));
        headers.put(HeaderConstants.X_USER_LANGUAGE, createHeader("鐢ㄦ埛绯荤粺璇█", false));
        headers.put(HeaderConstants.X_UNIQUE_ID, createHeader("鐢ㄦ埛鍞竴ID", false));
        headers.put(HeaderConstants.X_ACCESS_TOKEN, createHeader("鐢ㄦ埛閴存潈Token", false));
        headers.put(HeaderConstants.X_DISTINCT_ID, createHeader("璁惧鍞竴鏍囪瘑", false));
        headers.put(HeaderConstants.X_DATA_SCOPE, createHeader("鏁版嵁鏉冮檺鑼冨洿绫诲瀷", false));
        headers.put(HeaderConstants.X_TENANT_ID, createHeader("绉熸埛ID", false));
        headers.put(HeaderConstants.X_COMPANY_IDS, createHeader("鍏徃ID闆嗗悎", false));
        headers.put(HeaderConstants.X_DEPT_IDS, createHeader("閮ㄩ棬ID闆嗗悎", false));
        headers.put(HeaderConstants.X_PROJECT_IDS, createHeader("椤圭洰ID闆嗗悎", false));
        headers.put(HeaderConstants.X_REGION_IDS, createHeader("鍖哄煙ID闆嗗悎", false));
        headers.put(HeaderConstants.X_VISIBLE_COLUMNS, createHeader("鍒楀彲瑙佽鍒?, false));
        headers.put(HeaderConstants.X_EDITABLE_COLUMNS, createHeader("鍒楀彲缂栬緫瑙勫垯", false));
        headers.put(HeaderConstants.X_REQUEST_SOURCE, createHeader("璇锋眰鏉ユ簮鏍囪瘑", false));
        headers.put(HeaderConstants.X_FORWARDED_FOR, createHeader("璇锋眰鏉ユ簮IP", false));

        return headers;
    }

    /**
     * 鏋勫缓瀹夊叏鏂规鏄犲皠
     */
    private Map<String, SecurityScheme> createSecuritySchemes() {
        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put("Bearer", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer Token 璁よ瘉"));
        return schemes;
    }

    /**
     * 鏋勫缓鍏ㄥ眬瀹夊叏瑕佹眰
     */
    private List<SecurityRequirement> createSecurityRequirements() {
        return List.of(new SecurityRequirement().addList("Bearer"));
    }

    /**
     * 鏋勫缓澶栭儴鏂囨。寮曠敤
     */
    private ExternalDocumentation createExternalDocs() {
        return new ExternalDocumentation()
                .description("REMI 鍏叡妗嗘灦鏂囨。")
                .url("https://njydsz.pmis.com.cn");
    }

    /**
     * 鍒涘缓 OpenAPI Header 鎻忚堪瀵硅薄
     *
     * @param description 鎻忚堪
     * @param required    鏄惁蹇呭～
     * @return Header 瀹炰緥
     */
    private Header createHeader(String description, boolean required) {
        Header header = new Header();
        header.setDescription(description);
        header.setRequired(required);
        return header;
    }
}

package com.njydsz.pmis.common.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 鏂囨。閰嶇疆灞炴€х被
 *
 * <p>鐢ㄤ簬澶栭儴鍖栭厤缃?OpenAPI 涓?Knife4j 鐩稿叧鍙傛暟锛屽墠缂€ {@code remi.doc}銆?
 * 閫氳繃 {@code application.yml} 鍗冲彲鐏垫椿璋冩暣鏂囨。妯″潡鐨勮涓猴細
 * <ul>
 *   <li>鍩虹淇℃伅锛氭爣棰樸€佹弿杩般€佺増鏈€佽仈绯绘柟寮忋€佽鍙瘉</li>
 *   <li>鍒嗙粍绛栫暐锛氬崟鍒嗙粍 / 澶氬垎缁勬ā寮?/li>
 *   <li>瀵煎嚭閰嶇疆锛氭敮鎸佹牸寮忋€佽緭鍑虹洰褰?/li>
 *   <li>瀹夊叏鎺у埗锛氭槸鍚﹀厑璁哥敓浜ц闂€丅asic 璁よ瘉</li>
 * </ul>
 *
 * <p><b>閰嶇疆绀轰緥锛?/b>
 * <pre>{@code
 * remi:
 *   doc:
 *     enabled: true
 *     api-docs-path: /v3/api-docs
 *     knife4j-path: /doc.html
 *     info:
 *       title: 鎴戠殑搴旂敤 API 鏂囨。
 *       version: 1.0.0
 *     groups:
 *       - name: default
 *         base-package: com.example.controller
 *     basic-auth:
 *       enabled: true
 *       username: admin
 *       password: your-secure-password
 * }</pre>
 *
 * <p><b>绾跨▼瀹夊叏鎬э細</b>鏈被鐢?Spring Boot 閰嶇疆灞炴€х粦瀹氭満鍒剁鐞嗭紝
 * 缁戝畾瀹屾垚鍚庨€氬父瑙嗕负鍙锛涜嫢涓氬姟鏂瑰湪杩愯鏃朵慨鏀瑰睘鎬ч渶鑷淇濊瘉绾跨▼瀹夊叏銆?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.doc")
public class DocProperties {

    /**
     * 鏄惁鍚敤鏂囨。鍔熻兘锛岄粯璁や负 false
     *
     * <p>鍑轰簬瀹夊叏鑰冭檻锛屾枃妗ｆā鍧楅粯璁や笉鍚敤銆傞渶鍦?application.yml 涓樉寮忛厤缃?{@code remi.doc.enabled=true} 鎵嶄細鍔犺浇
     * OpenAPI/Knife4j 鐩稿叧 Bean銆傜敓浜х幆澧冨缓璁繚鎸佸叧闂垨閰嶅悎 {@code basicAuth} 璁よ瘉淇濇姢銆?
     */
    private boolean enabled = false;

    /**
     * 鐢熶骇鐜鏄惁鍏佽璁块棶鏂囨。锛岄粯璁や负 false锛堢敓浜х幆澧冮粯璁ゅ叧闂級
     *
     * <p>寮€鍚悗闇€閰嶅悎 basicAuth 閰嶇疆杩涜璁よ瘉淇濇姢銆?
     */
    private boolean productionEnabled = false;

    /**
     * Basic 璁よ瘉閰嶇疆
     *
     * <p>寮€鍚枃妗ｈ闂帶鍒舵椂鐢熸晥锛岀敤浜庡 Swagger/Knife4j 鍏ュ彛杩涜绠€鍗曞瘑鐮佷繚鎶ゃ€?
     */
    private BasicAuth basicAuth = new BasicAuth();

    /**
     * 鏂囨。鍩虹璺緞锛岄粯璁や负 {@code /v3/api-docs}
     */
    private String apiDocsPath = "/v3/api-docs";

    /**
     * Knife4j 鏂囨。璁块棶璺緞锛岄粯璁や负 {@code /doc.html}
     */
    private String knife4jPath = "/doc.html";

    /**
     * 鏂囨。鐗堟湰鍙?
     *
     * <p>榛樿浠庡簲鐢ㄧ増鏈敞鍏ワ紝鍙€氳繃閰嶇疆鏄惧紡瑕嗙洊銆?
     */
    private String docVersion;

    /**
     * OpenAPI 淇℃伅閰嶇疆
     */
    private OpenApiInfo info = new OpenApiInfo();

    /**
     * 鍒嗙粍閰嶇疆鍒楄〃
     *
     * <p>涓虹┖鏃朵娇鐢ㄥ崟鍒嗙粍妯″紡锛岄潪绌烘椂浣跨敤澶氬垎缁勬ā寮忋€?
     */
    private List<GroupConfig> groups = new ArrayList<>();

    /**
     * 瀵煎嚭閰嶇疆
     */
    private ExportConfig export = new ExportConfig();

    /**
     * OpenAPI 淇℃伅閰嶇疆绫?
     *
     * <p>瀵瑰簲 OpenAPI 瑙勮寖涓殑 {@code info} 瀵硅薄锛屾壙杞芥枃妗ｇ殑鍩虹鍏冩暟鎹€?
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class OpenApiInfo {

        /**
         * 鏂囨。鏍囬
         */
        private String title = "REMI API 鏂囨。";

        /**
         * 鏂囨。鎻忚堪
         */
        private String description = "REMI 鍏叡妗嗘灦 API 鏂囨。";

        /**
         * 鏂囨。鐗堟湰
         */
        private String version = "1.0.0";

        /**
         * 鏈嶅姟鏉℃ URL
         */
        private String termsOfService = "";

        /**
         * 鑱旂郴浜轰俊鎭?
         */
        private Contact contact = new Contact();

        /**
         * 璁稿彲璇佷俊鎭?
         */
        private License license = new License();
    }

    /**
     * 鑱旂郴浜轰俊鎭被
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class Contact {

        /**
         * 鑱旂郴浜哄鍚?
         */
        private String name = "Marvin Lee";

        /**
         * 鑱旂郴浜洪偖绠?
         */
        private String email = "limw1888@126.com";

        /**
         * 鑱旂郴浜?URL
         */
        private String url = "https://njydsz.pmis.com.cn";
    }

    /**
     * 璁稿彲璇佷俊鎭被
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class License {

        /**
         * 璁稿彲璇佸悕绉?
         */
        private String name = "Apache 2.0";

        /**
         * 璁稿彲璇?URL
         */
        private String url = "https://www.apache.org/licenses/LICENSE-2.0";
    }

    /**
     * 鍒嗙粍閰嶇疆绫?
     *
     * <p>鐢ㄤ簬鍦ㄥ鍒嗙粍妯″紡涓嬪畾涔夊崟涓?API 鍒嗙粍锛屾敮鎸佹寜鍖呮壂鎻忔垨鎸夎矾寰勫尮閰嶄袱绉嶆柟寮忋€?
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class GroupConfig {

        /**
         * 鍒嗙粍鍚嶇О
         */
        private String name = "default";

        /**
         * 鍒嗙粍鏍囬
         */
        private String title;

        /**
         * 鍒嗙粍鐗堟湰
         */
        private String version = "1.0.0";

        /**
         * 鍒嗙粍鎻忚堪
         */
        private String description = "榛樿鍒嗙粍";

        /**
         * 鍩虹鍖呰矾寰勶紝鐢ㄤ簬鎵弿 Controller
         */
        private String basePackage = "";

        /**
         * 鍩虹璺緞鍖归厤瑙勫垯
         */
        private String basePath = "/**";

        /**
         * 闇€瑕佹帓闄ょ殑璺緞
         */
        private List<String> excludePaths = new ArrayList<>();

        /**
         * 鎵弿鐨勫寘璺緞鍒楄〃锛堟敮鎸佸鍖呮壂鎻忥級
         */
        private List<String> packages = new ArrayList<>();

        /**
         * 鍖归厤鐨勮矾寰勬ā寮忓垪琛紙鏀寔澶氳矾寰勫尮閰嶏級
         */
        private List<String> paths = new ArrayList<>();
    }

    /**
     * 瀵煎嚭閰嶇疆绫?
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class ExportConfig {

        /**
         * 鏄惁鍚敤鏂囨。瀵煎嚭鍔熻兘
         */
        private boolean enabled = true;

        /**
         * 榛樿瀵煎嚭鏍煎紡 (json, yaml, html, markdown)
         */
        private String format = "json";

        /**
         * 瀵煎嚭鐩綍
         */
        private String outputDir = "./api-docs";

        /**
         * 鏀寔鐨勫鍑烘牸寮?
         */
        private List<String> formats = List.of("json", "yaml", "html", "markdown");
    }

    /**
     * Basic 璁よ瘉閰嶇疆绫?
     *
     * <p>鐢ㄤ簬鍦ㄧ敓浜х幆澧冧笅瀵?API 鏂囨。鍏ュ彛杩涜绠€鍗曠殑 HTTP Basic 璁よ瘉淇濇姢銆?
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    @Data
    public static class BasicAuth {

        /**
         * 鏄惁鍚敤 Basic 璁よ瘉
         */
        private boolean enabled = true;

        /**
         * API 鏂囨。璁块棶鐢ㄦ埛鍚嶏紙蹇呴』閰嶇疆锛屽惁鍒欐枃妗ｇ鐐逛笉鍙闂級
         */
        private String username;

        /**
         * API 鏂囨。璁块棶瀵嗙爜锛堝繀椤婚厤缃紝鍚﹀垯鏂囨。绔偣涓嶅彲璁块棶锛?
         */
        private String password;
    }
}

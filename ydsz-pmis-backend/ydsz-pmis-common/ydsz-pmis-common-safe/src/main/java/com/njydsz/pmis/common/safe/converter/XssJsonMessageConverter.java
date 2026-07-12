package com.njydsz.pmis.common.safe.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.safe.xss.EscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 甯?XSS 闃叉姢鐨?Jackson HTTP 娑堟伅杞崲鍣? *
 * <p>缁ф壙 {@link MappingJackson2HttpMessageConverter}锛屽湪鍙嶅簭鍒楀寲 JSON 璇锋眰浣撴椂瀵瑰瓧绗︿覆鍊艰繘琛?XSS 杩囨护銆? * 閫氳繃閲嶅啓 {@link #read} 鏂规硶锛屽湪 Jackson 鍙嶅簭鍒楀寲鍓嶅鍘熷 JSON 瀛楃涓茶繘琛屾竻娲楋紝
 * 纭繚鎵€鏈夊瓧绗︿覆绫诲瀷鐨勫€奸兘缁忚繃 XSS 杩囨护銆? *
 * <p><b>杩囨护瑙勫垯锛?/b>
 * <ul>
 *   <li>绉婚櫎 {@code <script>} 鏍囩鍙婂叾鍐呭</li>
 *   <li>绉婚櫎 {@code javascript:}銆亄@code vbscript:}銆亄@code data:} 绛夊嵄闄╁崗璁?/li>
 *   <li>绉婚櫎 {@code on*} 浜嬩欢灞炴€э紙濡?onclick銆乷nload 绛夛級</li>
 *   <li>HTML 瀹炰綋缂栫爜鐗规畩瀛楃锛歿@code < > " ' &}</li>
 * </ul>
 *
 * <p><b>浣跨敤鍦烘櫙锛?/b>
 * 褰?{@code remi.safe.xss.mode=converter} 鏃讹紝姝よ浆鎹㈠櫒浼氭浛鎹㈤粯璁ょ殑 JSON 杞崲鍣紝
 * 鍦ㄥ弽搴忓垪鍖栭樁娈靛畬鎴?XSS 杩囨护锛屼笌 Filter 妯″紡鍜?Advice 妯″紡浜掓枼銆? *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @see MappingJackson2HttpMessageConverter
 * @see EscapeUtils
 */
// NOTE: MappingJackson2HttpMessageConverter 鍦?Spring 7.0 宸插純鐢ㄥ苟鏍囪 forRemoval锛?// 寰呴」鐩粠 Jackson 2.x 杩佺Щ鑷?Jackson 3.x 鍚庢浛鎹负 JacksonJsonHttpMessageConverter
@SuppressWarnings("deprecation")
public class XssJsonMessageConverter extends MappingJackson2HttpMessageConverter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(XssJsonMessageConverter.class);

    /**
     * 杞崲鍣ㄤ紭鍏堢骇锛岃涓烘渶楂樹紭鍏堢骇纭繚 XSS 杩囨护鏈€鍏堟墽琛?     */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    /**
     * 鏀寔鐨勫獟浣撶被鍨嬪垪琛?     */
    private static final List<MediaType> SUPPORTED_MEDIA_TYPES = Arrays.asList(
            MediaType.APPLICATION_JSON,
            new MediaType("application", "*+json")
    );

    /**
     * 鏋勯€犳柟娉?     *
     * <p>浣跨敤榛樿閰嶇疆鍒涘缓 XSS 闃叉姢鐨勬秷鎭浆鎹㈠櫒銆?     */
    public XssJsonMessageConverter() {
        super();
    }

    /**
     * 鏋勯€犳柟娉?     *
     * <p>浣跨敤鎸囧畾鐨?ObjectMapper 鍒涘缓 XSS 闃叉姢鐨勬秷鎭浆鎹㈠櫒銆?     *
     * @param objectMapper 寰呬娇鐢ㄧ殑 ObjectMapper
     */
    public XssJsonMessageConverter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    /**
     * 閲嶅啓鏀寔鐨勫獟浣撶被鍨?     *
     * <p>杩斿洖姝よ浆鎹㈠櫒鏀寔鐨勫獟浣撶被鍨嬪垪琛細application/json 鍜?application/*+json
     *
     * @return 鏀寔鐨勫獟浣撶被鍨嬪垪琛?     */
    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return SUPPORTED_MEDIA_TYPES;
    }

    /**
     * 璇诲彇骞跺弽搴忓垪鍖?JSON 璇锋眰浣?     *
     * <p>閲嶅啓鐖剁被鏂规硶锛屽湪鍙嶅簭鍒楀寲鍓嶅 JSON 瀛楃涓插€艰繘琛?XSS 杩囨护銆?     * 浣跨敤 {@link EscapeUtils#cleanJsonValue} 杩涜娴佸紡 JSON 瑙ｆ瀽鍜屾竻娲楋紝
     * 纭繚浠呮竻娲楀瓧绗︿覆鍊硷紝涓嶇牬鍧?JSON 缁撴瀯銆?     *
     * @param type          鐩爣绫诲瀷
     * @param contextClass  涓婁笅鏂囩被
     * @param inputMessage  HTTP 杈撳叆娑堟伅
     * @return 鍙嶅簭鍒楀寲鍚庣殑瀵硅薄
     * @throws IOException                     IO寮傚父
     * @throws HttpMessageNotReadableException 娑堟伅涓嶅彲璇诲紓甯?     */
    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        byte[] originalBytes = inputMessage.getBody().readAllBytes();

        if (originalBytes == null || originalBytes.length == 0) {
            HttpInputMessage emptyInput = new XssByteArrayInputMessage(new byte[0], inputMessage.getHeaders());
            return super.read(type, contextClass, emptyInput);
        }

        String originalJson = new String(originalBytes, StandardCharsets.UTF_8);
        String cleanedJson = EscapeUtils.cleanJsonValue(originalJson);

        if (!cleanedJson.equals(originalJson)) {
            log.debug("[XssJsonMessageConverter] JSON Body XSS 杩囨护瀹屾垚");
        }

        byte[] cleanedBytes = cleanedJson.getBytes(StandardCharsets.UTF_8);
        HttpInputMessage cleanedInput = new XssByteArrayInputMessage(cleanedBytes, inputMessage.getHeaders());
        return super.read(type, contextClass, cleanedInput);
    }

    /**
     * 搴忓垪鍖栧璞′负 JSON 鍝嶅簲浣擄紙涓嶄慨鏀癸級
     */
    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotReadableException {
        super.writeInternal(object, outputMessage);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 鍩轰簬 ByteArrayInputStream 鐨?HttpInputMessage 瀹炵幇
     *
     * <p>鐢ㄤ簬鍖呰娓呮礂鍚庣殑 JSON 瀛楄妭鏁扮粍锛屼緵 Jackson 鍙嶅簭鍒楀寲浣跨敤銆?     */
    private static class XssByteArrayInputMessage implements HttpInputMessage {

        private final byte[] body;
        private final HttpHeaders headers;

        XssByteArrayInputMessage(byte[] body, HttpHeaders headers) {
            this.body = body;
            this.headers = headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}

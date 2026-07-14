package com.njydsz.pmis.common.util.saml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.njydsz.pmis.common.util.security.Base64Utils;
import com.njydsz.pmis.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * SAML 2.0 协议辅助工具类
 * <p>
 * 功能特性：
 * 1. SAMLRequest/SAMLResponse 编码解码（支持 Deflate 压缩）
 * 2. Base64/URL 编码解码
 * 3. XML 签名验证
 * 4. SAML 消息构建
 * 5. XXE 安全防护
 * <p>
 * 参考业界最佳实践（Spring Security、Apache Santuario 等）设计
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class SAMLUtils {

    private static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();
    
    private static final String XML_SIGNATURE_NS = "http://www.w3.org/2000/09/xmldsig#";

    private SAMLUtils() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    /**
     * 解析并解压 SAMLRequest / SAMLResponse
     *
     * @param samlStr 编码后的 SAML 字符串
     * @return 解压后的 XML 字符串，失败返回 null
     */
    public static String decodeAndInflateXML(String samlStr) {
        if (StringUtils.isBlank(samlStr)) {
            return null;
        }

        try {
            String urlDecoded = urlDecode(samlStr);
            byte[] decodedBytes = Base64Utils.decodeToBytes(urlDecoded);
            return inflateBytes(decodedBytes);
        } catch (Exception e) {
            log.error("SAMLUtils -> 解析并解压 SAML 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 压缩并编码 SAML XML
     *
     * @param xmlContent SAML XML 内容
     * @return 编码后的字符串，失败返回 null
     */
    public static String deflateAndEncode(String xmlContent) {
        if (StringUtils.isBlank(xmlContent)) {
            return null;
        }

        try {
            byte[] compressedBytes = deflateContent(xmlContent);
            String base64Encoded = Base64Utils.encode(compressedBytes);
            return urlEncode(base64Encoded);
        } catch (Exception e) {
            log.error("SAMLUtils -> 压缩并编码 SAML 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 仅 Base64 解码（不解压）
     *
     * @param samlStr 编码后的 SAML 字符串
     * @return 解码后的 XML 字符串，失败返回 null
     */
    public static String decodeBase64(String samlStr) {
        if (StringUtils.isBlank(samlStr)) {
            return null;
        }

        try {
            String urlDecoded = urlDecode(samlStr);
            byte[] decodedBytes = Base64Utils.decodeToBytes(urlDecoded);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("SAMLUtils -> Base64 解码失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 仅 Base64 编码（不压缩）
     *
     * @param xmlContent XML 内容
     * @return 编码后的字符串，失败返回 null
     */
    public static String encodeBase64(String xmlContent) {
        if (StringUtils.isBlank(xmlContent)) {
            return null;
        }

        try {
            byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);
            String base64Encoded = Base64Utils.encode(xmlBytes);
            return urlEncode(base64Encoded);
        } catch (Exception e) {
            log.error("SAMLUtils -> Base64 编码失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 验证 SAML 响应的 XML 签名
     *
     * @param samlResponse SAML Response XML 字符串
     * @param certificate  X509 证书（PEM 格式或 DER 格式）
     * @return 验证结果，true=成功，false=失败
     */
    public static boolean verifySignature(String samlResponse, String certificate) {
        if (StringUtils.isBlank(samlResponse) || StringUtils.isBlank(certificate)) {
            log.warn("SAMLUtils -> 验证签名失败：参数为空");
            return false;
        }

        try {
            Document doc = parseXML(samlResponse);
            if (doc == null) {
                log.error("SAMLUtils -> 验证签名失败：XML 解析失败");
                return false;
            }

            NodeList nl = doc.getElementsByTagNameNS(XML_SIGNATURE_NS, "Signature");
            if (nl.getLength() == 0) {
                log.warn("SAMLUtils -> 验证签名失败：未找到 Signature 元素");
                return false;
            }

            PublicKey publicKey = loadPublicKey(certificate);
            DOMValidateContext validateContext = new DOMValidateContext(publicKey, nl.item(0));
            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            XMLSignature signature = factory.unmarshalXMLSignature(validateContext);

            boolean coreValidity = signature.validate(validateContext);
            if (!coreValidity) {
                log.error("SAMLUtils -> 签名验证失败：核心验证不通过");
                return false;
            }

            log.info("SAMLUtils -> 签名验证成功");
            return true;
        } catch (Exception e) {
            log.error("SAMLUtils -> 验证签名异常：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 验证 SAML 响应的 XML 签名（使用证书文件路径）
     *
     * @param samlResponse SAML Response XML 字符串
     * @param certPath     证书文件路径
     * @return 验证结果，true=成功，false=失败
     */
    public static boolean verifySignatureWithCertFile(String samlResponse, String certPath) {
        if (StringUtils.isBlank(samlResponse) || StringUtils.isBlank(certPath)) {
            return false;
        }

        try {
            Path path = Paths.get(certPath);
            String certificate = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return verifySignature(samlResponse, certificate);
        } catch (Exception e) {
            log.error("SAMLUtils -> 从文件加载证书失败：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从 PEM 或 DER 格式加载公钥
     */
    private static PublicKey loadPublicKey(String certificate) throws Exception {
        String certContent = certificate
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");

        byte[] certBytes = Base64.getDecoder().decode(certContent);
        ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);
        return cert.getPublicKey();
    }

    /**
     * 解析 XML 字符串为 Document（安全版，防御 XXE）
     */
    private static Document parseXML(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setValidating(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource inputSource = new InputSource(new StringReader(xmlContent));
        return builder.parse(inputSource);
    }

    /**
     * URL 解码
     */
    private static String urlDecode(String str) {
        try {
            return URLDecoder.decode(str, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("URL 解码失败", e);
        }
    }

    /**
     * URL 编码
     */
    private static String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("URL 编码失败", e);
        }
    }

    /**
     * 解压字节数组
     */
    private static String inflateBytes(byte[] data) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             InflaterInputStream iis = new InflaterInputStream(bais, new Inflater(true));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            iis.transferTo(baos);
            return baos.toString(DEFAULT_ENCODING);
        }
    }

    /**
     * Deflate 压缩内容
     */
    private static byte[] deflateContent(String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.DEFLATED, true))) {
            dos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }
}

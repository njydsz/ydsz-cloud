package com.njydsz.common.util.ip;

/**
 * IP 信息查询工具类
 *
 * <p>基于 ip2region 离线库实现 IP 地址归属地查询，支持国家、省份、城市、运营商等信息解析。
 * 内置缓存机制，避免重复查询同一 IP 地址。
 *
 * <p>使用方式：
 * <pre>{@code
 * IpInfo ipInfo = IpInfoUtils.search("8.8.8.8");
 * log.info("Country: {}", ipInfo.getCountry());  // 美国
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.lionsoul.ip2region.xdb.Searcher;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IpInfoUtils {
    private static final int BUFFER_SIZE = 16384;
    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\|");
    private static final Pattern CACHE_KEY_PATTERN = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    
    private static volatile Searcher searcher;
    private static final Map<String, IpInfo> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;
    private static volatile boolean CACHE_ENABLED = true;
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);

    static {
        initializeSearcher();
    }

    private static void initializeSearcher() {
        try (InputStream inputStream = IpInfoUtils.class.getClassLoader().getResourceAsStream("ip2region.xdb")) {
            if (inputStream == null) {
                log.error("Critical error: ip2region.xdb not found in classpath");
                throw new RuntimeException("ip2region.xdb loading failed");
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            byte[] data = output.toByteArray();
            if (data.length == 0) {
                throw new IOException("Empty IP database file");
            }

            searcher = Searcher.newWithBuffer(data);
            log.info("IP database initialized successfully, size: {} bytes", data.length);
        } catch (Exception e) {
            log.error("IP database initialization failed", e);
            throw new RuntimeException("IP database initialization failed", e);
        }
    }

    public static IpInfo getInfo(String ip) {
        if (!IpAddrUtils.validIp(ip)) {
            log.warn("Invalid IP format detected: {}", ip);
            return createDefaultIpInfo(ip);
        }

        if (CACHE_ENABLED) {
            String cacheKey = getCacheKey(ip);
            IpInfo cached = CACHE.get(cacheKey);
            if (cached != null) {
                cacheHits.incrementAndGet();
                return copyIpInfo(cached);
            }
            cacheMisses.incrementAndGet();
        }

        IpInfo ipInfo = new IpInfo(ip);
        try {
            String regionStr = searcher.search(ip);
            if (regionStr != null && !regionStr.isEmpty()) {
                convert(regionStr, ipInfo);
            }

            if (CACHE_ENABLED) {
                String cacheKey = getCacheKey(ip);
                if (CACHE.size() < MAX_CACHE_SIZE) {
                    CACHE.put(cacheKey, copyIpInfo(ipInfo));
                }
            }
        } catch (Exception e) {
            log.error("IP search failed for: {}", ip, e);
        }
        return ipInfo;
    }

    public static List<IpInfo> batchGetInfo(List<String> ips) {
        if (ips == null || ips.isEmpty()) {
            return new ArrayList<>();
        }

        List<IpInfo> results = new ArrayList<>(ips.size());
        for (String ip : ips) {
            results.add(getInfo(ip));
        }
        return results;
    }

    public static <T> List<T> batchGetInfo(List<String> ips, Function<IpInfo, T> mapper) {
        if (ips == null || ips.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> results = new ArrayList<>(ips.size());
        for (String ip : ips) {
            results.add(mapper.apply(getInfo(ip)));
        }
        return results;
    }

    public static void clearCache() {
        CACHE.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        log.info("IP info cache cleared");
    }

    public static void setCacheEnabled(boolean enabled) {
        CACHE_ENABLED = enabled;
        log.info("IP info cache {}", enabled ? "enabled" : "disabled");
    }

    public static CacheStats getCacheStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        return new CacheStats(CACHE.size(), (int) hits, (int) misses, hitRate);
    }

    private static String getCacheKey(String ip) {
        if (CACHE_KEY_PATTERN.matcher(ip).matches()) {
            return ip;
        }
        return ip.replaceAll("[^0-9a-fA-F.]", "");
    }

    private static IpInfo copyIpInfo(IpInfo source) {
        IpInfo copy = new IpInfo(source.getIp());
        copy.setCountry(source.getCountry());
        copy.setRegion(source.getRegion());
        copy.setProvince(source.getProvince());
        copy.setCity(source.getCity());
        copy.setIsp(source.getIsp());
        return copy;
    }

    private static IpInfo createDefaultIpInfo(String ip) {
        IpInfo ipInfo = new IpInfo(ip);
        ipInfo.setCountry(null);
        ipInfo.setRegion(null);
        ipInfo.setProvince(null);
        ipInfo.setCity(null);
        ipInfo.setIsp(null);
        return ipInfo;
    }

    private static void convert(String regionStr, IpInfo ipInfo) {
        String[] regionSplit = SPLIT_PATTERN.split(regionStr);
        if (regionSplit.length != 5) {
            log.warn("Unexpected region format: {}", regionStr);
            return;
        }

        ipInfo.setCountry(filterZero(regionSplit[0]));
        ipInfo.setRegion(filterZero(regionSplit[1]));
        ipInfo.setProvince(filterZero(regionSplit[2]));
        ipInfo.setCity(filterZero(regionSplit[3]));
        ipInfo.setIsp(filterZero(regionSplit[4]));
    }

    private static String filterZero(String value) {
        return "0".equals(value) ? null : value;
    }

    @Setter
    @Getter
    @ToString
    public static class IpInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String country;
        private String region;
        private String province;
        private String city;
        private String isp;
        private final String ip;

        public IpInfo(String ip) {
            this.ip = ip;
        }

        public String getFullAddress() {
            StringBuilder sb = new StringBuilder();
            if (country != null) sb.append(country);
            if (region != null) sb.append(region);
            if (province != null) sb.append(province);
            if (city != null) sb.append(city);
            if (isp != null) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(isp);
            }
            return sb.length() > 0 ? sb.toString() : "未知";
        }

        public String getShortAddress() {
            StringBuilder sb = new StringBuilder();
            if (province != null) sb.append(province);
            if (city != null) sb.append(city);
            if (isp != null) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(isp);
            }
            return sb.length() > 0 ? sb.toString() : "未知";
        }
    }

    @Getter
    public static class CacheStats {
        private final int size;
        private final int hits;
        private final int misses;
        private final double hitRate;

        public CacheStats(int size, int hits, int misses, double hitRate) {
            this.size = size;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{size=%d, hits=%d, misses=%d, hitRate=%.2f%%}", 
                    size, hits, misses, hitRate);
        }
    }

}

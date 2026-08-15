package com.njydsz.common.util.ip;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * 本机网络接口枚举工具类。
 *
 * <p>提供本机 IP 地址、主机名的枚举和获取。
 * 自 1.4.0 起从原 {@code IpAddrUtils} 拆分为独立类。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public final class NetworkInterfaceUtils {

    private NetworkInterfaceUtils() {
        throw new UnsupportedOperationException("NetworkInterfaceUtils is a utility class and cannot be instantiated");
    }

    /**
     * 获取本机 IP 地址。
     *
     * @return 本机 IP，获取失败时返回 127.0.0.1
     */
    public static String getHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /**
     * 获取本机主机名。
     *
     * @return 主机名，获取失败时返回 "UnknownHost"
     */
    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "UnknownHost";
        }
    }

    /**
     * 枚举本机所有非回环、非虚拟且在线的网络接口 IP。
     *
     * @return 本机 IP 地址列表，可能为空
     */
    public static List<String> listLocalIps() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLinkLocalAddress() && !addr.isLoopbackAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to list local IPs", e);
        }
        return ips;
    }
}














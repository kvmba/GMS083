package org.gms.net.server.coordinator.session;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IpAddresses {
    private static final List<Pattern> LOCAL_ADDRESS_PATTERNS = loadLocalAddressPatterns();
    // docker默认网桥 172.17.0.0/16:容器内客户端与服务器同网络栈时按本机处理
    private static final Pattern DOCKER_BRIDGE_PATTERN = Pattern.compile("^172\\.17\\.");

    private static List<Pattern> loadLocalAddressPatterns() {
        return Stream.of("^10\\.", "^192\\.168\\.", "^172\\.(1[6-9]|2[0-9]|3[0-1])\\.")
                .map(Pattern::compile)
                .collect(Collectors.toList());
    }

    public static boolean isLocalAddress(String inetAddress) {
        return inetAddress.startsWith("127.");
    }

    /**
     * 是否为docker默认网桥网段(仅精确匹配172.17.0.0/16,不误伤172.18-31的其它私网)。
     */
    public static boolean isDockerBridgeAddress(String inetAddress) {
        return matchesPattern(DOCKER_BRIDGE_PATTERN, inetAddress);
    }

    public static boolean isLanAddress(String inetAddress) {
        return LOCAL_ADDRESS_PATTERNS.stream()
                .anyMatch(pattern -> matchesPattern(pattern, inetAddress));
    }

    private static boolean matchesPattern(Pattern pattern, String searchTerm) {
        return pattern.matcher(searchTerm).find();
    }
}

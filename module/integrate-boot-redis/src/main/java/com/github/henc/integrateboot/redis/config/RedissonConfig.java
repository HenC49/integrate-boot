package com.github.henc.integrateboot.redis.config;

import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis connection configuration for a single Redis instance. Bound under
 * {@code integrate-boot.redis.multi.<name>.*} for each extra instance.
 *
 * <p>The fields mirror Spring Boot's {@code spring.data.redis.*} properties so a multi-instance
 * entry reads the same way as the default configuration. {@link #toRedissonConfig()} translates
 * this POJO into a Redisson {@link Config}, dispatching to standalone / sentinel / cluster
 * server configuration based on what is populated.
 *
 * <p>In Redisson 4.x credentials (username / password) are set on the shared {@link Config}
 * rather than the per-topology server config; {@link #toRedissonConfig()} handles that split so
 * callers do not need to know the distinction.
 */
public class RedissonConfig {

    /** Default Redis host when none is specified. */
    public static final String DEFAULT_HOST = "localhost";

    /** Default Redis port when none is specified. */
    public static final int DEFAULT_PORT = 6379;

    /** Default Redis database index when none is specified. */
    public static final int DEFAULT_DATABASE = 0;

    private String host = DEFAULT_HOST;

    private int port = DEFAULT_PORT;

    /** Optional username (Redis 6+ ACL). */
    private String username;

    private String password;

    private int database = DEFAULT_DATABASE;

    /** Connection / command timeout. Redisson expects milliseconds. */
    private Duration timeout;

    /** Whether to use TLS ({@code rediss://}) for the connection. */
    private boolean ssl = false;

    private Sentinel sentinel;

    private Cluster cluster;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public Sentinel getSentinel() {
        return sentinel;
    }

    public void setSentinel(Sentinel sentinel) {
        this.sentinel = sentinel;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    /**
     * Build a Redisson {@link Config} from this connection configuration.
     *
     * <p>Dispatch rules:
     * <ul>
     *   <li>{@link Cluster} with nodes present → {@code useClusterServers()}</li>
     *   <li>{@link Sentinel} with master + nodes present → {@code useSentinelServers()}</li>
     *   <li>otherwise → {@code useSingleServer()}</li>
     * </ul>
     * Credentials are applied at the {@link Config} level, as Redisson 4.x expects.
     *
     * @return a ready-to-use Redisson configuration
     */
    @SuppressWarnings("deprecation")
    public Config toRedissonConfig() {
        Config config = new Config();
        if (username != null && !username.isEmpty()) {
            config.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
        int timeoutMs = timeout == null ? 0 : (int) timeout.toMillis();

        if (isCluster()) {
            ClusterServersConfig clusterConfig = config.useClusterServers();
            clusterConfig.setNodeAddresses(toAddresses(cluster.getNodes()));
            if (timeoutMs > 0) {
                clusterConfig.setTimeout(timeoutMs);
            }
            return config;
        }

        if (isSentinel()) {
            SentinelServersConfig sentinelConfig = config.useSentinelServers();
            sentinelConfig.setMasterName(sentinel.getMaster());
            sentinelConfig.setSentinelAddresses(toAddresses(sentinel.getNodes()));
            sentinelConfig.setDatabase(database);
            if (timeoutMs > 0) {
                sentinelConfig.setTimeout(timeoutMs);
            }
            return config;
        }

        SingleServerConfig single = config.useSingleServer();
        single.setAddress(toAddress(host, port));
        single.setDatabase(database);
        if (timeoutMs > 0) {
            single.setTimeout(timeoutMs);
        }
        return config;
    }

    private boolean isCluster() {
        return cluster != null && cluster.getNodes() != null && !cluster.getNodes().isEmpty();
    }

    private boolean isSentinel() {
        return sentinel != null
                && sentinel.getMaster() != null && !sentinel.getMaster().isEmpty()
                && sentinel.getNodes() != null && !sentinel.getNodes().isEmpty();
    }

    private String toAddress(String host, int port) {
        String scheme = ssl ? "rediss://" : "redis://";
        return scheme + host + ":" + port;
    }

    private List<String> toAddresses(List<String> nodes) {
        List<String> addresses = new ArrayList<>(nodes.size());
        String scheme = ssl ? "rediss://" : "redis://";
        for (String node : nodes) {
            // Allow users to write either "host:port" or a full "redis://host:port" URI.
            if (node.startsWith("redis://") || node.startsWith("rediss://")) {
                addresses.add(node);
            } else {
                addresses.add(scheme + node);
            }
        }
        return addresses;
    }

    /**
     * Sentinel mode configuration.
     */
    public static class Sentinel {

        /** Name of the master node monitored by the sentinels. */
        private String master;

        /** Sentinel addresses, as {@code host:port}. */
        private List<String> nodes;

        /** Optional sentinel-specific password. */
        private String password;

        public String getMaster() {
            return master;
        }

        public void setMaster(String master) {
            this.master = master;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * Cluster mode configuration.
     */
    public static class Cluster {

        /** Cluster node addresses, as {@code host:port}. */
        private List<String> nodes;

        /** Maximum number of redirects to follow when a slot is not owned by a node. */
        private int maxRedirects;

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes;
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }
    }
}

package com.github.henc.integrateboot.scheduling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("integrate-boot.scheduling")
public class SchedulingProperties {

    private boolean enabled;
    private final Executor executor = new Executor();
    private final Admin admin = new Admin();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Executor getExecutor() { return executor; }
    public Admin getAdmin() { return admin; }

    public static class Executor {
        private boolean enabled;
        private String adminAddresses;
        private String appName;
        private String accessToken;
        private String address;
        private String ip;
        private int port = 9999;
        private String logPath;
        private int logRetentionDays = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAdminAddresses() { return adminAddresses; }
        public void setAdminAddresses(String adminAddresses) { this.adminAddresses = adminAddresses; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getLogPath() { return logPath; }
        public void setLogPath(String logPath) { this.logPath = logPath; }
        public int getLogRetentionDays() { return logRetentionDays; }
        public void setLogRetentionDays(int logRetentionDays) { this.logRetentionDays = logRetentionDays; }
    }

    public static class Admin {
        private boolean enabled;
        private String accessToken;
        private String basePath = "/integrate/scheduling";
        private final Jdbc jdbc = new Jdbc();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
        public Jdbc getJdbc() { return jdbc; }
    }

    public static class Jdbc {
        private String url;
        private String username;
        private String password;
        private Duration lockTimeout = Duration.ofSeconds(30);

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public Duration getLockTimeout() { return lockTimeout; }
        public void setLockTimeout(Duration lockTimeout) { this.lockTimeout = lockTimeout; }
    }
}

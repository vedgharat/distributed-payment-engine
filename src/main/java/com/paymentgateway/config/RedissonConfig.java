package com.paymentgateway.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:redispassword}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        if (redisUrl != null && !redisUrl.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(redisUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                String userInfo = uri.getUserInfo();
                String password = null;
                if (userInfo != null) {
                    int colonIdx = userInfo.indexOf(':');
                    if (colonIdx >= 0) {
                        password = userInfo.substring(colonIdx + 1);
                    } else {
                        password = userInfo;
                    }
                }
                String scheme = uri.getScheme();
                if (scheme == null) {
                    scheme = "redis";
                }
                
                var serverConfig = config.useSingleServer()
                        .setAddress(String.format("%s://%s:%d", scheme, host, port))
                        .setConnectionPoolSize(64)
                        .setConnectionMinimumIdleSize(10)
                        .setConnectTimeout(3000)        // 3s to establish connection
                        .setRetryAttempts(3)
                        .setRetryInterval(1500);
                        
                if (password != null && !password.isEmpty()) {
                    serverConfig.setPassword(password);
                }
            } catch (java.net.URISyntaxException e) {
                throw new IllegalArgumentException("Invalid Redis URL: " + redisUrl, e);
            }
        } else {
            String protocol = sslEnabled ? "rediss://" : "redis://";
            
            config.useSingleServer()
                    .setAddress(String.format("%s%s:%d", protocol, redisHost, redisPort))
                    .setPassword(redisPassword)
                    // Connection pool settings — tune based on expected concurrency
                    .setConnectionPoolSize(64)
                    .setConnectionMinimumIdleSize(10)
                    .setConnectTimeout(3000)        // 3s to establish connection
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);
        }

        return Redisson.create(config);
    }
}
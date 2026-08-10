package com.moca.mocabe.global.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** MOCA opaque 세션을 저장할 Redis 연결을 구성한다. */
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(Environment environment) {
        String host = environment.getProperty("MOCA_REDIS_HOST", "localhost");
        int port = Integer.parseInt(environment.getProperty("MOCA_REDIS_PORT", "6379"));
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        int database = environment.getProperty("MOCA_REDIS_DATABASE", Integer.class, 0);
        configuration.setDatabase(database);
        String password = environment.getProperty("MOCA_REDIS_PASSWORD", "");
        if (!password.isEmpty()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(3))
                .shutdownTimeout(Duration.ofSeconds(3))
                .build();
        return new LettuceConnectionFactory(configuration, clientConfiguration);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}

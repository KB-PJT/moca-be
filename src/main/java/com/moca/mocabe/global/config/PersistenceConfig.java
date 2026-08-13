package com.moca.mocabe.global.config;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** MyBatis와 MySQL을 연결하는 Legacy Spring 설정이다. */
@Configuration
@EnableTransactionManagement
@MapperScan(basePackages = "com.moca.mocabe.domain", annotationClass = Mapper.class)
@PropertySource(value = "file:${MOCA_ENV_FILE:.env}", ignoreResourceNotFound = true)
public class PersistenceConfig {

    @Bean
    public DataSource dataSource(Environment environment) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(environment.getProperty(
                "MOCA_DB_URL", "jdbc:mysql://localhost:3307/moca?serverTimezone=UTC&characterEncoding=utf8"));
        config.setUsername(environment.getProperty("MOCA_DB_USERNAME", "moca"));
        config.setPassword(environment.getProperty("MOCA_DB_PASSWORD", ""));
        config.setMaximumPoolSize(environment.getProperty("MOCA_DB_POOL_MAX_SIZE", Integer.class, 10));
        config.setMinimumIdle(environment.getProperty("MOCA_DB_POOL_MIN_IDLE", Integer.class, 2));
        config.setConnectionTimeout(environment.getProperty("MOCA_DB_POOL_CONNECTION_TIMEOUT_MS", Long.class,
                3_000L));
        return new HikariDataSource(config);
    }

    /** MyBatis 초기화 전에 초기 스키마를 적용한다. */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    @DependsOn("flyway")
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml"));
        return factoryBean.getObject();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}

package com.moca.mocabe.global.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** MyBatis와 MySQL을 연결하는 Legacy Spring 설정이다. */
@Configuration
@EnableTransactionManagement
@MapperScan(basePackages = "com.moca.mocabe.domain")
@PropertySource(value = "file:${MOCA_ENV_FILE:.env}", ignoreResourceNotFound = true)
public class PersistenceConfig {

    @Bean
    public DataSource dataSource(Environment environment) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(environment.getProperty(
                "MOCA_DB_URL", "jdbc:mysql://localhost:3306/moca?serverTimezone=UTC&characterEncoding=utf8"));
        dataSource.setUsername(environment.getProperty("MOCA_DB_USERNAME", "moca"));
        dataSource.setPassword(environment.getProperty("MOCA_DB_PASSWORD", ""));
        return dataSource;
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

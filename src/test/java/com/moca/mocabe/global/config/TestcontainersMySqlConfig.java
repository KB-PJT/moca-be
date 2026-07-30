package com.moca.mocabe.global.config;

import com.moca.mocabe.domain.user.mapper.UserMapper;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL이 필요한 통합 테스트에서 공통으로 사용하는 Testcontainers 설정이다.
 * 컨테이너 시작, 마이그레이션 적용, MyBatis Mapper 등록을 이 설정에서 한 번만 처리한다.
 */
@Configuration
@MapperScan(basePackageClasses = UserMapper.class, sqlSessionFactoryRef = "testSqlSessionFactory")
public class TestcontainersMySqlConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("moca_test")
                .withUsername("moca")
                .withPassword("moca");
    }

    @Bean
    public DataSource dataSource(MySQLContainer mysqlContainer) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(mysqlContainer.getDriverClassName());
        dataSource.setUrl(mysqlContainer.getJdbcUrl());
        dataSource.setUsername(mysqlContainer.getUsername());
        dataSource.setPassword(mysqlContainer.getPassword());
        return dataSource;
    }

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
    public SqlSessionFactory testSqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml"));
        return factoryBean.getObject();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

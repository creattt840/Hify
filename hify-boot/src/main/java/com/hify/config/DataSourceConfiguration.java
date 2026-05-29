package com.hify.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(PgDataSourceProperties.class)
public class DataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfiguration.class);

    // ── MySQL 主数据源（@Primary，供 MyBatis-Plus 使用）──

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("HifyMySQLPool");
        return ds;
    }

    @Bean
    @Primary
    public SqlSessionFactory sqlSessionFactory(
            @Qualifier("dataSource") DataSource dataSource,
            MybatisPlusInterceptor mybatisPlusInterceptor,
            MetaObjectHandler metaObjectHandler) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);

        factory.setPlugins(mybatisPlusInterceptor);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(metaObjectHandler);
        factory.setGlobalConfig(globalConfig);

        return factory.getObject();
    }

    @Bean
    @Primary
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    // ── PostgreSQL 向量数据源 ──

    @Bean(name = "pgDataSource")
    public DataSource pgDataSource(PgDataSourceProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("HifyPgPool");
        ds.setJdbcUrl(props.getJdbcUrl());
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());
        ds.setDriverClassName(props.getDriverClassName());
        ds.setMaximumPoolSize(props.getMaximumPoolSize());
        ds.setConnectionTimeout(props.getConnectionTimeout());
        log.info("PostgreSQL 数据源已配置: url={}", props.getJdbcUrl());
        return ds;
    }

    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgDataSource") DataSource pgDataSource) {
        return new JdbcTemplate(pgDataSource);
    }

    @Bean(name = "pgTransactionManager")
    public PlatformTransactionManager pgTransactionManager(
            @Qualifier("pgDataSource") DataSource pgDataSource) {
        return new DataSourceTransactionManager(pgDataSource);
    }
}

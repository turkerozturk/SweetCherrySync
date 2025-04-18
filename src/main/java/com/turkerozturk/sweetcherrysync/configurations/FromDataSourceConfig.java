/*
 * This file is part of the SweetCherrySync project.
 * Please refer to the project's README.md file for additional details.
 * https://github.com/turkerozturk/SweetCherrySync
 *
 * Copyright (c) 2024 Turker Ozturk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.en.html>.
 */
package com.turkerozturk.sweetcherrysync.configurations;

import com.turkerozturk.sweetcherrysync.repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.turkerozturk.sweetcherrysync.repositories",
        entityManagerFactoryRef = "fromEntityManagerFactory",
        transactionManagerRef = "fromTransactionManager",
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {FromNodeRepository.class,
                        FromChildrenRepository.class,
                        FromGridRepository.class,
                        FromCodeBoxRepository.class,
                        FromBookmarkRepository.class,
                        FromImageRepository.class,
                }
        )
)
public class FromDataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(FromDataSourceConfig.class);

    @Value("${from.datasource.dialect}")
    private String dbDialect;

    @Value("${from.datasource.url}")
    private String fromDbUrl;

    @Value("${from.datasource.driver-class-name}")
    private String fromDbDriverClassName;

    @Bean(name = "fromDataSource")
    @Primary
    public DataSource fromDataSource() {
        return DataSourceBuilder.create()
                .url(fromDbUrl)
                .driverClassName(fromDbDriverClassName)
                .build();
    }

    @Bean(name = "fromEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean fromEntityManagerFactory(@Qualifier("fromDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);

        em.setPackagesToScan(new String[]{"com.turkerozturk.sweetcherrysync.entities"});

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", dbDialect);
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Bean(name = "fromTransactionManager")
    @Primary
    public PlatformTransactionManager fromTransactionManager(@Qualifier("fromEntityManagerFactory") LocalContainerEntityManagerFactoryBean fromEntityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(fromEntityManagerFactory.getObject());
        return transactionManager;
    }

    @Bean(name = "fromJdbcTemplate")
    @Primary
    public JdbcTemplate fromJdbcTemplate(@Qualifier("fromDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

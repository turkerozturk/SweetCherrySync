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
package com.turkerozturk.sweetcherrysync.my.configurations;

import com.turkerozturk.sweetcherrysync.my.repositories.MySettingsRepository;
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
        basePackages = "com.turkerozturk.sweetcherrysync.my.repositories",
        entityManagerFactoryRef = "myEntityManagerFactory",
        transactionManagerRef = "myTransactionManager",
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {MySettingsRepository.class,


                }
        )
)
public class MyDataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(MyDataSourceConfig.class);

    @Value("${my.datasource.dialect}")
    private String dbDialect;

    @Value("${my.datasource.url}")
    private String myDbUrl;

    @Value("${my.datasource.driver-class-name}")
    private String myDbDriverClassName;

    @Value("${my.datasource.username}")
    private String destinationDbUsername;

    @Value("${my.datasource.password}")
    private String destinationDbPassword;

    @Value("${my.datasource.ddl-auto}")
    private String ddlAuto;

    @Bean(name = "myDataSource")
    @Primary
    public DataSource myDataSource() {
        return DataSourceBuilder.create()
                .url(myDbUrl)
                .driverClassName(myDbDriverClassName)
                .username(destinationDbUsername)
                .password(destinationDbPassword)
                .build();
    }

    @Bean(name = "myEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean myEntityManagerFactory(@Qualifier("myDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);

        em.setPackagesToScan(new String[]{"com.turkerozturk.sweetcherrysync.my.entities"});

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        properties.put("hibernate.dialect", dbDialect);
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Bean(name = "myTransactionManager")
    @Primary
    public PlatformTransactionManager myTransactionManager(@Qualifier("myEntityManagerFactory") LocalContainerEntityManagerFactoryBean myEntityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(myEntityManagerFactory.getObject());
        return transactionManager;
    }

    @Bean(name = "myJdbcTemplate")
    @Primary
    public JdbcTemplate myJdbcTemplate(@Qualifier("myDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

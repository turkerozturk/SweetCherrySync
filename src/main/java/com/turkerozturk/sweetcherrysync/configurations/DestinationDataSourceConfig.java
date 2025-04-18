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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.turkerozturk.sweetcherrysync.repositories",
        entityManagerFactoryRef = "destinationEntityManagerFactory",
        transactionManagerRef = "destinationTransactionManager",
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {DestinationNodeRepository.class,
                        DestinationChildrenRepository.class,
                        DestinationGridRepository.class,
                        DestinationCodeBoxRepository.class,
                        DestinationBookmarkRepository.class,
                        DestinationImageRepository.class,
                }
        )
)
public class DestinationDataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DestinationDataSourceConfig.class);

    @Value("${destination.datasource.dialect}")
    private String dbDialect;

    @Value("${destination.datasource.url}")
    private String destinationDbUrl;

    @Value("${destination.datasource.driver-class-name}")
    private String destinationDbDriverClassName;

    @Value("${destination.datasource.username}")
    private String destinationDbUsername;

    @Value("${destination.datasource.password}")
    private String destinationDbPassword;

    @Value("${destination.datasource.schema}")
    private String destinationDbSchema;

    @Bean(name = "destinationDataSource")
    public DataSource destinationDataSource() {
        return DataSourceBuilder.create()
                .url(destinationDbUrl)
                .driverClassName(destinationDbDriverClassName)
                .username(destinationDbUsername)
                .password(destinationDbPassword)
                .build();
    }

    @Bean(name = "destinationEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean destinationEntityManagerFactory(@Qualifier("destinationDataSource") DataSource dataSource) {

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

    @Bean(name = "destinationTransactionManager")
    public PlatformTransactionManager destinationTransactionManager(@Qualifier("destinationEntityManagerFactory") LocalContainerEntityManagerFactoryBean destinationEntityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(destinationEntityManagerFactory.getObject());
        return transactionManager;
    }

    @Bean(name = "destinationJdbcTemplate")
    public JdbcTemplate destinationJdbcTemplate(@Qualifier("destinationDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Hedef
     * Veritabani var fakat tablolar yoksa, application.yml dosyasinda destination: altinda belirttigim schema dosyasina
     * gore tablolari olusturmaya kalkar.
     * @param dataSource
     * @return
     */
    @Bean
    public DataSourceInitializer dataSourceInitializer(@Qualifier("destinationDataSource") DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);




        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        //populator.addScript(new ClassPathResource(""));

        // bilgi tam sistem yolu verirken FileSystemResource kullanilir.
        Path targetFileFullPath = Path.of(Paths.get(".").toAbsolutePath().normalize().toString()
                + File.separator
                + destinationDbSchema);

        logger.info("targetFileFullPath" + targetFileFullPath.toString());
        populator.addScript(new FileSystemResource(targetFileFullPath));
        if(dbDialect.equals("org.hibernate.dialect.SQLServerDialect")) {
            populator.setSeparator("GO");
        }
        initializer.setDatabasePopulator(populator);

        return initializer;
    }
}

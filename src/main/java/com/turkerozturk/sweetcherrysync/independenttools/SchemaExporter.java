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
package com.turkerozturk.sweetcherrysync.independenttools;

import com.turkerozturk.sweetcherrysync.entities.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

// @Component
public class SchemaExporter {

    private static final String FROM_CREATION_SQL_FILE_NAME = "fromSchemaCreationScript.sql";
    private static final String DESTINATION_CREATION_SQL_FILE_NAME = "destinationSchemaCreationScript.sql";
    //@Value("${from.datasource.dialect}")
    private String fromDbDialect;
    //@Value("${from.datasource.url}")
    private String fromDbUrl;
    //@Value("${from.datasource.driver-class-name}")
    private String fromDbDriverClassName;
    //@Value("${from.datasource.username}")
    private String fromDbUsername;
    //@Value("${from.datasource.password}")
    private String fromDbPassword;
    //@Value("${destination.datasource.dialect}")
    private String destinationDbDialect;
    //@Value("${destination.datasource.url}")
    private String destinationDbUrl;
    //@Value("${destination.datasource.driver-class-name}")
    private String destinationDbDriverClassName;
    //@Value("${destination.datasource.username}")
    private String destinationDbUsername;
    //@Value("${destination.datasource.password}")
    private String destinationDbPassword;

    public static void main(String[] args) {

        File configFile = new File("src/main/resources/application.yml");

        if (!configFile.exists()) {
            System.out.println("application.yml bulunamadı. Lütfen aynı klasörde olduğundan emin olun.");
            return;
        }


        SchemaExporter schemaExporter = new SchemaExporter();

        schemaExporter.loadFromVariables(configFile);
        schemaExporter.exportSchemaOfFromDatabase();

        schemaExporter.loadDestinationVariables(configFile);
        schemaExporter.exportSchemaOfDestinationDatabase();
    }

    public void exportSchemaOfFromDatabase() {

        Map<String, Object> settings = new HashMap<>();

        //System.out.println(fromDbDialect);
        if (fromDbDialect != null) {
            settings.put("hibernate.dialect", fromDbDialect); // Veritabanı dialect
        }
        settings.put("hibernate.connection.driver_class", fromDbDriverClassName); // JDBC driver
        settings.put("hibernate.connection.url", fromDbUrl); // JDBC URL
        if (fromDbUsername != null) {
            settings.put("hibernate.connection.username", fromDbUsername); // Veritabanı kullanıcı adı
        }
        if (fromDbPassword != null) {
            settings.put("hibernate.connection.password", fromDbPassword); // Veritabanı şifresi
        }
        exportSchema(settings, FROM_CREATION_SQL_FILE_NAME);

    }

    public void exportSchemaOfDestinationDatabase() {

        Map<String, Object> settings = new HashMap<>();

        //System.out.println(destinationDbDialect);
        if (destinationDbDialect != null) {
            settings.put("hibernate.dialect", destinationDbDialect); // Veritabanı dialect
        }
        settings.put("hibernate.connection.driver_class", destinationDbDriverClassName); // JDBC driver
        settings.put("hibernate.connection.url", destinationDbUrl); // JDBC URL
        if (destinationDbUsername != null) {
            settings.put("hibernate.connection.username", destinationDbUsername); // Veritabanı kullanıcı adı
        }
        if (destinationDbPassword != null) {
            settings.put("hibernate.connection.password", destinationDbPassword); // Veritabanı şifresi
        }

        exportSchema(settings, DESTINATION_CREATION_SQL_FILE_NAME);

    }

    public void exportSchema(Map<String, Object> settings, String fileName) {

        if (new File(fileName).exists()) {
            System.out.println(fileName + " exits. Backup and delete it first. Cancelled recreation of " + fileName + ".");
            return;
        }

        ServiceRegistry serviceRegistry =
                new StandardServiceRegistryBuilder().applySettings(settings).build();

        MetadataSources metadataSources = new MetadataSources(serviceRegistry);
        metadataSources.addAnnotatedClass(Bookmark.class);
        metadataSources.addAnnotatedClass(Children.class);
        metadataSources.addAnnotatedClass(CodeBox.class);
        metadataSources.addAnnotatedClass(Grid.class);
        metadataSources.addAnnotatedClass(Image.class);
        metadataSources.addAnnotatedClass(Node.class);
        Metadata metadata = metadataSources.buildMetadata();

        SchemaExport schemaExport = new SchemaExport();
        schemaExport.setFormat(true);
        schemaExport.setOutputFile(fileName);
        schemaExport.createOnly(EnumSet.of(TargetType.SCRIPT), metadata);

        System.out.println(fileName + " created.");
        // bu dugumde anlattigim seyi oku tekrar http://localhost:8080/nodes/6661

        // CREATE TABLE satırlarını CREATE TABLE IF NOT EXISTS ILE DEGISTIR, post-process et
        Path path = Paths.get(fileName);
        Charset charset = StandardCharsets.UTF_8;

        String content = null;
        try {
            content = new String(Files.readAllBytes(path), charset);
            // CREATE TABLE'ları IF NOT EXISTS şeklinde değiştir
            content = content.replaceAll("(?i)CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ");

            // ALTER TABLE ADD CONSTRAINT için de benzer şekilde işlem yapılabilir
            Files.write(path, content.getBytes(charset));
            System.out.println("In " + fileName + ", CREATE TABLE strings are replaced with CREATE TABLE IF NOT EXISTS.");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("You need to adjust schema datatypes manually by editing the " + fileName);

    }

    private void loadDestinationVariables(File configFile) {

        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(configFile)) {
            Map<String, Object> data = yaml.load(in);
            Map<String, Object> destination = (Map<String, Object>) data.get("destination");
            Map<String, Object> datasource = (Map<String, Object>) destination.get("datasource");
            this.destinationDbDialect = (String) datasource.get("dialect");
            this.destinationDbUrl = (String) datasource.get("url");
            this.destinationDbDriverClassName = (String) datasource.get("driver-class-name");
            this.destinationDbUsername = (String) datasource.get("username");
            this.destinationDbPassword = String.valueOf(datasource.get("password"));
            System.out.println("Dialect: " + destinationDbDialect);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadFromVariables(File configFile) {

        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(configFile)) {
            Map<String, Object> data = yaml.load(in);
            Map<String, Object> from = (Map<String, Object>) data.get("from");
            Map<String, Object> datasource = (Map<String, Object>) from.get("datasource");
            this.fromDbDialect = (String) datasource.get("dialect");
            this.fromDbUrl = (String) datasource.get("url");
            this.fromDbDriverClassName = (String) datasource.get("driver-class-name");
            this.fromDbUsername = (String) datasource.get("username");
            this.fromDbPassword = String.valueOf(datasource.get("password"));
            System.out.println("Dialect: " + fromDbDialect);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}

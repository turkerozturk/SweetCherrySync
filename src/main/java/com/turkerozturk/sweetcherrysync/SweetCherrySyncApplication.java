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
package com.turkerozturk.sweetcherrysync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SweetCherrySyncApplication {

	private static final Logger logger = LoggerFactory.getLogger(SweetCherrySyncApplication.class);


	public static void main(String[] args) {
		SpringApplication.run(SweetCherrySyncApplication.class, args);
	}
/*
	@Bean
	CommandLineRunner commandLineRunner(MySettingsRepository repository) {
		return args -> {
			Setting setting = new Setting();
			setting.setId(1);
			setting.setKey("version");
			setting.setValue("1.0");
			repository.save(setting);

			repository.save(new Setting(2,"developer", "Türker Öztürk"));


		};
	}
*/

}

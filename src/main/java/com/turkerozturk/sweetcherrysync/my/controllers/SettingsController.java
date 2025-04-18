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
package com.turkerozturk.sweetcherrysync.my.controllers;


import com.turkerozturk.sweetcherrysync.my.entities.Setting;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class SettingsController {

    @Autowired
    private MyService myService;

    /**
     * anasayfa mainpage index sayfasi
     *
     * @param model
     * @return
     */
    @GetMapping("/")
    public String showSettings(Model model) {
        // Tüm ayarları çekelim
        List<Setting> settings = myService.getAllSettings();

        // key = setting.getKey()
        // value = setting.getValue()
        // şeklinde bir map'e dönüştürelim
        Map<String, String> settingsMap = settings.stream()
                .collect(Collectors.toMap(Setting::getKey, Setting::getValue));

        // Thymeleaf’e ekleyelim
        model.addAttribute("settingsMap", settingsMap);

        return "index";  // resources/templates/settings.html
    }
}


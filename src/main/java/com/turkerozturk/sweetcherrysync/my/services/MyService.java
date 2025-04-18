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
package com.turkerozturk.sweetcherrysync.my.services;

import com.turkerozturk.sweetcherrysync.my.entities.Setting;
import com.turkerozturk.sweetcherrysync.my.repositories.MySettingsRepository;
import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MyService {

    private static final Logger logger = LoggerFactory.getLogger(MyService.class);

    @Autowired
    MySettingsRepository mySettingsRepository;

    public List<Setting> getAllSettings() {
        return mySettingsRepository.findAll();
    }

    public void printAllSettings() {
        List<Setting> settings = mySettingsRepository.findAll();
        settings.forEach(setting -> {
            System.out.println("Key: " + setting.getKey() + ", Value: " + setting.getValue());
        });
    }

    public void increment(String keyName) {
        Setting setting = mySettingsRepository.findByKey(keyName)
                .orElseGet(() -> {
                    Setting newSetting = new Setting();
                    newSetting.setKey(keyName);
                    newSetting.setValue("0");
                    return newSetting;
                });

        int currentValue = Integer.parseInt(setting.getValue());
        setting.setValue(String.valueOf(currentValue + 1));

        mySettingsRepository.save(setting);

      //  logger.info("nodeCount updated to: " + setting.getValue());
    }

    public void set(String keyName, String value) {
        Optional<Setting> setting = mySettingsRepository.findByKey(keyName);

        if(setting.isEmpty()) {
            Setting newSetting = new Setting();
            newSetting.setKey(keyName);
            newSetting.setValue(value);
            mySettingsRepository.save(newSetting);
        } else {
            setting.get().setValue(value);
            mySettingsRepository.save(setting.get());

        }

        //  logger.info("nodeCount updated to: " + setting.getValue());
    }


    public boolean areValuesEqual(String keyName1, String keyName2) {
        Optional<Setting> setting1 = mySettingsRepository.findByKey(keyName1);
        Optional<Setting> setting2 = mySettingsRepository.findByKey(keyName2);
       // System.out.println(setting1.get().getValue() + " x " + keyName1);
       // System.out.println(setting2.get().getValue() + " x " + keyName2);

        if(setting1.get().getValue().equals(setting2.get().getValue())) {
            return true;
        }
        return false;
    }

    /**
     * resets counters
     */
    public void resetStatistics() {
        for (Statistics statistic : Statistics.values()) {
            String keyName = statistic.getValue();
            if (keyName.startsWith("created")
                    || keyName.startsWith("updated")
                    || keyName.startsWith("deleted")
                    || keyName.startsWith("from")
                    || keyName.startsWith("destination")) {
                set(keyName, "0");
            }
        }
    }


}

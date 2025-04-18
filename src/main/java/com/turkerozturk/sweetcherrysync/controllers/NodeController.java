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
package com.turkerozturk.sweetcherrysync.controllers;


import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.services.NodeService;
import com.turkerozturk.sweetcherrysync.services.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
public class NodeController {

    private static final Logger logger = LoggerFactory.getLogger(NodeController.class);

    @Autowired
    private MyService myService;

    @Autowired
    private SyncService syncService;

    @Autowired
    private NodeService nodeService;

    @GetMapping("/sync")
    public String syncDatabases() {
      //  nodeService.syncNodes(); // TODO examine and make orphan

        logger.info("Sync started manually by calling the <webserver>/sync URL.");


        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date dateStart = new Date();
        System.out.println(dateFormat.format(dateStart));

        myService.resetStatistics();
        syncService.syncNodes();

        return "The destination database is synchronized successfully!";
    }
}

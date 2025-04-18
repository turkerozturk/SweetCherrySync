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
package com.turkerozturk.sweetcherrysync.scheduler;

import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.services.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerConfig.class);

    @Autowired
    private MyService myService;

    @Autowired
    private SyncService syncService;

/*
    @Autowired
    private NodeService nodeService;
*/




/*
    @Autowired
    private ChildrenService childrenService;

    @Autowired
    private GridService gridService;

    @Autowired
    private CodeBoxService codeBoxService;

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private ImageService imageService;
    */



    /*
    @Scheduled(fixedRate = 10 * 1000) // dakikada bir
    public void printH2() {
        myService.printH2();
    }
    */

    //@Scheduled(fixedRate = 12 * 60 * 60 * 1000) // 12 saatte bir

    /**
     * Anotasyon üzerinden properties okumak için fixedDelayString en pratik çözümdür.
     * Eğer “derleme zamanı” yerine “çalışma zamanında” anlaşılan bir değer istiyorsanız,
     * fixedDelayString gibi SpEL ile çözüm yapmak daha nettir:
     * properties dosyasind yerlestirdigimiz my.scheduler.delayInSeconds degiskeninde elde ediyoruz.
     */
    @Scheduled(fixedDelayString = "#{${my.scheduler.delayInSeconds} * 1000}") // bir gorev bittikten n saniye sonra digeri calissin.
    public void syncDatabases() {




        logger.info("Sync started.");


        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date dateStart = new Date();
        System.out.println(dateFormat.format(dateStart));

        myService.resetStatistics();
        syncService.syncNodes();



        /*
        // tek node denemesi

        Node fromNode = nodeService.findById(2231).orElse(null);
        nodeServiceSmart.syncNode(fromNode);

         */

        Date dateEnd = new Date();
        System.out.println(dateFormat.format(dateEnd));
/*
        nodeService.syncNodes();
        logger.info("node table is synced.");

        childrenService.syncNodes();
        logger.info("children table is synced.");

        gridService.syncNodes();
        logger.info("grid table is synced.");

        codeBoxService.syncNodes();
        logger.info("codebox table is synced.");

        bookmarkService.syncNodes();
        logger.info("bookmark table is synced.");

        imageService.syncNodes();
        logger.info("image table is synced.");


 */

        logger.info("Sync finished.");

    }
}

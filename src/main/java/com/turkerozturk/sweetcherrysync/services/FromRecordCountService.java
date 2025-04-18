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
package com.turkerozturk.sweetcherrysync.services;


import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FromRecordCountService {

    private static final Logger logger = LoggerFactory.getLogger(FromRecordCountService.class);

    @Autowired
    private MyService myService;

    @Autowired
    private FromNodeRepository nodeRepository;

    @Autowired
    private FromChildrenRepository childrenRepository;

    @Autowired
    private FromBookmarkRepository bookmarkRepository;

    @Autowired
    private FromImageRepository imageRepository;

    @Autowired
    private FromGridRepository gridRepository;

    @Autowired
    private FromCodeBoxRepository codeBoxRepository;

    public void printAndSaveRecordCounts() {
        long fromNodeCount = nodeRepository.count();
        long fromChildrenCount = childrenRepository.count();
        long fromBookmarkCount = bookmarkRepository.count();
        long fromImageCount = imageRepository.count();
        long fromGridCount = gridRepository.count();
        long fromCodeBoxCount = codeBoxRepository.count();

        myService.set(Statistics.FROM_NODE_COUNT.getValue(), String.valueOf(fromNodeCount));
        myService.set(Statistics.FROM_CHILDREN_COUNT.getValue(), String.valueOf(fromChildrenCount));
        myService.set(Statistics.FROM_BOOKMARK_COUNT.getValue(), String.valueOf(fromBookmarkCount));
        myService.set(Statistics.FROM_IMAGE_COUNT.getValue(), String.valueOf(fromImageCount));
        myService.set(Statistics.FROM_GRID_COUNT.getValue(), String.valueOf(fromGridCount));
        myService.set(Statistics.FROM_CODE_BOX_COUNT.getValue(), String.valueOf(fromCodeBoxCount));

        logger.debug("From Node count: {}", fromNodeCount);
        logger.debug("From Children count: {}", fromChildrenCount);
        logger.debug("From Bookmark count: {}", fromBookmarkCount);
        logger.debug("From Image count: {}", fromImageCount);
        logger.debug("From Grid count: {}", fromGridCount);
        logger.debug("From CodeBox count: {}", fromCodeBoxCount);
    }
}

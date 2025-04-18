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
public class DestinationRecordCountService {

    private static final Logger logger = LoggerFactory.getLogger(DestinationRecordCountService.class);

    @Autowired
    private MyService myService;
    @Autowired
    private DestinationNodeRepository nodeRepository;

    @Autowired
    private DestinationChildrenRepository childrenRepository;

    @Autowired
    private DestinationBookmarkRepository bookmarkRepository;

    @Autowired
    private DestinationImageRepository imageRepository;

    @Autowired
    private DestinationGridRepository gridRepository;

    @Autowired
    private DestinationCodeBoxRepository codeBoxRepository;

    public void printAndSaveRecordCounts() {
        long destinationNodeCount = nodeRepository.count();
        long destinationChildrenCount = childrenRepository.count();
        long destinationBookmarkCount = bookmarkRepository.count();
        long destinationImageCount = imageRepository.count();
        long destinationGridCount = gridRepository.count();
        long destinationCodeBoxCount = codeBoxRepository.count();

        myService.set(Statistics.DESTINATION_NODE_COUNT.getValue(), String.valueOf(destinationNodeCount));
        myService.set(Statistics.DESTINATION_CHILDREN_COUNT.getValue(), String.valueOf(destinationChildrenCount));
        myService.set(Statistics.DESTINATION_BOOKMARK_COUNT.getValue(), String.valueOf(destinationBookmarkCount));
        myService.set(Statistics.DESTINATION_IMAGE_COUNT.getValue(), String.valueOf(destinationImageCount));
        myService.set(Statistics.DESTINATION_GRID_COUNT.getValue(), String.valueOf(destinationGridCount));
        myService.set(Statistics.DESTINATION_CODE_BOX_COUNT.getValue(), String.valueOf(destinationCodeBoxCount));

        logger.debug("Destination Node count: {}", destinationNodeCount);
        logger.debug("Destination Children count: {}", destinationChildrenCount);
        logger.debug("Destination Bookmark count: {}", destinationBookmarkCount);
        logger.debug("Destination Image count: {}", destinationImageCount);
        logger.debug("Destination Grid count: {}", destinationGridCount);
        logger.debug("Destination CodeBox count: {}", destinationCodeBoxCount);
    }
}

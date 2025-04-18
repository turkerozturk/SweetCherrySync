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

import com.turkerozturk.sweetcherrysync.entities.Bookmark;
import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.repositories.DestinationBookmarkRepository;
import com.turkerozturk.sweetcherrysync.repositories.FromBookmarkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookmarkService {

    private static final Logger logger = LoggerFactory.getLogger(BookmarkService.class);


    @Autowired
    private MyService myService;
    @Autowired
    FromBookmarkRepository fromBookmarkRepository;

    @Autowired
    DestinationBookmarkRepository destinationBookmarkRepository;

    public List<Bookmark> getAllFromNodes() {
        return fromBookmarkRepository.findAll();
    }

    public List<Bookmark> getAllDestinationNodes() {
        return destinationBookmarkRepository.findAll();
    }

    public void saveToDestination(Bookmark bookmark) {
        destinationBookmarkRepository.save(bookmark);
    }

    public void deleteFromDestination(Bookmark bookmark) {
        destinationBookmarkRepository.delete(bookmark);
    }

    public void syncNodes() {
        List<Bookmark> fromBookmarks = fromBookmarkRepository.findAll();
        List<Bookmark> destinationBookmarks = destinationBookmarkRepository.findAll();

        // Ekleme ve güncelleme işlemleri
        for (Bookmark fromBookmark : fromBookmarks) {
            Bookmark destinationBookmark = destinationBookmarkRepository.findById(fromBookmark.getNodeId()).orElse(null);
            boolean isNewBookmark = (destinationBookmark == null);
            if (isNewBookmark) {
                destinationBookmarkRepository.save(fromBookmark);
                myService.increment(Statistics.CREATED_BOOKMARK.getValue());

            } else {
                destinationBookmark.setSequence(fromBookmark.getSequence());
                destinationBookmarkRepository.save(destinationBookmark);
                myService.increment(Statistics.UPDATED_BOOKMARK.getValue());
            }
        }

        // Silme işlemi
        for (Bookmark destinationNode : destinationBookmarks) {
            Bookmark fromNode = fromBookmarkRepository.findById(destinationNode.getNodeId()).orElse(null);
            if (fromNode == null) {
                destinationBookmarkRepository.delete(destinationNode);
                myService.increment(Statistics.DELETED_BOOKMARK.getValue());
            }
        }
    }

    /**
     * Compare all ids in the table, sync missing ids on destination table.
     */
    public void syncAllMissingIds() {
        List<Integer> fromIds = fromBookmarkRepository.findAllIds();
        List<Integer> destinationIds = destinationBookmarkRepository.findAllIds();

        Set<Integer> destinationIdSet = new HashSet<>(destinationIds);

        List<Integer> missingIds = fromIds.stream()
                .filter(id -> !destinationIdSet.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            List<Bookmark> missingRows = fromBookmarkRepository.findAllById(missingIds);
            destinationBookmarkRepository.saveAll(missingRows);
            logger.info("Synced Missing Ids: " + missingIds);
        }
    }



}

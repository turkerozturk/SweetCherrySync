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

import com.turkerozturk.sweetcherrysync.entities.CombinedId;
import com.turkerozturk.sweetcherrysync.entities.Image;
import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.repositories.DestinationImageRepository;
import com.turkerozturk.sweetcherrysync.repositories.FromImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    @Autowired
    private MyService myService;
    @Autowired
    private FromImageRepository fromImageRepository;

    @Autowired
    private DestinationImageRepository destinationImageRepository;

    public List<Image> getAllFromNodes() {
        return fromImageRepository.findAll();
    }

    public List<Image> getAllDestinationNodes() {
        return destinationImageRepository.findAll();
    }

    public void saveToDestination(Image grid) {
        destinationImageRepository.save(grid);
    }

    public void deleteFromDestination(CombinedId combinedId) {
        destinationImageRepository.deleteById(combinedId);
    }

    public void syncCreateRecords(Integer nodeId) {
        List<Image> fromImages = fromImageRepository.findByIdNodeId(nodeId);
        for (Image fromImage : fromImages) {
            Image destinationImage = destinationImageRepository.findById(fromImage.getId()).orElse(null);
            if (destinationImage == null) {
                destinationImageRepository.save(fromImage);
                myService.increment(Statistics.CREATED_IMAGES.getValue());
            }
        }
    }

    public void syncUpdateRecords(Integer nodeId) {
        syncDeleteRecords(nodeId);
        syncCreateRecords(nodeId);
    }

    public void syncDeleteRecords(Integer nodeId) {
        List<Image> destinationImages = destinationImageRepository.findByIdNodeId(nodeId);
        for (Image destinationImage : destinationImages) {
            destinationImageRepository.delete(destinationImage);
            myService.increment(Statistics.DELETED_IMAGES.getValue());
        }
    }

    /**
     * Compare all ids in the table, sync missing ids on destination table.
     */
    public void syncAllMissingIds() {
        List<CombinedId> fromIds = fromImageRepository.findAllIds();
        List<CombinedId> destinationIds = destinationImageRepository.findAllIds();

        Set<CombinedId> destinationIdSet = new HashSet<>(destinationIds);

        List<CombinedId> missingIds = fromIds.stream()
                .filter(id -> !destinationIdSet.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            List<Image> missingRows = fromImageRepository.findAllById(missingIds);
            destinationImageRepository.saveAll(missingRows);
            logger.info("Synced Missing Ids: " + missingIds);
        }
    }

    @Deprecated
    public void syncImageRecords(Integer nodeId, boolean isAddOrUpdate) {
        if (isAddOrUpdate) {
            List<Image> fromImagees = fromImageRepository.findByIdNodeId(nodeId);
            for (Image fromImage : fromImagees) {
                Image destinationImage = destinationImageRepository.findById(fromImage.getId()).orElse(null);
                if (destinationImage == null) {
                    try {
                        System.out.println("(insert) id: " + fromImage.getId().getNodeId() + ", offset: " + fromImage.getId().getOffset() );
                        destinationImageRepository.save(fromImage);
                    } catch (DataIntegrityViolationException exception) {
                        logger.error(exception.getMessage());
                    }

                } else {
                    destinationImage.setJustification(fromImage.getJustification());
                    destinationImage.setAnchor(fromImage.getAnchor());
                    destinationImage.setPng(fromImage.getPng());
                    destinationImage.setFileName(fromImage.getFileName());
                    destinationImage.setLink(fromImage.getLink());
                    destinationImage.setTime(fromImage.getTime());
                    try {
                        System.out.println("(update) id: " + fromImage.getId().getNodeId() + ", offset: " + fromImage.getId().getOffset());
                        destinationImageRepository.save(fromImage);
                    } catch (DataIntegrityViolationException exception) {
                        logger.error(exception.getMessage());
                    }                }
            }
        } else {
            List<Image> destinationImages = destinationImageRepository.findByIdNodeId(nodeId);
            for (Image destinationImage : destinationImages) {
                System.out.println("(delete) nodeid: " + nodeId + ", image offset: " + destinationImage.getId().getOffset());
                destinationImageRepository.delete(destinationImage);
            }
        }
    }

    @Deprecated
    public void syncAllImageTable() {
        List<Image> fromImages = fromImageRepository.findAll();
        List<Image> destinationImages = destinationImageRepository.findAll();

        // Ekleme ve güncelleme işlemleri
        for (Image fromImage : fromImages) {
            CombinedId fromCombinedId = new CombinedId();
            fromCombinedId.setNodeId(fromImage.getId().getNodeId());
            fromCombinedId.setOffset(fromImage.getId().getOffset());

            Image destinationImage = destinationImageRepository.findById(fromCombinedId).orElse(null);
            if (destinationImage == null) {
                destinationImageRepository.save(fromImage);
            } else {
                destinationImage.setJustification(fromImage.getJustification());
                destinationImage.setAnchor(fromImage.getAnchor());
                destinationImage.setPng(fromImage.getPng());
                destinationImage.setFileName(fromImage.getFileName());
                destinationImage.setLink(fromImage.getLink());
                destinationImage.setTime(fromImage.getTime());
                destinationImageRepository.save(destinationImage);
            }
        }

        // Silme işlemi
        for (Image destinationImage : destinationImages) {
            CombinedId destinationCombinedId = new CombinedId();
            destinationCombinedId.setNodeId(destinationImage.getId().getNodeId());
            destinationCombinedId.setOffset(destinationImage.getId().getOffset());

            Image fromImage = fromImageRepository.findById(destinationCombinedId).orElse(null);
            if (fromImage == null) {
                destinationImageRepository.delete(destinationImage);
            }
        }
    }
}

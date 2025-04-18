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

import com.turkerozturk.sweetcherrysync.entities.CodeBox;
import com.turkerozturk.sweetcherrysync.entities.CombinedId;
import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.repositories.DestinationCodeBoxRepository;
import com.turkerozturk.sweetcherrysync.repositories.FromCodeBoxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CodeBoxService {

    private static final Logger logger = LoggerFactory.getLogger(CodeBoxService.class);

    @Autowired
    private MyService myService;
    @Autowired
    private FromCodeBoxRepository fromCodeBoxRepository;

    @Autowired
    private DestinationCodeBoxRepository destinationCodeBoxRepository;

    public List<CodeBox> getAllFromNodes() {
        return fromCodeBoxRepository.findAll();
    }

    public List<CodeBox> getAllDestinationNodes() {
        return destinationCodeBoxRepository.findAll();
    }

    public void saveToDestination(CodeBox codeBox) {
        destinationCodeBoxRepository.save(codeBox);
    }

    public void deleteFromDestination(CombinedId combinedId) {
        destinationCodeBoxRepository.deleteById(combinedId);
    }


    public void syncCreateRecords(Integer nodeId) {
        List<CodeBox> fromCodeBoxes = fromCodeBoxRepository.findByIdNodeId(nodeId);
        for (CodeBox fromCodeBox : fromCodeBoxes) {
            CodeBox destinationCodeBox = destinationCodeBoxRepository.findById(fromCodeBox.getId()).orElse(null);
            if (destinationCodeBox == null) {
                destinationCodeBoxRepository.save(fromCodeBox);
                myService.increment(Statistics.CREATED_CODE_BOXES.getValue());
            }
        }
    }

    public void syncUpdateRecords(Integer nodeId) {
        syncDeleteRecords(nodeId);
        syncCreateRecords(nodeId);
    }

    public void syncDeleteRecords(Integer nodeId) {
        List<CodeBox> destinationCodeBoxes = destinationCodeBoxRepository.findByIdNodeId(nodeId);
        for (CodeBox destinationCodeBox : destinationCodeBoxes) {
            destinationCodeBoxRepository.delete(destinationCodeBox);
            myService.increment(Statistics.DELETED_CODE_BOXES.getValue());
        }
    }

    /**
     * Compare all ids in the table, sync missing ids on destination table.
     */
    public void syncAllMissingIds() {
        List<CombinedId> fromIds = fromCodeBoxRepository.findAllIds();
        List<CombinedId> destinationIds = destinationCodeBoxRepository.findAllIds();

        Set<CombinedId> destinationIdSet = new HashSet<>(destinationIds);

        List<CombinedId> missingIds = fromIds.stream()
                .filter(id -> !destinationIdSet.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            List<CodeBox> missingRows = fromCodeBoxRepository.findAllById(missingIds);
            destinationCodeBoxRepository.saveAll(missingRows);
            logger.info("Synced Missing Ids: " + missingIds);
        }
    }

    @Deprecated
    public void syncCodeBoxRecords(Integer nodeId, boolean isAddOrUpdate) {
        if (isAddOrUpdate) {
            List<CodeBox> fromCodeBoxes = fromCodeBoxRepository.findByIdNodeId(nodeId);
            for (CodeBox fromCodeBox : fromCodeBoxes) {
                CodeBox destinationCodeBox = destinationCodeBoxRepository.findById(fromCodeBox.getId()).orElse(null);
                if (destinationCodeBox == null) {
                    destinationCodeBoxRepository.save(fromCodeBox);
                } else {
                    destinationCodeBox.setJustification(fromCodeBox.getJustification());
                    destinationCodeBox.setTxt(fromCodeBox.getTxt());
                    destinationCodeBox.setSyntax(fromCodeBox.getSyntax());
                    destinationCodeBox.setWidth(fromCodeBox.getWidth());
                    destinationCodeBox.setHeight(fromCodeBox.getHeight());
                    destinationCodeBox.setIsWidthPix(fromCodeBox.getIsWidthPix());
                    destinationCodeBox.setDoHighlBra(fromCodeBox.getDoHighlBra());
                    destinationCodeBox.setDoShowLinenum(fromCodeBox.getDoShowLinenum());
                    destinationCodeBoxRepository.save(destinationCodeBox);
                }
            }
        } else {
            List<CodeBox> destinationGrids = destinationCodeBoxRepository.findByIdNodeId(nodeId);
            for (CodeBox destinationGrid : destinationGrids) {
                destinationCodeBoxRepository.delete(destinationGrid);
            }
        }
    }

    @Deprecated
    public void syncNodes() {
        List<CodeBox> fromCodeBoxes = fromCodeBoxRepository.findAll();
        List<CodeBox> destinationCodeBoxes = destinationCodeBoxRepository.findAll();

        // Ekleme ve güncelleme işlemleri
        for (CodeBox fromCodeBox : fromCodeBoxes) {
            CombinedId fromCombinedId = new CombinedId();
            fromCombinedId.setNodeId(fromCodeBox.getId().getNodeId());
            fromCombinedId.setOffset(fromCodeBox.getId().getOffset());

            CodeBox destinationCodeBox = destinationCodeBoxRepository.findById(fromCombinedId).orElse(null);
            if (destinationCodeBox == null) {
                destinationCodeBoxRepository.save(fromCodeBox);
            } else {
                destinationCodeBox.setJustification(fromCodeBox.getJustification());
                destinationCodeBox.setTxt(fromCodeBox.getTxt());
                destinationCodeBox.setSyntax(fromCodeBox.getSyntax());
                destinationCodeBox.setWidth(fromCodeBox.getWidth());
                destinationCodeBox.setHeight(fromCodeBox.getHeight());
                destinationCodeBox.setIsWidthPix(fromCodeBox.getIsWidthPix());
                destinationCodeBox.setDoHighlBra(fromCodeBox.getDoHighlBra());
                destinationCodeBox.setDoShowLinenum(fromCodeBox.getDoShowLinenum());
                destinationCodeBoxRepository.save(destinationCodeBox);
            }
        }

        // Silme işlemi
        for (CodeBox destinationCodeBox : destinationCodeBoxes) {
            CombinedId destinationCombinedId = new CombinedId();
            destinationCombinedId.setNodeId(destinationCodeBox.getId().getNodeId());
            destinationCombinedId.setOffset(destinationCodeBox.getId().getOffset());

            CodeBox fromGrid = fromCodeBoxRepository.findById(destinationCombinedId).orElse(null);
            if (fromGrid == null) {
                destinationCodeBoxRepository.delete(destinationCodeBox);
            }
        }
    }
}

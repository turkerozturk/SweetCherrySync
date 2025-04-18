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
import com.turkerozturk.sweetcherrysync.entities.Grid;
import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.repositories.DestinationGridRepository;
import com.turkerozturk.sweetcherrysync.repositories.FromGridRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GridService {

    private static final Logger logger = LoggerFactory.getLogger(GridService.class);

    @Autowired
    private MyService myService;
    @Autowired
    private FromGridRepository fromGridRepository;

    @Autowired
    private DestinationGridRepository destinationGridRepository;

    public List<Grid> getAllFromNodes() {
        return fromGridRepository.findAll();
    }

    public List<Grid> getAllDestinationNodes() {
        return destinationGridRepository.findAll();
    }

    public void saveToDestination(Grid grid) {
        destinationGridRepository.save(grid);
    }

    public void deleteFromDestination(CombinedId combinedId) {
        destinationGridRepository.deleteById(combinedId);
    }


    public void syncCreateRecords(Integer nodeId) {
        List<Grid> fromGrids = fromGridRepository.findByIdNodeId(nodeId);
        for (Grid fromGrid : fromGrids) {
            Grid destinationGrid = destinationGridRepository.findById(fromGrid.getId()).orElse(null);
            if (destinationGrid == null) {
                destinationGridRepository.save(fromGrid);
                myService.increment(Statistics.CREATED_GRIDS.getValue());

            }
        }
    }
    public void syncUpdateRecords(Integer nodeId) {
        syncDeleteRecords(nodeId);
        syncCreateRecords(nodeId);
    }

    public void syncDeleteRecords(Integer nodeId) {
        List<Grid> destinationGrids = destinationGridRepository.findByIdNodeId(nodeId);
        for (Grid destinationGrid : destinationGrids) {
            destinationGridRepository.delete(destinationGrid);
            myService.increment(Statistics.DELETED_GRIDS.getValue());

        }
    }

    /**
     * Compare all ids in the table, sync missing ids on destination table.
     */
    public void syncAllMissingIds() {
        List<CombinedId> fromIds = fromGridRepository.findAllIds();
        List<CombinedId> destinationIds = destinationGridRepository.findAllIds();

        Set<CombinedId> destinationIdSet = new HashSet<>(destinationIds);

        List<CombinedId> missingIds = fromIds.stream()
                .filter(id -> !destinationIdSet.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            List<Grid> missingRows = fromGridRepository.findAllById(missingIds);
            destinationGridRepository.saveAll(missingRows);
            logger.info("Synced Missing Ids: " + missingIds);
        }
    }

    @Deprecated
    public void syncGridRecords(Integer nodeId, boolean isAddOrUpdate) {
        if (isAddOrUpdate) {
            List<Grid> fromGrids = fromGridRepository.findByIdNodeId(nodeId);
            for (Grid fromGrid : fromGrids) {
                Grid destinationGrid = destinationGridRepository.findById(fromGrid.getId()).orElse(null);
                if (destinationGrid == null) {
                    destinationGridRepository.save(fromGrid);
                } else {
                    destinationGrid.setJustification(fromGrid.getJustification());
                    destinationGrid.setTxt(fromGrid.getTxt());
                    destinationGrid.setColMin(fromGrid.getColMin());
                    destinationGrid.setColMax(fromGrid.getColMax());
                    destinationGridRepository.save(destinationGrid);
                }
            }
        } else {
            List<Grid> destinationGrids = destinationGridRepository.findByIdNodeId(nodeId);
            for (Grid destinationGrid : destinationGrids) {
                destinationGridRepository.delete(destinationGrid);
            }
        }
    }

    @Deprecated
    public void syncNodes() {
        List<Grid> fromGrids = fromGridRepository.findAll();
        List<Grid> destinationGrids = destinationGridRepository.findAll();

        // Ekleme ve güncelleme işlemleri
        for (Grid fromGrid : fromGrids) {
            CombinedId fromCombinedId = new CombinedId();
            fromCombinedId.setNodeId(fromGrid.getId().getNodeId());
            fromCombinedId.setOffset(fromGrid.getId().getOffset());

            Grid destinationGrid = destinationGridRepository.findById(fromCombinedId).orElse(null);
            if (destinationGrid == null) {
                destinationGridRepository.save(fromGrid);
            } else {
                destinationGrid.setTxt(fromGrid.getTxt());
                destinationGrid.setJustification(fromGrid.getJustification());
                destinationGrid.setColMin(fromGrid.getColMin());
                destinationGrid.setColMax(fromGrid.getColMax());
                destinationGridRepository.save(destinationGrid);
            }
        }

        // Silme işlemi
        for (Grid destinationGrid : destinationGrids) {
            CombinedId destinationCombinedId = new CombinedId();
            destinationCombinedId.setNodeId(destinationGrid.getId().getNodeId());
            destinationCombinedId.setOffset(destinationGrid.getId().getOffset());

            Grid fromGrid = fromGridRepository.findById(destinationCombinedId).orElse(null);
            if (fromGrid == null) {
                destinationGridRepository.delete(destinationGrid);
            }
        }
    }






}

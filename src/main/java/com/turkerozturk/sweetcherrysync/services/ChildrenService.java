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

import com.turkerozturk.sweetcherrysync.entities.Children;
import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.repositories.DestinationChildrenRepository;
import com.turkerozturk.sweetcherrysync.repositories.FromChildrenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChildrenService {

    private static final Logger logger = LoggerFactory.getLogger(ChildrenService.class);

    @Autowired
    FromChildrenRepository fromChildrenRepository;

    @Autowired
    DestinationChildrenRepository destinationChildrenRepository;

    @Autowired
    MyService myService;

    public List<Children> getAllFromNodes() {
        return fromChildrenRepository.findAll();
    }

    public List<Children> getAllDestinationNodes() {
        return destinationChildrenRepository.findAll();
    }

    public void saveToDestination(Children children) {
        destinationChildrenRepository.save(children);
    }

    public void deleteFromDestination(Children children) {
        destinationChildrenRepository.delete(children);
    }

    public void syncSharedNodesInRealityAllChildrenTable() {
        List<Children> fromChildrens = fromChildrenRepository.findAll();
        List<Children> destinationChildrens = destinationChildrenRepository.findAll();

        // Ekleme ve güncelleme işlemleri
        for (Children fromChildren : fromChildrens) {
            Optional<Children> destinationChildren = destinationChildrenRepository.findById(fromChildren.getNodeId());//.orElse(null);
            if (destinationChildren.isEmpty()) {
                System.out.println("AAAAAAAAAA: " + fromChildren.getNodeId());
                    destinationChildrenRepository.save(fromChildren);

            } else {
                Children destinationChildrennn = destinationChildren.get();

                destinationChildrennn.setFatherId(fromChildren.getFatherId());
                destinationChildrennn.setSequence(fromChildren.getSequence());
                destinationChildrennn.setMasterId(fromChildren.getMasterId());
                destinationChildrenRepository.save(destinationChildrennn);

            }

            /*
            Map<String, Object[]> changes = getWhatIsChangedInChildrenTable(fromChildren, destinationChildren);
            for (Map.Entry<String, Object[]> entry : changes.entrySet()) {
                String value = entry.getValue()[0].toString();
                System.out.println("Children diff node_id:  " + fromChildren.getNodeId());
            }
            */

        }

        // Silme işlemi
        for (Children destinationChildren : destinationChildrens) {
            Children fromChildren = fromChildrenRepository.findById(destinationChildren.getNodeId()).orElse(null);
            if (fromChildren == null) {
                destinationChildrenRepository.delete(destinationChildren);
            }
        }
    }


    public void syncCreateRow(Integer nodeId) {
        Children fromChildren = fromChildrenRepository.findById(nodeId).orElse(null);
        Children destinationChildren = destinationChildrenRepository.findById(nodeId).orElse(null);
        if (destinationChildren == null) {
            destinationChildrenRepository.save(fromChildren);
            myService.increment(Statistics.CREATED_CHILDREN.getValue());

        } else {
            logger.warn("burada kayit olmamasi gerekirdi! " + nodeId);
        }
    }

    public void syncUpdateRow(Integer nodeId) {
        syncDeleteRow(nodeId);
        syncCreateRow(nodeId);
    }

    public void syncDeleteRow(Integer nodeId) {
        Children destinationChildren = destinationChildrenRepository.findById(nodeId).orElse(null);
        if (destinationChildren != null) {
            destinationChildrenRepository.delete(destinationChildren);
            myService.increment(Statistics.DELETED_CHILDREN.getValue());
        } else {
            logger.warn("burada kayit olmasi gerekirdi! " + nodeId);
        }
        logger.info("delete row");

    }

    public void updateChangedIdNumbers() {
        List<Children> fromChildrens = fromChildrenRepository.findAll();
       // List<Children> destinationChildrens = destinationChildrenRepository.findAll();

        for (Children fromChildren : fromChildrens) {
            Children destinationChildren = destinationChildrenRepository.findById(fromChildren.getNodeId()).orElse(null);
            if (destinationChildren == null) {
                // destinationChildrenRepository.save(fromChildren);
            } else {
                destinationChildren.setFatherId(fromChildren.getFatherId());
                destinationChildren.setSequence(fromChildren.getSequence());
                destinationChildren.setMasterId(fromChildren.getMasterId());
                destinationChildrenRepository.save(destinationChildren);
                myService.increment(Statistics.UPDATED_CHILDREN.getValue());

            }
        }

    }

    /**
     * Compare all ids in the table, sync missing ids on destination table.
     */
    public void syncAllMissingIds() {
        List<Integer> fromIds = fromChildrenRepository.findAllIds();
        List<Integer> destinationIds = destinationChildrenRepository.findAllIds();

        Set<Integer> destinationIdSet = new HashSet<>(destinationIds);

        List<Integer> missingIds = fromIds.stream()
                .filter(id -> !destinationIdSet.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            List<Children> missingRows = fromChildrenRepository.findAllById(missingIds);
            destinationChildrenRepository.saveAll(missingRows);
            logger.info("Synced Missing Ids: " + missingIds);
        }
    }


    public static Map<String, Object[]> getWhatIsChangedInChildrenTable(Children fromChildren, Children destinationChildren) {
        Map<String, Object[]> changes = new HashMap<>();

        if (fromChildren == null || destinationChildren == null) {
            throw new IllegalArgumentException("Both Children objects must be non-null");
        }

        if (!fromChildren.getNodeId().equals(destinationChildren.getNodeId())) {
            changes.put("nodeId", new Object[]{fromChildren.getNodeId(), destinationChildren.getNodeId()});
        }

        if (fromChildren.getFatherId() != destinationChildren.getFatherId()) {
            changes.put("fatherId", new Object[]{fromChildren.getFatherId(), destinationChildren.getFatherId()});
        }

        if (fromChildren.getMasterId() != null ? !fromChildren.getMasterId().equals(destinationChildren.getMasterId()) : destinationChildren.getMasterId() != null) {
            changes.put("masterId", new Object[]{fromChildren.getMasterId(), destinationChildren.getMasterId()});
        }

        if (fromChildren.getSequence() != destinationChildren.getSequence()) {
            changes.put("sequence", new Object[]{fromChildren.getSequence(), destinationChildren.getSequence()});
        }

        return changes;
    }



    /*
     else {
            destinationChildren.setFatherId(fromChildren.getFatherId());
            destinationChildren.setSequence(fromChildren.getSequence());
            destinationChildren.setMasterId(fromChildren.getMasterId());
            destinationChildrenRepository.save(destinationChildren);
        }
     */


}

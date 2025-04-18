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


import com.turkerozturk.sweetcherrysync.entities.Node;
import com.turkerozturk.sweetcherrysync.repositories.DestinationNodeRepository;
import com.turkerozturk.sweetcherrysync.repositories.FromNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NodeService {
/*
    @Autowired
    @Qualifier("fromNodeRepository")
    private NodeRepository fromRepository;

    @Autowired
    @Qualifier("destinationNodeRepository")
    private NodeRepository destinationRepository;
*/


    private static final Logger logger = LoggerFactory.getLogger(NodeService.class);

    @Autowired
    private FromNodeRepository fromNodeRepository;

    @Autowired
    private DestinationNodeRepository destinationNodeRepository;



    public List<Node> getAllFromNodes() {
        return fromNodeRepository.findAll();
    }

    public List<Node> getAllDestinationNodes() {
        return destinationNodeRepository.findAll();
    }

    public void saveToDestination(Node node) {
        destinationNodeRepository.save(node);
    }

    public void deleteFromDestination(Node node) {
        destinationNodeRepository.delete(node);
    }

    /**
     * Compare all ids in the table, sync missing ids on destination table.
     */
    public void syncAllMissingIds() {
        List<Integer> fromIds = fromNodeRepository.findAllIds();
        List<Integer> destinationIds = destinationNodeRepository.findAllIds();

        Set<Integer> destinationIdSet = new HashSet<>(destinationIds);

        List<Integer> missingIds = fromIds.stream()
                .filter(id -> !destinationIdSet.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            List<Node> missingRows = fromNodeRepository.findAllById(missingIds);
            destinationNodeRepository.saveAll(missingRows);
            logger.info("Synced Missing Ids: " + missingIds);
        }
    }

    @Deprecated
    public void syncNodes() {
        List<Node> fromNodes = fromNodeRepository.findAll();
        List<Node> destinationNodes = destinationNodeRepository.findAll();

        // Ekleme ve güncelleme işlemleri
        for (Node fromNode : fromNodes) {
            Node destinationNode = destinationNodeRepository.findById(fromNode.getNodeId()).orElse(null);
            if (destinationNode == null) {
                destinationNodeRepository.save(fromNode);
            } else if (!fromNode.getTsLastsave().equals(destinationNode.getTsLastsave())) {
                destinationNode.setName(fromNode.getName());
                destinationNode.setTxt(fromNode.getTxt());
                destinationNode.setSyntax(fromNode.getSyntax());
                destinationNode.setTags(fromNode.getTags());
                destinationNode.setIsRo(fromNode.getIsRo());
                destinationNode.setIsRichtxt(fromNode.getIsRichtxt());
                destinationNode.setHasCodebox(fromNode.getHasCodebox());
                destinationNode.setHasTable(fromNode.getHasTable());
                destinationNode.setHasImage(fromNode.getHasImage());
                destinationNode.setLevel(fromNode.getLevel());
                destinationNode.setTsCreation(fromNode.getTsCreation());
                destinationNode.setTsLastsave(fromNode.getTsLastsave());
                destinationNodeRepository.save(destinationNode);
            }
        }

        // Silme işlemi
        for (Node destinationNode : destinationNodes) {
            Node fromNode = fromNodeRepository.findById(destinationNode.getNodeId()).orElse(null);
            if (fromNode == null) {
                destinationNodeRepository.delete(destinationNode);
            }
        }
    }

    public Optional<Node> findById(int i) {

        return fromNodeRepository.findById(i);
    }
}

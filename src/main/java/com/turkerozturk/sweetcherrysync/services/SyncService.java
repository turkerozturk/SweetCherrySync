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
import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.repositories.DestinationNodeRepository;
import com.turkerozturk.sweetcherrysync.repositories.FromNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncService {

    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);

    @Autowired
    private FromNodeRepository fromRepository;

    @Autowired
    private DestinationNodeRepository destinationRepository;

    @Autowired
    private GridService gridService;

    @Autowired
    private CodeBoxService codeBoxService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private ChildrenService childrenService;

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private MyService myService;

    @Autowired
    private FromRecordCountService fromRecordCountService;

    @Autowired
    private DestinationRecordCountService destinationRecordCountService;

    public List<Node> getAllFromNodes() {
        return fromRepository.findAll();
    }

    public List<Node> getAllDestinationNodes() {
        return destinationRepository.findAll();
    }

    public void saveToDestination(Node node) {
        destinationRepository.save(node);
    }

    public void deleteFromDestination(Node node) {
        destinationRepository.delete(node);
    }

    public void syncNode(Node fromNode) {


        // Ekleme ve güncelleme işlemleri

        syncCreateOrUpdateNodeWithRelationships(fromNode);


        // Silme işlemi

        //   syncDeleteNodeWithRelationships(destinationNode);


    }


    /**
     *
     */
    public void syncNodes() {


        // bilgi: Yeni eklenmis veya guncellenmis dugumleri siralamada en basa alarak getiriyoruz.
        logger.info("başla fromRepository.findAllOrderedByNodeIdAndTsLastsave");
        List<Node> fromNodes = fromRepository.findAllOrderedByNodeIdAndTsLastsave();

        logger.info("başla destinationRepository.findAllOrderedByNodeIdAndTsLastsave");
        List<Node> destinationNodes = destinationRepository.findAllOrderedByNodeIdAndTsLastsave();


        logger.info("başla syncNodes");

        // bilgi: Bookmark sayısı az olduğuudan bağımsız tablo olarak senkronize etmeyi tercih ettim.
        bookmarkService.syncNodes();

        // bilgi: diger sync işlemleri node tablosu ile bağlantılı olarak yapılıyor çünkü sadece onda primary id ve
        //  güncelleme tarihi var.
        logger.info("başla Node fromNode : fromNodes");

        // bilgi: önce kaynakta yeni veya güncellenmiş düğüm varsa hedefi senkronize ediyoruz.
        // Ekleme ve güncelleme işlemleri
        for (Node fromNode : fromNodes) {
//logger.info("f: " + fromNode.getNodeId());
//            logger.info("d: " + fromNode.getNodeId());
            myService.increment(Statistics.NODE_COUNTER.getValue());

            syncNode(fromNode);

        }
        logger.info("başla Node destinationNode : destinationNodes");

        // bilgi: sonra hedefte fazla node ler varsa onları ve bağlantılı tablolardaki verileri siliyoruz.
        // Silme işlemi
        for (Node destinationNode : destinationNodes) {

            syncDeleteNodeWithRelationships(destinationNode);

        }
        logger.info("başla updateChangedIdNumbers");
        // bilgi dugumlerin siralamasi degisince node tablosunda degisiklik olmuyor ama children tablosunda oluyor.
        // bilgi o degisikligi yakalamak ve senkrnizasyonu yapmak icin asdagidaki metod var.
        childrenService.updateChangedIdNumbers();
        // bilgi bir sonraki metod tum tabloyu hallediyor ama cirkin oldu, bu iki metodu tekrar dusun,
        //  daha iyi bir yontem olabilir mi diye. Ama verileri dogru senkronize eder asagidaki. Yani mantikli birsey
        //  yapacagim derken veri kaybina yolacama, birak boyle kalsin!

        logger.info("başla syncSharedNodesInRealityAllChildrenTable");

        // bilgi asagidaki aslinda tum children tablosunu esitliyor.
        //  cunku shared node varsa bska turlu anlayamayiz. Sadece bu tabloda fazladan bir kayit olur.
        //  ve shared node sadece olustururken degil, ana nodede degisiklik olursa da degiseceginden bence
        //  tum tabloyu guncellemek simdilik mantikli.
        childrenService.syncSharedNodesInRealityAllChildrenTable();

        // bilgi her iki dbdeki tablolarin satir sayilarini my db ye yazar.
        fromRecordCountService.printAndSaveRecordCounts();
        destinationRecordCountService.printAndSaveRecordCounts();

        myService.printAllSettings();

        boolean imagesOk = myService.areValuesEqual(Statistics.FROM_IMAGE_COUNT.getValue(), Statistics.DESTINATION_IMAGE_COUNT.getValue());
        if (!imagesOk) {
            logger.warn("Image table must be sync deeply");
            imageService.syncAllMissingIds();
        }

        boolean gridsOk = myService.areValuesEqual(Statistics.FROM_GRID_COUNT.getValue(), Statistics.DESTINATION_GRID_COUNT.getValue());
        if (!gridsOk) {
            logger.warn("Grid table must be sync deeply");
            gridService.syncAllMissingIds();
        }

        boolean codeBoxesOk = myService.areValuesEqual(Statistics.FROM_CODE_BOX_COUNT.getValue(), Statistics.DESTINATION_CODE_BOX_COUNT.getValue());
        if (!codeBoxesOk) {
            logger.warn("CodeBox table must be sync deeply");
            codeBoxService.syncAllMissingIds();
        }

        boolean bookmarksOk = myService.areValuesEqual(Statistics.FROM_BOOKMARK_COUNT.getValue(), Statistics.DESTINATION_BOOKMARK_COUNT.getValue());
        if (!bookmarksOk) {
            logger.warn("Bookmark table must be sync deeply");
            bookmarkService.syncAllMissingIds();
        }

        boolean childrenOk = myService.areValuesEqual(Statistics.FROM_CHILDREN_COUNT.getValue(), Statistics.DESTINATION_CHILDREN_COUNT.getValue());
        if (!childrenOk) {
            logger.warn("Children table must be sync deeply");
            childrenService.syncAllMissingIds();
        }

        boolean nodesOk = myService.areValuesEqual(Statistics.FROM_NODE_COUNT.getValue(), Statistics.DESTINATION_NODE_COUNT.getValue());
        if (!nodesOk) {
            logger.warn("Node table must be sync deeply");
            nodeService.syncAllMissingIds();
        }

        fromRecordCountService.printAndSaveRecordCounts();
        destinationRecordCountService.printAndSaveRecordCounts();

        myService.printAllSettings();


    }


    public void syncCreateOrUpdateNodeWithRelationships(Node fromNode) {

        Integer nodeId = fromNode.getNodeId();

        Node destinationNode = destinationRepository.findById(nodeId).orElse(null);
        boolean isNewNode = (destinationNode == null);

        if (isNewNode) {
            logger.info("new node: " + nodeId + ", " + fromNode.getName());
            destinationRepository.save(fromNode);
            myService.increment(Statistics.CREATED_NODES.getValue());

            childrenService.syncCreateRow(nodeId);


            gridService.syncCreateRecords(nodeId);
            codeBoxService.syncCreateRecords(nodeId);
            imageService.syncCreateRecords(nodeId);


        } else if (!fromNode.getTsLastsave().equals(destinationNode.getTsLastsave())) {
            // bilgi: yukaridaki condition, isModifiedNode diye yazilsa iyi olurdu ama o zaman destination node bazen
            //  null olabileceginden yaamiyoruz, oyle yazsak daha anlasilir olurdu. Fakat bu sekilde biraktik mecburen.
            logger.info("changed node: " + nodeId + ", " + fromNode.getName());

/*
            Map<String, Object[]> differences = getWhatIsChanged(fromNode, destinationNode);
            int crop = 20;

            for (Map.Entry<String, Object[]> entry : differences.entrySet()) {
                String value = entry.getValue()[0].toString();
                String truncatedValue = null;
                if (value.length() > crop) {
                    truncatedValue = value.substring(0, crop);
                } else {
                    truncatedValue = value;
                }

                String value2 = (String) entry.getValue()[1].toString();
                String truncatedValue2 = null;
                if (value2.length() > crop) {
                    truncatedValue2 = value2.substring(0, crop);
                } else {
                    truncatedValue2 = value2;
                }

                System.out.println(fromNode.getNodeId() + ": Field: " + entry.getKey() + ", From: " + truncatedValue + ", To: " + truncatedValue2);
            }
*/

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
            destinationRepository.save(destinationNode);
            myService.increment(Statistics.UPDATED_NODES.getValue());


            childrenService.syncUpdateRow(nodeId);

            gridService.syncUpdateRecords(nodeId);
            codeBoxService.syncUpdateRecords(nodeId);
            imageService.syncUpdateRecords(nodeId);


        } else {
            // logger.info("nothing node");

        }


    }

    /**
     * hedefte bir node var fakat kaynakta yoksa silinmis demektir.
     * hedefteki node'yi ve onunla baglantili tablolardaki verileri silme islemi yapar.
     * @param destinationNode
     */
    public void syncDeleteNodeWithRelationships(Node destinationNode) {

        Integer nodeId = destinationNode.getNodeId();


        Node fromNode = fromRepository.findById(nodeId).orElse(null);
        if (fromNode == null) {
            logger.info("delete node: " + destinationNode.getNodeId() + ", " + destinationNode.getName());

            destinationRepository.delete(destinationNode);

            childrenService.syncDeleteRow(nodeId);            ;
            gridService.syncDeleteRecords(nodeId);
            codeBoxService.syncDeleteRecords(nodeId);
            imageService.syncDeleteRecords(nodeId);
            // TODO Bookmark node de olmali mi? Ama shadow node varsa?

            myService.increment(Statistics.DELETED_NODES.getValue());



        }

    }

    public static Map<String, Object[]> getWhatIsChanged(Node fromNode, Node destinationNode) {
        Map<String, Object[]> differences = new HashMap<>();

        try {
            // Lazy loaded collections must be initialized explicitly
            // Hibernate.initialize(fromNode.getImages());
            // Hibernate.initialize(destinationNode.getImages());

            Field[] fields = Node.class.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object fromValue = field.get(fromNode);
                Object toValue = field.get(destinationNode);

                if (fromValue != null && toValue != null && !fromValue.equals(toValue)) {
                    differences.put(field.getName(), new Object[]{fromValue, toValue});
                } else if ((fromValue == null && toValue != null) || (fromValue != null && toValue == null)) {
                    differences.put(field.getName(), new Object[]{fromValue, toValue});
                }
            }
        } catch (IllegalAccessException e) {
            logger.error("Error accessing fields for comparison", e);
        }

        return differences;
    }

}

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
package com.turkerozturk.sweetcherrysync.entities;


import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Entity
@Table(name = "node")
public class Node {

    private static final Logger logger = LoggerFactory.getLogger(Node.class);

    @Id
    @Column(name = "node_id")
    private Integer nodeId;
    private String name;
    private String txt;
    private String syntax;
    private String tags;

    @Column(name = "is_ro")
    private Integer isRo;

    @Column(name = "is_richtxt")
    private Integer isRichtxt;

    @Column(name = "has_codebox")
    private Integer hasCodebox;

    @Column(name = "has_table")
    private Integer hasTable;

    @Column(name = "has_image")
    private Integer hasImage;
    private Integer level;

    @Column(name = "ts_creation")
    private Long tsCreation;


    @Column(name = "ts_lastsave")
    private Long tsLastsave;

    @Transient
    @OneToMany(mappedBy = "node", fetch = FetchType.LAZY)
    private Set<Image> images;

    @Transient
    @OneToMany(mappedBy = "node", fetch = FetchType.LAZY)
    private Set<Grid> grids;

    @Transient
    @OneToMany(mappedBy = "node", fetch = FetchType.LAZY)
    private Set<CodeBox> codeBoxes;

    @Transient
    @OneToOne
    @JoinColumn(name = "node_id", referencedColumnName = "node_id")
    private Bookmark bookmark;




    @Transient
    @OneToOne(mappedBy = "node", optional = true, fetch = FetchType.LAZY)
    private Children children;

    public Children getChildren() {
        return children;
    }

    public void setChildren(Children children) {
        this.children = children;
    }


    public Integer getNodeId() {
        return nodeId;
    }

    public void setNodeId(Integer nodeId) {
        this.nodeId = nodeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTxt() {
        return txt;
    }

    public void setTxt(String txt) {
        this.txt = txt;
    }

    public String getSyntax() {
        return syntax;
    }

    public void setSyntax(String syntax) {
        this.syntax = syntax;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getIsRo() {
        return isRo;
    }

    public void setIsRo(Integer isRo) {
        this.isRo = isRo;
    }

    public Integer getIsRichtxt() {
        return isRichtxt;
    }

    public void setIsRichtxt(Integer isRichtxt) {
        this.isRichtxt = isRichtxt;
    }

    public Integer getHasCodebox() {
        return hasCodebox;
    }

    public void setHasCodebox(Integer hasCodebox) {
        this.hasCodebox = hasCodebox;
    }

    public Integer getHasTable() {
        return hasTable;
    }

    public void setHasTable(Integer hasTable) {
        this.hasTable = hasTable;
    }

    public Integer getHasImage() {
        return hasImage;
    }

    public void setHasImage(Integer hasImage) {
        this.hasImage = hasImage;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Long getTsCreation() {
        return tsCreation;
    }

    public void setTsCreation(Long tsCreation) {
        this.tsCreation = tsCreation;
    }

    public Long getTsLastsave() {
        return tsLastsave;
    }

    public void setTsLastsave(Long tsLastsave) {
        this.tsLastsave = tsLastsave;
    }

    public Set<Image> getImages() {
        return images;
    }

    public void setImages(Set<Image> images) {
        this.images = images;
    }

    public Set<Grid> getGrids() {
        return grids;
    }

    public void setGrids(Set<Grid> grids) {
        this.grids = grids;
    }

    public Set<CodeBox> getCodeBoxes() {
        return codeBoxes;
    }

    public void setCodeBoxes(Set<CodeBox> codeBoxes) {
        this.codeBoxes = codeBoxes;
    }

    public Bookmark getBookmark() {
        return bookmark;
    }

    public void setBookmark(Bookmark bookmark) {
        this.bookmark = bookmark;
    }



}


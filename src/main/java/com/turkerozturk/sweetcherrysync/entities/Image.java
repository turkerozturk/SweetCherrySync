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

@Entity
@Table(name = "image")
public class Image {

    private static final Logger logger = LoggerFactory.getLogger(Image.class);

    @EmbeddedId
    private CombinedId id;

    @Column(name = "justification")
    private String justification;

    @Column(name = "anchor")
    private String anchor;

    //@Lob // bu lob a gerek olmadi daha once su an da yok, sqlite desteklimiyor gibi zaten.
    @Column(name = "png")//blob oldugunu belirtmek gerekirse uncomment edilip denenir, columnDefinition="BLOB")
    private byte[] png;

    @Column(name = "filename")
    private String fileName;

    @Column(name = "\"link\"")  // Escape the link column name for postgresql
    private String link;

    @Column(name = "time")
    private Long time;

    @ManyToOne
    @JoinColumn(name = "node_id", insertable = false, updatable = false)
    private Node node;


    public CombinedId getId() {
        return id;
    }

    public void setId(CombinedId id) {
        this.id = id;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public String getAnchor() {
        return anchor;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }

    public byte[] getPng() {
        return png;
    }

    public void setPng(byte[] png) {
        this.png = png;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String filename) {
        this.fileName = filename;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }
}

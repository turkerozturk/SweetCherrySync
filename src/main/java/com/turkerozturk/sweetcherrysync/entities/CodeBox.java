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
@Table(name = "codebox")
public class CodeBox {

    private static final Logger logger = LoggerFactory.getLogger(CodeBox.class);


    @EmbeddedId
    private CombinedId id;

    @Column(name = "justification")
    private String justification;

    @Column(name = "txt")
    private String txt;

    @Column(name = "syntax")
    private String syntax;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "is_width_pix")
    private Integer isWidthPix;

    @Column(name = "do_highl_bra")
    private Integer doHighlBra;

    @Column(name = "do_show_linenum")
    private Integer doShowLinenum;

    @ManyToOne
    @JoinColumn(name = "node_id", insertable = false, updatable = false)
    private Node node;

    // Getter ve setter metotları


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

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getIsWidthPix() {
        return isWidthPix;
    }

    public void setIsWidthPix(Integer isWidthPix) {
        this.isWidthPix = isWidthPix;
    }

    public Integer getDoHighlBra() {
        return doHighlBra;
    }

    public void setDoHighlBra(Integer doHighlBra) {
        this.doHighlBra = doHighlBra;
    }

    public Integer getDoShowLinenum() {
        return doShowLinenum;
    }

    public void setDoShowLinenum(Integer doShowLinenum) {
        this.doShowLinenum = doShowLinenum;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }


}

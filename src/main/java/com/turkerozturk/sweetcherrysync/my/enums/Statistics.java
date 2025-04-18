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
package com.turkerozturk.sweetcherrysync.my.enums;

public enum Statistics {

    CREATED_BOOKMARK("createdBookmark", "enum.created.bookmark"),
    CREATED_CHILDREN("createdChildren", "enum.created.children"),
    CREATED_CODE_BOXES("createdCodeBoxes", "enum.created.codeBoxes"),
    CREATED_GRIDS("createdGrids", "enum.created.grids"),
    CREATED_IMAGES("createdImages", "enum.created.images"),
    CREATED_NODES("createdNodes", "enum.created.nodes"),

    DELETED_BOOKMARK("deletedBookmark", "enum.deleted.bookmark"),
    DELETED_CHILDREN("deletedChildren", "enum.deleted.children"),
    DELETED_CODE_BOXES("deletedCodeBoxes", "enum.deleted.codeBoxes"),
    DELETED_GRIDS("deletedGrids", "enum.deleted.grids"),
    DELETED_IMAGES("deletedImages", "enum.deleted.images"),
    DELETED_NODES("deletedNodes", "enum.deleted.nodes"),

    DESTINATION_BOOKMARK_COUNT("destinationBookmarkCount", "enum.destination.bookmarkCount"),
    DESTINATION_CHILDREN_COUNT("destinationChildrenCount", "enum.destination.childrenCount"),
    DESTINATION_CODE_BOX_COUNT("destinationCodeBoxCount", "enum.destination.codeBoxCount"),
    DESTINATION_GRID_COUNT("destinationGridCount", "enum.destination.gridCount"),
    DESTINATION_IMAGE_COUNT("destinationImageCount", "enum.destination.imageCount"),
    DESTINATION_NODE_COUNT("destinationNodeCount", "enum.destination.nodeCount"),

    FROM_BOOKMARK_COUNT("fromBookmarkCount", "enum.from.bookmarkCount"),
    FROM_CHILDREN_COUNT("fromChildrenCount", "enum.from.childrenCount"),
    FROM_CODE_BOX_COUNT("fromCodeBoxCount", "enum.from.codeBoxCount"),
    FROM_GRID_COUNT("fromGridCount", "enum.from.gridCount"),
    FROM_IMAGE_COUNT("fromImageCount", "enum.from.imageCount"),
    FROM_NODE_COUNT("fromNodeCount", "enum.from.nodeCount"),

    UPDATED_BOOKMARK("updatedBookmark", "enum.updated.bookmark"),
    UPDATED_CHILDREN("updatedChildren", "enum.updated.children"),
    UPDATED_CODE_BOXES("updatedCodeBoxes", "enum.updated.codeBoxes"),
    UPDATED_GRIDS("updatedGrids", "enum.updated.grids"),
    UPDATED_IMAGES("updatedImages", "enum.updated.images"),
    UPDATED_NODES("updatedNodes", "enum.updated.nodes"),

    NODE_COUNTER("nodeCounter", "enum.node.counter");

    private final String value;
    private final String i18nKey;

    Statistics(String value, String i18nKey) {
        this.value = value;
        this.i18nKey = i18nKey;
    }

    public String getValue() {
        return value;
    }

    public String getI18nKey() {
        return i18nKey;
    }
}

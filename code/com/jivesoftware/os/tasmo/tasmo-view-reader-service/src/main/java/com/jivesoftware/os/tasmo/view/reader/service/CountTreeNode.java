/*
 * Copyright 2014 pete.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jivesoftware.os.tasmo.view.reader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import com.jivesoftware.os.jive.utils.id.Id;
import com.jivesoftware.os.jive.utils.id.ObjectId;
import com.jivesoftware.os.tasmo.event.api.ReservedFields;
import com.jivesoftware.os.tasmo.model.path.ModelPathStep;
import com.jivesoftware.os.tasmo.view.reader.service.shared.ViewValue;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
class CountTreeNode implements MultiTreeNode {

    public static final String JSON_FIELD = "count";
    private final Set<Id> idsSeen = new HashSet<>();

    CountTreeNode() {
    }

    @Override
    public void add(ModelPathStep[] steps, ObjectId[] ids, ViewValue value, Long threadTimestamp) {
        idsSeen.add(ids[0].getId());
    }

    @Override
    public JsonNode merge(JsonViewMerger merger, Set<Id> permittedIds) throws IOException {
        ObjectNode objectNode = merger.createObjectNode();
        objectNode.put(JSON_FIELD, Sets.intersection(idsSeen, permittedIds).size());
        return objectNode;
    }

    @Override
    public String getFieldPrefix() {
        return ReservedFields.COUNT_BACK_REF_FIELD_PREFIX;
    }
}


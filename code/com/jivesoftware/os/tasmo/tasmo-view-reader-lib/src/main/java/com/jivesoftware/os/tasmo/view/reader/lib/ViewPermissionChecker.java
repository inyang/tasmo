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
package com.jivesoftware.os.tasmo.view.reader.lib;
import com.jivesoftware.os.tasmo.id.Id;
import com.jivesoftware.os.tasmo.id.TenantId;
import java.util.Set;

public interface ViewPermissionChecker {

    /**
     *  Returns a set of ids that are equal to or a subset of the
     * input set of ids which the actor has permission to view.
     *
     *
     * @param tenantId
     * @param actorId
     * @param permissionCheckTheseIds
     * @return
     */
    public ViewPermissionCheckResult check(TenantId tenantId, Id actorId, Set<Id> permissionCheckTheseIds);
}
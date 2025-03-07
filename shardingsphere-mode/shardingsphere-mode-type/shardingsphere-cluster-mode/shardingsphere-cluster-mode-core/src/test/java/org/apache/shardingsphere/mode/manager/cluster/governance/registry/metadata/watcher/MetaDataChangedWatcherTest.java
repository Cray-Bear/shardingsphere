/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.mode.manager.cluster.governance.registry.metadata.watcher;

import org.apache.shardingsphere.mode.manager.cluster.governance.registry.GovernanceEvent;
import org.apache.shardingsphere.mode.repository.cluster.listener.DataChangedEvent;
import org.apache.shardingsphere.mode.repository.cluster.listener.DataChangedEvent.Type;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MetaDataChangedWatcherTest {
    
    @Test
    public void assertCreateEventWithInvalidPath() {
        String key = "/metadata_invalid/sharding_db";
        String value = "encrypt_db";
        Optional<GovernanceEvent> actual = createEvent(key, value, Type.UPDATED);
        assertFalse(actual.isPresent());
        actual = createEvent(key, value, Type.ADDED);
        assertFalse(actual.isPresent());
        actual = createEvent(key, value, Type.DELETED);
        assertFalse(actual.isPresent());
        actual = createEvent(key, value, Type.IGNORED);
        assertFalse(actual.isPresent());
    }
    
    @Test
    public void assertCreateAddedEvent() {
        String key = "/metadata/sharding_db";
        String value = "encrypt_db";
        Optional<GovernanceEvent> actual = createEvent(key, value, Type.UPDATED);
        assertTrue(actual.isPresent());
        actual = createEvent(key, value, Type.ADDED);
        assertTrue(actual.isPresent());
        actual = createEvent(key, value, Type.IGNORED);
        assertFalse(actual.isPresent());
    }
    
    @Test
    public void assertEmptyValue() {
        String key = "/metadata/sharding_db/dataSources";
        Optional<GovernanceEvent> actual = createEvent(key, null, Type.UPDATED);
        assertFalse(actual.isPresent());
    }
    
    @Test
    public void assertCreateDeletedEvent() {
        String key = "/metadata/sharding_db";
        String value = "encrypt_db";
        Optional<GovernanceEvent> actual = createEvent(key, value, Type.DELETED);
        assertTrue(actual.isPresent());
    }
    
    @Test
    public void assertCreateDataSourceChangedEvent() {
        String key = "/metadata/sharding_db/dataSources";
        String value = "{}";
        Optional<GovernanceEvent> actual = createEvent(key, value, Type.UPDATED);
        assertTrue(actual.isPresent());
    }
    
    @Test
    public void assertCreateRuleChangedEvent() {
        String key = "/metadata/sharding_db/rules";
        Optional<GovernanceEvent> actual = createEvent(key, "[]", Type.UPDATED);
        assertTrue(actual.isPresent());
    }
    
    @Test
    public void assertCreateCachedEvent() {
        String key = "/metadata/sharding_db/rules/cache";
        Optional<GovernanceEvent> actual = createEvent(key, "[]", Type.UPDATED);
        assertTrue(actual.isPresent());
    }
    
    @Test
    public void assertCreateSchemaChangedEvent() {
        String key = "/metadata/sharding_db/schema";
        Optional<GovernanceEvent> actual = createEvent(key, "{}", Type.UPDATED);
        assertTrue(actual.isPresent());
    }
    
    private Optional<GovernanceEvent> createEvent(final String key, final String value, final Type type) {
        DataChangedEvent dataChangedEvent = new DataChangedEvent(key, value, type);
        return new MetaDataChangedWatcher().createGovernanceEvent(dataChangedEvent);
    }
}

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

package org.apache.shardingsphere.mode.manager.cluster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.lock.ShardingSphereLock;
import org.apache.shardingsphere.infra.rule.event.impl.DataSourceNameDisabledEvent;
import org.apache.shardingsphere.infra.rule.identifier.type.StatusContainedRule;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.manager.cluster.governance.lock.ShardingSphereDistributeLock;
import org.apache.shardingsphere.mode.manager.cluster.governance.schema.GovernanceSchema;
import org.apache.shardingsphere.mode.metadata.MetaDataContexts;
import org.apache.shardingsphere.mode.persist.PersistService;
import org.apache.shardingsphere.mode.repository.cluster.ClusterPersistRepository;
import org.apache.shardingsphere.transaction.context.TransactionContexts;

import java.util.Collection;
import java.util.Optional;

/**
 * Cluster context manager.
 */
@RequiredArgsConstructor
public final class ClusterContextManager implements ContextManager {
    
    private final PersistService persistService;
    
    private final RegistryCenter registryCenter;
    
    @Getter
    private volatile MetaDataContexts metaDataContexts;
    
    @Getter
    private volatile TransactionContexts transactionContexts;
    
    private volatile ShardingSphereLock lock;
    
    @Override
    public void init(final MetaDataContexts metaDataContexts, final TransactionContexts transactionContexts) {
        this.metaDataContexts = metaDataContexts;
        this.transactionContexts = transactionContexts;
        disableDataSources();
        persistMetaData();
        lock = createShardingSphereLock(registryCenter.getRepository());
        registryCenter.onlineInstance(metaDataContexts.getAllSchemaNames());
    }
    
    private void disableDataSources() {
        metaDataContexts.getMetaDataMap().forEach((key, value)
            -> value.getRuleMetaData().getRules().stream().filter(each -> each instanceof StatusContainedRule).forEach(each -> disableDataSources(key, (StatusContainedRule) each)));
    }
    
    private void disableDataSources(final String schemaName, final StatusContainedRule rule) {
        Collection<String> disabledDataSources = registryCenter.getDataSourceStatusService().loadDisabledDataSources(schemaName);
        disabledDataSources.stream().map(this::getDataSourceName).forEach(each -> rule.updateRuleStatus(new DataSourceNameDisabledEvent(each, true)));
    }
    
    private String getDataSourceName(final String disabledDataSource) {
        return new GovernanceSchema(disabledDataSource).getDataSourceName();
    }
    
    private void persistMetaData() {
        metaDataContexts.getMetaDataMap().forEach((key, value) -> persistService.getSchemaMetaDataService().persist(key, value.getSchema()));
    }
    
    private ShardingSphereLock createShardingSphereLock(final ClusterPersistRepository repository) {
        return metaDataContexts.getProps().<Boolean>getValue(ConfigurationPropertyKey.LOCK_ENABLED)
                ? new ShardingSphereDistributeLock(repository, metaDataContexts.getProps().<Long>getValue(ConfigurationPropertyKey.LOCK_WAIT_TIMEOUT_MILLISECONDS)) : null;
    }
    
    @Override
    public synchronized void renewMetaDataContexts(final MetaDataContexts metaDataContexts) {
        this.metaDataContexts = metaDataContexts;
    }
    
    @Override
    public synchronized void renewTransactionContexts(final TransactionContexts transactionContexts) {
        this.transactionContexts = transactionContexts;
    }
    
    @Override
    public Optional<ShardingSphereLock> getLock() {
        return Optional.ofNullable(lock);
    }
    
    @Override
    public void close() {
        metaDataContexts.getExecutorEngine().close();
        registryCenter.getRepository().close();
    }
}

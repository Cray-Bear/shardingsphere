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

package org.apache.shardingsphere.mode.manager.standalone;

import org.apache.shardingsphere.infra.config.RuleConfiguration;
import org.apache.shardingsphere.infra.config.datasource.DataSourceConfiguration;
import org.apache.shardingsphere.infra.config.datasource.DataSourceConverter;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.config.mode.PersistRepositoryConfiguration;
import org.apache.shardingsphere.infra.metadata.resource.ShardingSphereResource;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.manager.ContextManagerBuilder;
import org.apache.shardingsphere.mode.metadata.MetaDataContexts;
import org.apache.shardingsphere.mode.metadata.MetaDataContextsBuilder;
import org.apache.shardingsphere.mode.persist.PersistService;
import org.apache.shardingsphere.mode.repository.standalone.StandalonePersistRepository;
import org.apache.shardingsphere.mode.repository.standalone.StandalonePersistRepositoryConfiguration;
import org.apache.shardingsphere.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.spi.typed.TypedSPIRegistry;
import org.apache.shardingsphere.transaction.ShardingSphereTransactionManagerEngine;
import org.apache.shardingsphere.transaction.config.TransactionRuleConfiguration;
import org.apache.shardingsphere.transaction.context.TransactionContexts;
import org.apache.shardingsphere.transaction.core.TransactionType;
import org.apache.shardingsphere.transaction.rule.TransactionRule;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Standalone context manager builder.
 */
public final class StandaloneContextManagerBuilder implements ContextManagerBuilder {
    
    static {
        ShardingSphereServiceLoader.register(StandalonePersistRepository.class);
    }
    
    @Override
    public ContextManager build(final ModeConfiguration modeConfig, final Map<String, Map<String, DataSource>> dataSourcesMap,
                                final Map<String, Collection<RuleConfiguration>> schemaRuleConfigs, final Collection<RuleConfiguration> globalRuleConfigs,
                                final Properties props, final boolean isOverwrite) throws SQLException {
        PersistRepositoryConfiguration repositoryConfig = null == modeConfig.getRepository() ? new StandalonePersistRepositoryConfiguration("Local", new Properties()) : modeConfig.getRepository();
        StandalonePersistRepository repository = TypedSPIRegistry.getRegisteredService(StandalonePersistRepository.class, repositoryConfig.getType(), repositoryConfig.getProps());
        PersistService persistService = new PersistService(repository);
        persistConfigurations(persistService, dataSourcesMap, schemaRuleConfigs, globalRuleConfigs, props, isOverwrite);
        Collection<String> schemaNames = persistService.getSchemaMetaDataService().loadAllNames();
        MetaDataContexts metaDataContexts = new MetaDataContextsBuilder(loadDataSourcesMap(persistService, dataSourcesMap, schemaNames),
                loadSchemaRules(persistService, schemaNames), persistService.getGlobalRuleService().load(), persistService.getPropsService().load()).build(persistService);
        TransactionContexts transactionContexts = createTransactionContexts(metaDataContexts);
        ContextManager result = new StandaloneContextManager();
        result.init(metaDataContexts, transactionContexts);
        return result;
    }
    
    private void persistConfigurations(final PersistService persistService, final Map<String, Map<String, DataSource>> dataSourcesMap,
                                       final Map<String, Collection<RuleConfiguration>> schemaRuleConfigs, final Collection<RuleConfiguration> globalRuleConfigs,
                                       final Properties props, final boolean overwrite) {
        if (!isEmptyLocalConfiguration(dataSourcesMap, schemaRuleConfigs, globalRuleConfigs, props)) {
            persistService.persistConfigurations(getDataSourceConfigurations(dataSourcesMap), schemaRuleConfigs, globalRuleConfigs, props, overwrite);
        }
    }
    
    private boolean isEmptyLocalConfiguration(final Map<String, Map<String, DataSource>> dataSourcesMap,
                                              final Map<String, Collection<RuleConfiguration>> schemaRuleConfigs, final Collection<RuleConfiguration> globalRuleConfigs, final Properties props) {
        return isEmptyLocalDataSourcesMap(dataSourcesMap) && isEmptyLocalSchemaRuleConfigurations(schemaRuleConfigs) && globalRuleConfigs.isEmpty() && props.isEmpty();
    }
    
    private boolean isEmptyLocalDataSourcesMap(final Map<String, Map<String, DataSource>> dataSourcesMap) {
        return dataSourcesMap.entrySet().stream().allMatch(entry -> entry.getValue().isEmpty());
    }
    
    private boolean isEmptyLocalSchemaRuleConfigurations(final Map<String, Collection<RuleConfiguration>> schemaRuleConfigs) {
        return schemaRuleConfigs.entrySet().stream().allMatch(entry -> entry.getValue().isEmpty());
    }
    
    private Map<String, Map<String, DataSourceConfiguration>> getDataSourceConfigurations(final Map<String, Map<String, DataSource>> dataSourcesMap) {
        Map<String, Map<String, DataSourceConfiguration>> result = new LinkedHashMap<>(dataSourcesMap.size(), 1);
        for (Entry<String, Map<String, DataSource>> entry : dataSourcesMap.entrySet()) {
            result.put(entry.getKey(), DataSourceConverter.getDataSourceConfigurationMap(entry.getValue()));
        }
        return result;
    }
    
    private Map<String, Map<String, DataSource>> loadDataSourcesMap(final PersistService persistService, final Map<String, Map<String, DataSource>> dataSourcesMap,
                                                                    final Collection<String> schemaNames) {
        Map<String, Map<String, DataSourceConfiguration>> loadedDataSourceConfigs = loadDataSourceConfigurations(persistService, schemaNames);
        Map<String, Map<String, DataSourceConfiguration>> changedDataSourceConfigs = getChangedDataSourceConfigurations(dataSourcesMap, loadedDataSourceConfigs);
        Map<String, Map<String, DataSource>> result = new LinkedHashMap<>(dataSourcesMap);
        getChangedDataSources(changedDataSourceConfigs).forEach((key, value) -> {
            if (result.containsKey(key)) {
                result.get(key).putAll(value);
            } else {
                result.put(key, value);
            }
        });
        return result;
    }
    
    private Map<String, Map<String, DataSourceConfiguration>> loadDataSourceConfigurations(final PersistService persistService, final Collection<String> schemaNames) {
        Map<String, Map<String, DataSourceConfiguration>> result = new LinkedHashMap<>();
        for (String each : schemaNames) {
            result.put(each, persistService.getDataSourceService().load(each));
        }
        return result;
    }
    
    private Map<String, Map<String, DataSourceConfiguration>> getChangedDataSourceConfigurations(final Map<String, Map<String, DataSource>> configuredDataSourcesMap,
                                                                                                 final Map<String, Map<String, DataSourceConfiguration>> loadedDataSourceConfigs) {
        if (isEmptyLocalDataSourcesMap(configuredDataSourcesMap)) {
            return loadedDataSourceConfigs;
        }
        Map<String, Map<String, DataSourceConfiguration>> result = new HashMap<>(loadedDataSourceConfigs.size(), 1);
        for (Entry<String, Map<String, DataSourceConfiguration>> entry : loadedDataSourceConfigs.entrySet()) {
            if (configuredDataSourcesMap.containsKey(entry.getKey())) {
                Map<String, DataSourceConfiguration> changedDataSources = getChangedDataSourcesConfigurations(configuredDataSourcesMap.get(entry.getKey()), entry.getValue());
                if (!changedDataSources.isEmpty()) {
                    result.put(entry.getKey(), changedDataSources);
                }
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    private Map<String, DataSourceConfiguration> getChangedDataSourcesConfigurations(final Map<String, DataSource> dataSourceMap,
                                                                                     final Map<String, DataSourceConfiguration> loadedDataSourceConfigurationMap) {
        Map<String, DataSourceConfiguration> dataSourceConfigurationMap = DataSourceConverter.getDataSourceConfigurationMap(dataSourceMap);
        return loadedDataSourceConfigurationMap.entrySet().stream().filter(entry -> !dataSourceConfigurationMap.containsKey(entry.getKey())
                || !dataSourceConfigurationMap.get(entry.getKey()).equals(entry.getValue())).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }
    
    private Map<String, Map<String, DataSource>> getChangedDataSources(final Map<String, Map<String, DataSourceConfiguration>> changedDataSourceConfigurations) {
        Map<String, Map<String, DataSource>> result = new LinkedHashMap<>(changedDataSourceConfigurations.size(), 1);
        for (Entry<String, Map<String, DataSourceConfiguration>> entry : changedDataSourceConfigurations.entrySet()) {
            result.put(entry.getKey(), createDataSources(entry.getValue()));
        }
        return result;
    }
    
    private Map<String, DataSource> createDataSources(final Map<String, DataSourceConfiguration> dataSourceConfigs) {
        Map<String, DataSource> result = new LinkedHashMap<>(dataSourceConfigs.size(), 1);
        for (Entry<String, DataSourceConfiguration> each : dataSourceConfigs.entrySet()) {
            result.put(each.getKey(), each.getValue().createDataSource());
        }
        return result;
    }
    
    private Map<String, Collection<RuleConfiguration>> loadSchemaRules(final PersistService persistService, final Collection<String> schemaNames) {
        return schemaNames.stream().collect(Collectors.toMap(
            each -> each, each -> persistService.getSchemaRuleService().load(each), (oldValue, currentValue) -> oldValue, LinkedHashMap::new));
    }
    
    private TransactionContexts createTransactionContexts(final MetaDataContexts metaDataContexts) {
        Map<String, ShardingSphereTransactionManagerEngine> engines = new HashMap<>(metaDataContexts.getAllSchemaNames().size(), 1);
        TransactionRule transactionRule = getTransactionRule(metaDataContexts);
        for (String each : metaDataContexts.getAllSchemaNames()) {
            ShardingSphereTransactionManagerEngine engine = new ShardingSphereTransactionManagerEngine();
            ShardingSphereResource resource = metaDataContexts.getMetaData(each).getResource();
            engine.init(resource.getDatabaseType(), resource.getDataSources(), transactionRule);
            engines.put(each, engine);
        }
        return new TransactionContexts(engines);
    }
    
    private TransactionRule getTransactionRule(final MetaDataContexts metaDataContexts) {
        Optional<TransactionRule> transactionRule = metaDataContexts.getGlobalRuleMetaData().getRules().stream().filter(
            each -> each instanceof TransactionRule).map(each -> (TransactionRule) each).findFirst();
        return transactionRule.orElseGet(() -> new TransactionRule(new TransactionRuleConfiguration(TransactionType.LOCAL.name(), null)));
    }
    
    @Override
    public String getType() {
        return "Standalone";
    }
}

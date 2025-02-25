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

package org.apache.shardingsphere.example.governance.raw.jdbc;

import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.apache.shardingsphere.example.config.ExampleConfiguration;
import org.apache.shardingsphere.example.core.api.ExampleExecuteTemplate;
import org.apache.shardingsphere.example.core.api.service.ExampleService;
import org.apache.shardingsphere.example.core.jdbc.service.OrderServiceImpl;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.ClusterModeConfigurationUtil;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.cloud.CloudEncryptConfiguration;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.cloud.CloudReadwriteSplittingConfiguration;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.cloud.CloudShadowConfiguration;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.cloud.CloudShardingDatabasesAndTablesConfiguration;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.local.LocalEncryptConfiguration;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.local.LocalReadwriteSplittingConfiguration;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.local.LocalShadowConfiguration;
import org.apache.shardingsphere.example.governance.raw.jdbc.config.local.LocalShardingDatabasesAndTablesConfiguration;
import org.apache.shardingsphere.example.type.ShardingType;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;

import javax.sql.DataSource;
import java.sql.SQLException;

/*
 * 1. Please make sure primary replica data replication sync on MySQL is running correctly. Otherwise this example will query empty data from replica.
 * 2. Please make sure sharding-governance-center-zookeeper-curator in your pom if registryCenterType = RegistryCenterType.ZOOKEEPER.
 */
public final class GovernanceRawJavaConfigurationExample {
    
    private static ShardingType shardingType = ShardingType.SHARDING_DATABASES_AND_TABLES;
//    private static ShardingType shardingType = ShardingType.READWRITE_SPLITTING;
//    private static ShardingType shardingType = ShardingType.ENCRYPT;
//    private static ShardingType shardingType = ShardingType.SHADOW;
    
    private static boolean loadConfigFromRegCenter = false;
//    private static boolean loadConfigFromRegCenter = true;
    
    public static void main(final String[] args) throws Exception {
        DataSource dataSource = getDataSource(shardingType, loadConfigFromRegCenter);
        try {
            ExampleExecuteTemplate.run(getExampleService(dataSource));
        } finally {
            closeDataSource(dataSource);
        }
    }
    
    private static DataSource getDataSource(final ShardingType shardingType, final boolean loadConfigFromRegCenter) throws SQLException {
        ModeConfiguration modeConfig = getModeConfiguration(shardingType);
        ExampleConfiguration config;
        switch (shardingType) {
            case SHARDING_DATABASES_AND_TABLES:
                config = loadConfigFromRegCenter 
                        ? new CloudShardingDatabasesAndTablesConfiguration(modeConfig) : new LocalShardingDatabasesAndTablesConfiguration(modeConfig);
                break;
            case READWRITE_SPLITTING:
                config = loadConfigFromRegCenter ? new CloudReadwriteSplittingConfiguration(modeConfig) : new LocalReadwriteSplittingConfiguration(modeConfig);
                break;
            case ENCRYPT:
                config = loadConfigFromRegCenter ? new CloudEncryptConfiguration(modeConfig) : new LocalEncryptConfiguration(modeConfig);
                break;
            case SHADOW:
                config = loadConfigFromRegCenter ? new CloudShadowConfiguration(modeConfig) : new LocalShadowConfiguration(modeConfig);
                break;
            default:
                throw new UnsupportedOperationException(shardingType.name());
        }
        return config.getDataSource();
    }
    
    private static ModeConfiguration getModeConfiguration(final ShardingType shardingType) {
        return ClusterModeConfigurationUtil.getZooKeeperConfiguration(!loadConfigFromRegCenter, shardingType);
    }
    
    private static ExampleService getExampleService(final DataSource dataSource) {
        return new OrderServiceImpl(dataSource);
    }
    
    private static void closeDataSource(final DataSource dataSource) throws Exception {
        if (dataSource instanceof ShardingSphereDataSource) {
            ((ShardingSphereDataSource) dataSource).close();
        }
    }
}

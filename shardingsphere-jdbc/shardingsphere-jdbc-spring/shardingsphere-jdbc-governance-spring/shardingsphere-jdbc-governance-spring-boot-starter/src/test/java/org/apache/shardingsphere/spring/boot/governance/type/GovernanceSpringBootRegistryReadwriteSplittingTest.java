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

package org.apache.shardingsphere.spring.boot.governance.type;

import lombok.SneakyThrows;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.apache.shardingsphere.mode.repository.cluster.ClusterPersistRepositoryConfiguration;
import org.apache.shardingsphere.mode.repository.cluster.zookeeper.CuratorZookeeperRepository;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.infra.database.DefaultSchema;
import org.apache.shardingsphere.spring.boot.governance.util.EmbedTestingServer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = GovernanceSpringBootRegistryReadwriteSplittingTest.class)
@SpringBootApplication
@ActiveProfiles("registry-readwrite-splitting")
public class GovernanceSpringBootRegistryReadwriteSplittingTest {
    
    private static final String DATA_SOURCE_FILE = "yaml/readwrite-splitting-databases.yaml";
    
    private static final String RULE_FILE = "yaml/readwrite-splitting-rule.yaml";
    
    @Resource
    private DataSource dataSource;
    
    @BeforeClass
    public static void init() {
        EmbedTestingServer.start();
        CuratorZookeeperRepository repository = new CuratorZookeeperRepository();
        repository.init(new ClusterPersistRepositoryConfiguration("ZooKeeper", "governance-spring-boot-registry-readwrite-splitting-test", "localhost:3183", new Properties()));
        repository.persist("/metadata/logic_db/dataSources", readYAML(DATA_SOURCE_FILE));
        repository.persist("/metadata/logic_db/rules", readYAML(RULE_FILE));
        repository.persist("/props", "{}\n");
        repository.persist("/states/datanodes", "");
    }
    
    @Test
    public void assertWithReadwriteSplittingDataSource() throws NoSuchFieldException, IllegalAccessException {
        assertTrue(dataSource instanceof ShardingSphereDataSource);
        Field field = ShardingSphereDataSource.class.getDeclaredField("contextManager");
        field.setAccessible(true);
        ContextManager contextManager = (ContextManager) field.get(dataSource);
        for (DataSource each : contextManager.getMetaDataContexts().getMetaData(DefaultSchema.LOGIC_NAME).getResource().getDataSources().values()) {
            assertThat(((BasicDataSource) each).getMaxTotal(), is(16));
            assertThat(((BasicDataSource) each).getUsername(), is("sa"));
        }
    }
    
    @SneakyThrows({URISyntaxException.class, IOException.class})
    private static String readYAML(final String yamlFile) {
        return Files.readAllLines(Paths.get(ClassLoader.getSystemResource(yamlFile).toURI())).stream().map(each -> each + System.lineSeparator()).collect(Collectors.joining());
    }
}

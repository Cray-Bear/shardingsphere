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

package org.apache.shardingsphere.shadow.rule;

import com.google.common.collect.Lists;
import org.apache.shardingsphere.shadow.algorithm.config.AlgorithmProvidedShadowRuleConfiguration;
import org.apache.shardingsphere.shadow.algorithm.shadow.column.ColumnRegexMatchShadowAlgorithm;
import org.apache.shardingsphere.shadow.algorithm.shadow.note.SimpleSQLNoteShadowAlgorithm;
import org.apache.shardingsphere.shadow.api.config.ShadowRuleConfiguration;
import org.apache.shardingsphere.shadow.api.config.datasource.ShadowDataSourceConfiguration;
import org.apache.shardingsphere.shadow.api.config.table.ShadowTableConfiguration;
import org.apache.shardingsphere.shadow.spi.ShadowAlgorithm;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public final class ShadowRuleTest {
    
    private ShadowRule shadowRuleWithAlgorithm;
    
    @Before
    public void init() {
        shadowRuleWithAlgorithm = new ShadowRule(createAlgorithmProvidedShadowRuleConfiguration());
    }
    
    private AlgorithmProvidedShadowRuleConfiguration createAlgorithmProvidedShadowRuleConfiguration() {
        AlgorithmProvidedShadowRuleConfiguration result = new AlgorithmProvidedShadowRuleConfiguration("shadow", Arrays.asList("ds", "ds1"), Arrays.asList("shadow_ds", "shadow_ds1"));
        result.setEnable(true);
        result.setDataSources(createDataSources());
        result.setTables(createTables());
        result.setShadowAlgorithms(createShadowAlgorithms());
        return result;
    }
    
    private Map<String, ShadowAlgorithm> createShadowAlgorithms() {
        Map<String, ShadowAlgorithm> result = new LinkedHashMap<>();
        result.put("simple-note-algorithm", createNoteShadowAlgorithm());
        result.put("user-id-insert-regex-algorithm", createColumnShadowAlgorithm("user_id", "insert"));
        result.put("user-id-update-regex-algorithm", createColumnShadowAlgorithm("user_id", "update"));
        result.put("order-id-insert-regex-algorithm", createColumnShadowAlgorithm("order_id", "insert"));
        return result;
    }
    
    private ShadowAlgorithm createNoteShadowAlgorithm() {
        SimpleSQLNoteShadowAlgorithm simpleSQLNoteShadowAlgorithm = new SimpleSQLNoteShadowAlgorithm();
        simpleSQLNoteShadowAlgorithm.setProps(createNoteProperties());
        simpleSQLNoteShadowAlgorithm.init();
        return simpleSQLNoteShadowAlgorithm;
    }
    
    private Properties createNoteProperties() {
        Properties properties = new Properties();
        properties.setProperty("shadow", "true");
        return properties;
    }
    
    private ShadowAlgorithm createColumnShadowAlgorithm(final String column, final String operation) {
        ColumnRegexMatchShadowAlgorithm columnRegexMatchShadowAlgorithm = new ColumnRegexMatchShadowAlgorithm();
        columnRegexMatchShadowAlgorithm.setProps(createColumnProperties(column, operation));
        columnRegexMatchShadowAlgorithm.init();
        return columnRegexMatchShadowAlgorithm;
    }
    
    private Properties createColumnProperties(final String column, final String operation) {
        Properties properties = new Properties();
        properties.setProperty("column", column);
        properties.setProperty("operation", operation);
        properties.setProperty("regex", "[1]");
        return properties;
    }
    
    private Map<String, ShadowTableConfiguration> createTables() {
        Map<String, ShadowTableConfiguration> result = new LinkedHashMap<>();
        result.put("t_user", new ShadowTableConfiguration(createShadowAlgorithmNames("t_user")));
        result.put("t_order", new ShadowTableConfiguration(createShadowAlgorithmNames("t_order")));
        return result;
    }
    
    private Collection<String> createShadowAlgorithmNames(final String tableName) {
        Collection<String> result = new LinkedList<>();
        result.add("simple-note-algorithm");
        if ("t_user".equals(tableName)) {
            result.add("user-id-insert-regex-algorithm");
            result.add("user-id-update-regex-algorithm");
        } else {
            result.add("order-id-insert-regex-algorithm");
        }
        return result;
    }
    
    private Map<String, ShadowDataSourceConfiguration> createDataSources() {
        Map<String, ShadowDataSourceConfiguration> result = new LinkedHashMap<>();
        result.put("ds-data-source", new ShadowDataSourceConfiguration("ds", "ds_shadow"));
        result.put("ds1-data-source", new ShadowDataSourceConfiguration("ds1", "ds1_shadow"));
        return result;
    }
    
    @Test
    public void assertNewShadowRulSuccessByShadowRuleConfiguration() {
        ShadowRule shadowRule = new ShadowRule(new ShadowRuleConfiguration("shadow", Arrays.asList("ds", "ds1"), Arrays.asList("shadow_ds", "shadow_ds1")));
        assertThat(shadowRule.isEnable(), is(false));
        assertBasicShadowRule(shadowRule);
    }
    
    private void assertBasicShadowRule(final ShadowRule shadowRule) {
        assertThat(shadowRule.getColumn(), is("shadow"));
        Map<String, String> shadowMappings = shadowRule.getShadowMappings();
        assertThat(shadowMappings.get("ds"), is("shadow_ds"));
        assertThat(shadowMappings.get("ds1"), is("shadow_ds1"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void assertNewShadowRuleByShadowRuleConfiguration() {
        new ShadowRule(new ShadowRuleConfiguration("shadow", Collections.emptyList(), Collections.emptyList()));
    }
    
    @Test
    public void assertNewShadowRulSuccessByAlgorithmProvidedShadowRuleConfiguration() {
        assertThat(shadowRuleWithAlgorithm.isEnable(), is(true));
        assertBasicShadowRule(shadowRuleWithAlgorithm);
        assertShadowDataSourceMappings(shadowRuleWithAlgorithm.getShadowDataSourceMappings());
        assertShadowTableRules(shadowRuleWithAlgorithm.getShadowTableRules());
    }
    
    private void assertShadowTableRules(final Map<String, ShadowTableRule> shadowTableRules) {
        assertThat(shadowTableRules.size(), is(2));
        shadowTableRules.forEach((key, value) -> assertShadowTableRule(key, value));
    }
    
    private void assertShadowTableRule(final String tableName, final ShadowTableRule shadowTableRule) {
        if ("t_user".equals(tableName)) {
            assertThat(shadowTableRule.getShadowAlgorithmNames().size(), is(3));
        } else {
            assertThat(shadowTableRule.getShadowAlgorithmNames().size(), is(2));
        }
    }
    
    private void assertShadowDataSourceMappings(final Map<String, String> shadowDataSourceMappings) {
        assertThat(shadowDataSourceMappings.get("ds"), is("ds_shadow"));
        assertThat(shadowDataSourceMappings.get("ds1"), is("ds1_shadow"));
    }
    
    @Test
    public void assertGetRelatedShadowTables() {
        Collection<String> relatedShadowTables = shadowRuleWithAlgorithm.getRelatedShadowTables(Lists.newArrayList("t_user", "t_auto"));
        assertThat(relatedShadowTables.size(), is(1));
        assertThat(relatedShadowTables.iterator().next(), is("t_user"));
    }
    
    @Test
    public void assertGetAllShadowTableNames() {
        Collection<String> allShadowTableNames = shadowRuleWithAlgorithm.getAllShadowTableNames();
        assertThat(allShadowTableNames.size(), is(2));
        Iterator<String> iterator = allShadowTableNames.iterator();
        assertThat(iterator.next(), is("t_user"));
        assertThat(iterator.next(), is("t_order"));
    }
    
    @Test
    public void assertGetRelatedShadowAlgorithms() {
        Optional<Collection<ShadowAlgorithm>> shadowAlgorithmsOptional = shadowRuleWithAlgorithm.getRelatedShadowAlgorithms("t_user");
        assertThat(shadowAlgorithmsOptional.isPresent(), is(true));
        Collection<ShadowAlgorithm> shadowAlgorithms = shadowAlgorithmsOptional.get();
        Iterator<ShadowAlgorithm> iterator = shadowAlgorithms.iterator();
        ShadowAlgorithm shadowAlgorithm0 = iterator.next();
        assertThat(shadowAlgorithm0.getType(), is("SIMPLE_NOTE"));
        assertThat(shadowAlgorithm0.getProps().get("shadow"), is("true"));
        ShadowAlgorithm shadowAlgorithm1 = iterator.next();
        assertThat(shadowAlgorithm1.getType(), is("COLUMN_REGEX_MATCH"));
        assertThat(shadowAlgorithm1.getProps().get("operation"), is("insert"));
        assertThat(shadowAlgorithm1.getProps().get("column"), is("user_id"));
        ShadowAlgorithm shadowAlgorithm2 = iterator.next();
        assertThat(shadowAlgorithm2.getType(), is("COLUMN_REGEX_MATCH"));
        assertThat(shadowAlgorithm2.getProps().get("operation"), is("update"));
        assertThat(shadowAlgorithm2.getProps().get("column"), is("user_id"));
    }
}

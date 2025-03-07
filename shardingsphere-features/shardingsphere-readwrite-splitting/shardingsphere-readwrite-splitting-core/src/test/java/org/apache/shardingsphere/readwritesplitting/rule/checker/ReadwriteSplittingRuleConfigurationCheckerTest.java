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

package org.apache.shardingsphere.readwritesplitting.rule.checker;

import org.apache.shardingsphere.infra.rule.checker.RuleConfigurationChecker;
import org.apache.shardingsphere.readwritesplitting.api.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.rule.ReadwriteSplittingDataSourceRuleConfiguration;
import org.apache.shardingsphere.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.spi.ordered.OrderedSPIRegistry;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ReadwriteSplittingRuleConfigurationCheckerTest {
    
    static {
        ShardingSphereServiceLoader.register(RuleConfigurationChecker.class);
    }
    
    @Test
    public void assertCheckPass() {
        ReadwriteSplittingRuleConfiguration ruleConfig = mock(ReadwriteSplittingRuleConfiguration.class);
        ReadwriteSplittingDataSourceRuleConfiguration ds0 = mock(ReadwriteSplittingDataSourceRuleConfiguration.class);
        when(ds0.getAutoAwareDataSourceName()).thenReturn("ds0");
        when(ruleConfig.getDataSources()).thenReturn(Collections.singletonList(ds0));
        RuleConfigurationChecker checker = OrderedSPIRegistry.getRegisteredServices(RuleConfigurationChecker.class, Collections.singletonList(ruleConfig)).get(ruleConfig);
        assertNotNull(checker);
        assertThat(checker, instanceOf(ReadwriteSplittingRuleConfigurationChecker.class));
        checker.check("test", ruleConfig);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertCheckNoPass() {
        ReadwriteSplittingRuleConfiguration ruleConfig = mock(ReadwriteSplittingRuleConfiguration.class);
        ReadwriteSplittingDataSourceRuleConfiguration ds0 = mock(ReadwriteSplittingDataSourceRuleConfiguration.class);
        when(ds0.getAutoAwareDataSourceName()).thenReturn("");
        when(ds0.getWriteDataSourceName()).thenReturn("");
        when(ruleConfig.getDataSources()).thenReturn(Collections.singletonList(ds0));
        RuleConfigurationChecker checker = OrderedSPIRegistry.getRegisteredServices(RuleConfigurationChecker.class, Collections.singletonList(ruleConfig)).get(ruleConfig);
        assertNotNull(checker);
        assertThat(checker, instanceOf(ReadwriteSplittingRuleConfigurationChecker.class));
        checker.check("test", ruleConfig);
    }
}

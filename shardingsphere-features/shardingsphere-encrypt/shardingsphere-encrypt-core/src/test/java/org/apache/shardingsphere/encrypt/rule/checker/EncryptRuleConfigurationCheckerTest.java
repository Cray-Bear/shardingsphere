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

package org.apache.shardingsphere.encrypt.rule.checker;

import org.apache.shardingsphere.encrypt.api.config.EncryptRuleConfiguration;
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmConfiguration;
import org.apache.shardingsphere.infra.rule.checker.RuleConfigurationChecker;
import org.apache.shardingsphere.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.spi.ordered.OrderedSPIRegistry;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class EncryptRuleConfigurationCheckerTest {
    
    static {
        ShardingSphereServiceLoader.register(RuleConfigurationChecker.class);
    }
    
    @Test
    public void assertCheckPass() {
        EncryptRuleConfiguration ruleConfig = mock(EncryptRuleConfiguration.class);
        ShardingSphereAlgorithmConfiguration algorithmConfiguration = mock(ShardingSphereAlgorithmConfiguration.class);
        when(ruleConfig.getEncryptors()).thenReturn(Collections.singletonMap("type1", algorithmConfiguration));
        RuleConfigurationChecker checker = OrderedSPIRegistry.getRegisteredServices(RuleConfigurationChecker.class, Collections.singleton(ruleConfig)).get(ruleConfig);
        assertNotNull(checker);
        assertThat(checker, instanceOf(EncryptRuleConfigurationChecker.class));
        checker.check("test", ruleConfig);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertCheckNoPass() {
        EncryptRuleConfiguration ruleConfig = mock(EncryptRuleConfiguration.class);
        when(ruleConfig.getEncryptors()).thenReturn(Collections.emptyMap());
        RuleConfigurationChecker checker = OrderedSPIRegistry.getRegisteredServices(RuleConfigurationChecker.class, Collections.singleton(ruleConfig)).get(ruleConfig);
        assertNotNull(checker);
        assertThat(checker, instanceOf(EncryptRuleConfigurationChecker.class));
        checker.check("test", ruleConfig);
    }
}

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

package org.apache.shardingsphere.sharding.rewrite.token;

import org.apache.shardingsphere.infra.binder.segment.insert.keygen.GeneratedKeyContext;
import org.apache.shardingsphere.infra.binder.statement.dml.InsertStatementContext;
import org.apache.shardingsphere.sharding.rewrite.token.generator.impl.keygen.GeneratedKeyForUseDefaultInsertColumnsTokenGenerator;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.column.InsertColumnsSegment;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class GeneratedKeyForUseDefaultInsertColumnsTokenGeneratorTest {

    private static final int TEST_STOP_INDEX = 4;

    private static final String TEST_COLUMN_NAME = "TEST_COLUMN_NAME";

    @Test
    public void assertGenerateSQLToken() {
        InsertColumnsSegment insertColumnsSegment = mock(InsertColumnsSegment.class);
        when(insertColumnsSegment.getStopIndex()).thenReturn(TEST_STOP_INDEX);
        InsertStatementContext insertStatementContext = mock(InsertStatementContext.class, RETURNS_DEEP_STUBS);
        when(insertStatementContext.getSqlStatement().getInsertColumns()).thenReturn(Optional.of(insertColumnsSegment));
        GeneratedKeyContext generatedKeyContext = mock(GeneratedKeyContext.class);
        when(generatedKeyContext.getColumnName()).thenReturn(TEST_COLUMN_NAME);
        when(insertStatementContext.getGeneratedKeyContext()).thenReturn(Optional.of(generatedKeyContext));
        when(insertStatementContext.getColumnNames()).thenReturn(Collections.emptyList());
        GeneratedKeyForUseDefaultInsertColumnsTokenGenerator generatedKeyForUseDefaultInsertColumnsTokenGenerator = new GeneratedKeyForUseDefaultInsertColumnsTokenGenerator();
        assertThat(generatedKeyForUseDefaultInsertColumnsTokenGenerator.generateSQLToken(insertStatementContext).toString(), is(("(" + TEST_COLUMN_NAME) + ")"));
    }
}

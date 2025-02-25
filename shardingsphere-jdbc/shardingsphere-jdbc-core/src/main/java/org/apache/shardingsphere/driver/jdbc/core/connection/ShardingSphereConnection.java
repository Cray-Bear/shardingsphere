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

package org.apache.shardingsphere.driver.jdbc.core.connection;

import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.shardingsphere.driver.jdbc.adapter.AbstractConnectionAdapter;
import org.apache.shardingsphere.driver.jdbc.core.datasource.metadata.ShardingSphereDatabaseMetaData;
import org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSpherePreparedStatement;
import org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSphereStatement;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.ConnectionMode;
import org.apache.shardingsphere.infra.executor.sql.prepare.driver.jdbc.ExecutorJDBCManager;
import org.apache.shardingsphere.infra.executor.sql.prepare.driver.jdbc.StatementOption;
import org.apache.shardingsphere.infra.transaction.TransactionHolder;
import org.apache.shardingsphere.transaction.core.TransactionType;
import org.apache.shardingsphere.transaction.spi.ShardingSphereTransactionManager;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ShardingSphere Connection.
 */
@Getter
public final class ShardingSphereConnection extends AbstractConnectionAdapter implements ExecutorJDBCManager {
    
    private final String schemaName;
    
    private final Map<String, DataSource> dataSourceMap;
    
    private final ContextManager contextManager;
    
    private final TransactionType transactionType;
    
    private final ShardingSphereTransactionManager transactionManager;
    
    @Getter(AccessLevel.NONE)
    private boolean autoCommit = true;
    
    public ShardingSphereConnection(final String schemaName, final Map<String, DataSource> dataSourceMap, final ContextManager contextManager, final TransactionType transactionType) {
        this.schemaName = schemaName;
        this.dataSourceMap = dataSourceMap;
        this.contextManager = contextManager;
        this.transactionType = transactionType;
        transactionManager = contextManager.getTransactionContexts().getEngines().get(schemaName).getTransactionManager(transactionType);
    }
    
    /**
     * Get database connection.
     *
     * @param dataSourceName data source name
     * @return database connection
     * @throws SQLException SQL exception
     */
    public Connection getConnection(final String dataSourceName) throws SQLException {
        return getConnections(dataSourceName, 1, ConnectionMode.MEMORY_STRICTLY).get(0);
    }
    
    @Override
    public List<Connection> getConnections(final String dataSourceName, final int connectionSize, final ConnectionMode connectionMode) throws SQLException {
        DataSource dataSource = dataSourceMap.get(dataSourceName);
        Preconditions.checkState(null != dataSource, "Missing the data source name: '%s'", dataSourceName);
        Collection<Connection> connections;
        synchronized (getCachedConnections()) {
            connections = getCachedConnections().get(dataSourceName);
        }
        List<Connection> result;
        if (connections.size() >= connectionSize) {
            result = new ArrayList<>(connections).subList(0, connectionSize);
        } else if (!connections.isEmpty()) {
            result = new ArrayList<>(connectionSize);
            result.addAll(connections);
            List<Connection> newConnections = createConnections(dataSourceName, dataSource, connectionSize - connections.size(), connectionMode);
            result.addAll(newConnections);
            synchronized (getCachedConnections()) {
                getCachedConnections().putAll(dataSourceName, newConnections);
            }
        } else {
            result = new ArrayList<>(createConnections(dataSourceName, dataSource, connectionSize, connectionMode));
            synchronized (getCachedConnections()) {
                getCachedConnections().putAll(dataSourceName, result);
            }
        }
        return result;
    }
    
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private List<Connection> createConnections(final String dataSourceName, final DataSource dataSource, final int connectionSize, final ConnectionMode connectionMode) throws SQLException {
        if (1 == connectionSize) {
            Connection connection = createConnection(dataSourceName, dataSource);
            replayMethodsInvocation(connection);
            return Collections.singletonList(connection);
        }
        if (ConnectionMode.CONNECTION_STRICTLY == connectionMode) {
            return createConnections(dataSourceName, dataSource, connectionSize);
        }
        synchronized (dataSource) {
            return createConnections(dataSourceName, dataSource, connectionSize);
        }
    }
    
    private List<Connection> createConnections(final String dataSourceName, final DataSource dataSource, final int connectionSize) throws SQLException {
        List<Connection> result = new ArrayList<>(connectionSize);
        for (int i = 0; i < connectionSize; i++) {
            try {
                Connection connection = createConnection(dataSourceName, dataSource);
                replayMethodsInvocation(connection);
                result.add(connection);
            } catch (final SQLException ex) {
                for (Connection each : result) {
                    each.close();
                }
                throw new SQLException(String.format("Can not get %d connections one time, partition succeed connection(%d) have released!", connectionSize, result.size()), ex);
            }
        }
        return result;
    }
    
    private Connection createConnection(final String dataSourceName, final DataSource dataSource) throws SQLException {
        return isInTransaction() ? transactionManager.getConnection(dataSourceName) : dataSource.getConnection();
    }
    
    private boolean isInTransaction() {
        return null != transactionManager && transactionManager.isInTransaction();
    }
    
    /**
     * Whether hold transaction or not.
     *
     * @return true or false
     */
    public boolean isHoldTransaction() {
        return (TransactionType.LOCAL == transactionType && !autoCommit) || (TransactionType.XA == transactionType && isInTransaction());
    }
    
    @SuppressWarnings("MagicConstant")
    @Override
    public Statement createStorageResource(final Connection connection, final ConnectionMode connectionMode, final StatementOption option) throws SQLException {
        return connection.createStatement(option.getResultSetType(), option.getResultSetConcurrency(), option.getResultSetHoldability());
    }

    @SuppressWarnings("MagicConstant")
    @Override
    public PreparedStatement createStorageResource(final String sql, final List<Object> parameters,
                                                   final Connection connection, final ConnectionMode connectionMode, final StatementOption option) throws SQLException {
        return option.isReturnGeneratedKeys() ? connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                : connection.prepareStatement(sql, option.getResultSetType(), option.getResultSetConcurrency(), option.getResultSetHoldability());
    }
    
    @Override
    public DatabaseMetaData getMetaData() {
        return new ShardingSphereDatabaseMetaData(this);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, autoGeneratedKeys);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    @Override
    public Statement createStatement() {
        return new ShardingSphereStatement(this);
    }
    
    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) {
        return new ShardingSphereStatement(this, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
        return new ShardingSphereStatement(this, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public boolean getAutoCommit() {
        return autoCommit;
    }
    
    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        if (TransactionType.LOCAL == transactionType) {
            this.autoCommit = autoCommit;
            recordMethodInvocation(Connection.class, "setAutoCommit", new Class[]{boolean.class}, new Object[]{autoCommit});
            getForceExecuteTemplate().execute(getCachedConnections().values(), connection -> connection.setAutoCommit(autoCommit));
            TransactionHolder.setInTransaction();
            return;
        }
        if (autoCommit != transactionManager.isInTransaction()) {
            return;
        }
        if (autoCommit && transactionManager.isInTransaction()) {
            transactionManager.commit();
            return;
        }
        if (!autoCommit && !transactionManager.isInTransaction()) {
            closeCachedConnections();
            transactionManager.begin();
            TransactionHolder.setInTransaction();
        }
    }
    
    private void closeCachedConnections() throws SQLException {
        getForceExecuteTemplate().execute(getCachedConnections().values(), Connection::close);
        getCachedConnections().clear();
    }
    
    @Override
    public void commit() throws SQLException {
        try {
            if (TransactionType.LOCAL == transactionType) {
                getForceExecuteTemplate().execute(getCachedConnections().values(), Connection::commit);
            } else {
                transactionManager.commit();
            }
        } finally {
            TransactionHolder.clear();
        }
    }
    
    @Override
    public void rollback() throws SQLException {
        try {
            if (TransactionType.LOCAL == transactionType) {
                getForceExecuteTemplate().execute(getCachedConnections().values(), Connection::rollback);
            } else {
                transactionManager.rollback();
            }
        } finally {
            TransactionHolder.clear();
        }
    }
    
    @Override
    public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
        String dataSourceName = getDataSourceMap().entrySet().iterator().next().getKey();
        Connection connection = getConnection(dataSourceName);
        return connection.createArrayOf(typeName, elements);
    }
}

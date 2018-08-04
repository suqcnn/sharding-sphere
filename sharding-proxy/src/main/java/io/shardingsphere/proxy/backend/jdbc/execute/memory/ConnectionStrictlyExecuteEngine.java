/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.proxy.backend.jdbc.execute.memory;

import io.shardingsphere.core.exception.ShardingException;
import io.shardingsphere.core.merger.QueryResult;
import io.shardingsphere.core.routing.SQLRouteResult;
import io.shardingsphere.core.routing.SQLUnit;
import io.shardingsphere.proxy.backend.jdbc.connection.BackendConnection;
import io.shardingsphere.proxy.backend.jdbc.execute.JDBCExecuteEngine;
import io.shardingsphere.proxy.backend.jdbc.execute.response.ExecuteQueryResponse;
import io.shardingsphere.proxy.backend.jdbc.execute.response.ExecuteResponse;
import io.shardingsphere.proxy.backend.jdbc.execute.response.ExecuteUpdateResponse;
import io.shardingsphere.proxy.backend.jdbc.execute.response.unit.ExecuteQueryResponseUnit;
import io.shardingsphere.proxy.backend.jdbc.execute.response.unit.ExecuteResponseUnit;
import io.shardingsphere.proxy.backend.jdbc.execute.response.unit.ExecuteUpdateResponseUnit;
import io.shardingsphere.proxy.backend.jdbc.wrapper.JDBCExecutorWrapper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Connection strictly execute engine.
 *
 * @author zhaojun
 * @author zhangliang
 */
public final class ConnectionStrictlyExecuteEngine extends JDBCExecuteEngine {
    
    public ConnectionStrictlyExecuteEngine(final BackendConnection backendConnection, final JDBCExecutorWrapper jdbcExecutorWrapper) {
        super(backendConnection, jdbcExecutorWrapper);
    }
    
    @Override
    public ExecuteResponse execute(final SQLRouteResult routeResult, final boolean isReturnGeneratedKeys) throws SQLException {
        Map<String, Collection<SQLUnit>> sqlExecutionUnits = routeResult.getSQLUnitGroups();
        Entry<String, Collection<SQLUnit>> firstEntry = sqlExecutionUnits.entrySet().iterator().next();
        sqlExecutionUnits.remove(firstEntry.getKey());
        List<Future<Collection<ExecuteResponseUnit>>> futureList = asyncExecute(isReturnGeneratedKeys, sqlExecutionUnits);
        Collection<ExecuteResponseUnit> firstExecuteResponseUnits = syncExecute(isReturnGeneratedKeys, firstEntry.getKey(), firstEntry.getValue());
        return getExecuteQueryResponse(firstExecuteResponseUnits, futureList);
    }
    
    private List<Future<Collection<ExecuteResponseUnit>>> asyncExecute(final boolean isReturnGeneratedKeys, final Map<String, Collection<SQLUnit>> sqlUnitGroups) throws SQLException {
        List<Future<Collection<ExecuteResponseUnit>>> result = new LinkedList<>();
        for (Entry<String, Collection<SQLUnit>> entry : sqlUnitGroups.entrySet()) {
            final Map<SQLUnit, Statement> sqlUnitStatementMap = createSQLUnitStatement(entry.getKey(), entry.getValue(), isReturnGeneratedKeys);
            result.add(getExecutorService().submit(new Callable<Collection<ExecuteResponseUnit>>() {
                
                @Override
                public Collection<ExecuteResponseUnit> call() throws SQLException {
                    Collection<ExecuteResponseUnit> result = new LinkedList<>();
                    for (Entry<SQLUnit, Statement> each : sqlUnitStatementMap.entrySet()) {
                        result.add(executeWithoutMetadata(each.getValue(), each.getKey().getSql(), isReturnGeneratedKeys));
                    }
                    return result;
                }
            }));
        }
        return result;
    }
    
    private Map<SQLUnit, Statement> createSQLUnitStatement(final String dataSourceName, final Collection<SQLUnit> sqlUnits, final boolean isReturnGeneratedKeys) throws SQLException {
        Map<SQLUnit, Statement> result = new HashMap<>(sqlUnits.size(), 1);
        Connection connection = getBackendConnection().getConnection(dataSourceName);
        for (SQLUnit each : sqlUnits) {
            result.put(each, getJdbcExecutorWrapper().createStatement(connection, each.getSql(), isReturnGeneratedKeys));
        }
        return result;
    }
    
    private Collection<ExecuteResponseUnit> syncExecute(final boolean isReturnGeneratedKeys, final String dataSourceName, final Collection<SQLUnit> sqlUnits) throws SQLException {
        Collection<ExecuteResponseUnit> result = new LinkedList<>();
        boolean hasMetaData = false;
        Connection connection = getBackendConnection().getConnection(dataSourceName);
        for (SQLUnit each : sqlUnits) {
            String actualSQL = each.getSql();
            Statement statement = getJdbcExecutorWrapper().createStatement(connection, actualSQL, isReturnGeneratedKeys);
            ExecuteResponseUnit response;
            if (hasMetaData) {
                response = executeWithoutMetadata(statement, actualSQL, isReturnGeneratedKeys);
            } else {
                response = executeWithMetadata(statement, actualSQL, isReturnGeneratedKeys);
                hasMetaData = true;
            }
            result.add(response);
        }
        return result;
    }
    
    private ExecuteResponse getExecuteQueryResponse(final Collection<ExecuteResponseUnit> firstExecuteResponseUnits, final List<Future<Collection<ExecuteResponseUnit>>> futureList) {
        ExecuteResponseUnit firstExecuteResponseUnit = firstExecuteResponseUnits.iterator().next();
        return firstExecuteResponseUnit instanceof ExecuteQueryResponseUnit
                ? getExecuteQueryResponse((ExecuteQueryResponseUnit) firstExecuteResponseUnit, firstExecuteResponseUnits, futureList) : getExecuteUpdateResponse(firstExecuteResponseUnits, futureList);
    }
    
    private ExecuteResponse getExecuteQueryResponse(
            final ExecuteQueryResponseUnit firstExecuteResponseUnit, final Collection<ExecuteResponseUnit> firstExecuteResponseUnits, final List<Future<Collection<ExecuteResponseUnit>>> futureList) {
        ExecuteQueryResponse result = new ExecuteQueryResponse(firstExecuteResponseUnit.getQueryResponsePackets());
        for (ExecuteResponseUnit each : firstExecuteResponseUnits) {
            result.getQueryResults().add(((ExecuteQueryResponseUnit) each).getQueryResult());
        }
        for (Future<Collection<ExecuteResponseUnit>> each : futureList) {
            try {
                Collection<ExecuteResponseUnit> executeResponses = each.get();
                for (ExecuteResponseUnit executeResponse : executeResponses) {
                    if (executeResponse instanceof ExecuteQueryResponseUnit) {
                        result.getQueryResults().add(((ExecuteQueryResponseUnit) executeResponse).getQueryResult());
                    }
                }
            } catch (final InterruptedException | ExecutionException ex) {
                throw new ShardingException(ex.getMessage(), ex);
            }
        }
        return result;
    }
    
    private ExecuteResponse getExecuteUpdateResponse(final Collection<ExecuteResponseUnit> firstExecuteResponseUnits, final List<Future<Collection<ExecuteResponseUnit>>> futureList) {
        ExecuteUpdateResponse result = new ExecuteUpdateResponse(firstExecuteResponseUnits);
        for (Future<Collection<ExecuteResponseUnit>> each : futureList) {
            try {
                for (ExecuteResponseUnit executeResponse : each.get()) {
                    result.getPackets().add(((ExecuteUpdateResponseUnit) executeResponse).getOkPacket());
                }
            } catch (final InterruptedException | ExecutionException ex) {
                throw new ShardingException(ex.getMessage(), ex);
            }
        }
        return result;
    }
    
    @Override
    protected void setFetchSize(final Statement statement) {
    }
    
    @Override
    protected QueryResult createQueryResult(final ResultSet resultSet) throws SQLException {
        return new MemoryQueryResult(resultSet);
    }
}

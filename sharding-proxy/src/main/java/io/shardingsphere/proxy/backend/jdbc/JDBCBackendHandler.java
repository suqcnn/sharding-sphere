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

package io.shardingsphere.proxy.backend.jdbc;

import com.google.common.base.Optional;
import io.shardingsphere.core.constant.DatabaseType;
import io.shardingsphere.core.constant.SQLType;
import io.shardingsphere.core.constant.TransactionType;
import io.shardingsphere.core.merger.MergeEngineFactory;
import io.shardingsphere.core.merger.MergedResult;
import io.shardingsphere.core.metadata.table.executor.TableMetaDataLoader;
import io.shardingsphere.core.parsing.parser.sql.SQLStatement;
import io.shardingsphere.core.parsing.parser.sql.dml.insert.InsertStatement;
import io.shardingsphere.core.routing.SQLRouteResult;
import io.shardingsphere.proxy.backend.BackendHandler;
import io.shardingsphere.proxy.backend.ResultPacket;
import io.shardingsphere.proxy.backend.jdbc.execute.JDBCExecuteEngine;
import io.shardingsphere.proxy.backend.jdbc.execute.response.ExecuteQueryResponse;
import io.shardingsphere.proxy.backend.jdbc.execute.response.ExecuteResponse;
import io.shardingsphere.proxy.backend.jdbc.execute.response.ExecuteUpdateResponse;
import io.shardingsphere.proxy.config.RuleRegistry;
import io.shardingsphere.proxy.config.ProxyTableMetaDataConnectionManager;
import io.shardingsphere.proxy.transport.mysql.constant.ServerErrorCode;
import io.shardingsphere.proxy.transport.mysql.packet.command.CommandResponsePackets;
import io.shardingsphere.proxy.transport.mysql.packet.command.query.QueryResponsePackets;
import io.shardingsphere.proxy.transport.mysql.packet.generic.ErrPacket;
import io.shardingsphere.proxy.transport.mysql.packet.generic.OKPacket;
import io.shardingsphere.proxy.util.ExecutorContext;
import lombok.RequiredArgsConstructor;

import javax.transaction.Status;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Backend handler via JDBC to connect databases.
 *
 * @author zhaojun
 * @author zhangliang
 */
@RequiredArgsConstructor
public final class JDBCBackendHandler implements BackendHandler {
    
    private static final RuleRegistry RULE_REGISTRY = RuleRegistry.getInstance();
    
    private final String sql;
    
    private final JDBCExecuteEngine executeEngine;
    
    private ExecuteResponse executeResponse;
    
    private MergedResult mergedResult;
    
    private int currentSequenceId;
    
    @Override
    public CommandResponsePackets execute() {
        try {
            return execute(executeEngine.getJdbcExecutorWrapper().route(sql, DatabaseType.MySQL));
        } catch (final SQLException ex) {
            return new CommandResponsePackets(new ErrPacket(1, ex));
            // CHECKSTYLE:OFF
        } catch (final Exception ex) {
            // CHECKSTYLE:ON
            Optional<SQLException> sqlException = findSQLException(ex);
            return sqlException.isPresent()
                    ? new CommandResponsePackets(new ErrPacket(1, sqlException.get())) : new CommandResponsePackets(new ErrPacket(1, ServerErrorCode.ER_STD_UNKNOWN_EXCEPTION, ex.getMessage()));
        }
    }
    
    private CommandResponsePackets execute(final SQLRouteResult routeResult) throws SQLException {
        if (routeResult.getExecutionUnits().isEmpty()) {
            return new CommandResponsePackets(new OKPacket(1));
        }
        SQLStatement sqlStatement = routeResult.getSqlStatement();
        boolean isReturnGeneratedKeys = sqlStatement instanceof InsertStatement;
        if (isUnsupportedXA(sqlStatement.getType())) {
            return new CommandResponsePackets(new ErrPacket(1, 
                    ServerErrorCode.ER_ERROR_ON_MODIFYING_GTID_EXECUTED_TABLE, sqlStatement.getTables().isSingleTable() ? sqlStatement.getTables().getSingleTableName() : "unknown_table"));
        }
        executeResponse = executeEngine.execute(routeResult, isReturnGeneratedKeys);
        if (!RULE_REGISTRY.isMasterSlaveOnly() && SQLType.DDL == sqlStatement.getType() && !sqlStatement.getTables().isEmpty()) {
            String logicTableName = sqlStatement.getTables().getSingleTableName();
            TableMetaDataLoader tableMetaDataLoader = new TableMetaDataLoader(
                    ExecutorContext.getInstance().getExecutorService(), new ProxyTableMetaDataConnectionManager(RULE_REGISTRY.getBackendDataSource()));
            RULE_REGISTRY.getMetaData().getTable().put(logicTableName, tableMetaDataLoader.load(logicTableName, RULE_REGISTRY.getShardingRule()));
        }
        return merge(sqlStatement);
    }
    
    private boolean isUnsupportedXA(final SQLType sqlType) throws SQLException {
        return TransactionType.XA == RULE_REGISTRY.getTransactionType() && SQLType.DDL == sqlType && Status.STATUS_NO_TRANSACTION != RULE_REGISTRY.getTransactionManager().getStatus();
    }
    
    private CommandResponsePackets merge(final SQLStatement sqlStatement) throws SQLException {
        if (executeResponse instanceof ExecuteUpdateResponse) {
            return ((ExecuteUpdateResponse) executeResponse).merge();
        }
        mergedResult = MergeEngineFactory.newInstance(
                RULE_REGISTRY.getShardingRule(), ((ExecuteQueryResponse) executeResponse).getQueryResults(), sqlStatement, RULE_REGISTRY.getMetaData().getTable()).merge();
        QueryResponsePackets result = ((ExecuteQueryResponse) executeResponse).getQueryResponsePackets();
        currentSequenceId = result.getPackets().size();
        return result;
    }
    
    private Optional<SQLException> findSQLException(final Exception exception) {
        if (null == exception.getCause()) {
            return Optional.absent();
        }
        if (exception.getCause() instanceof SQLException) {
            return Optional.of((SQLException) exception.getCause());
        }
        if (null == exception.getCause().getCause()) {
            return Optional.absent();
        }
        if (exception.getCause().getCause() instanceof SQLException) {
            return Optional.of((SQLException) exception.getCause());
        }
        return Optional.absent();
    }
    
    @Override
    public boolean next() throws SQLException {
        return null != mergedResult && mergedResult.next();
    }
    
    @Override
    public ResultPacket getResultValue() throws SQLException {
        QueryResponsePackets queryResponsePackets = ((ExecuteQueryResponse) executeResponse).getQueryResponsePackets();
        int columnCount = queryResponsePackets.getColumnCount();
        List<Object> data = new ArrayList<>(columnCount);
        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            data.add(mergedResult.getValue(columnIndex, Object.class));
        }
        return new ResultPacket(++currentSequenceId, data, columnCount, queryResponsePackets.getColumnTypes());
    }
}

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

package io.shardingsphere.proxy.backend.netty;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.channel.Channel;
import io.netty.channel.pool.SimpleChannelPool;
import io.shardingsphere.core.constant.DatabaseType;
import io.shardingsphere.core.constant.SQLType;
import io.shardingsphere.core.merger.MergeEngineFactory;
import io.shardingsphere.core.merger.MergedResult;
import io.shardingsphere.core.merger.QueryResult;
import io.shardingsphere.core.metadata.table.executor.TableMetaDataLoader;
import io.shardingsphere.core.parsing.SQLJudgeEngine;
import io.shardingsphere.core.parsing.parser.sql.SQLStatement;
import io.shardingsphere.core.routing.SQLExecutionUnit;
import io.shardingsphere.core.routing.SQLRouteResult;
import io.shardingsphere.core.routing.StatementRoutingEngine;
import io.shardingsphere.core.routing.router.masterslave.MasterSlaveRouter;
import io.shardingsphere.proxy.backend.BackendHandler;
import io.shardingsphere.proxy.backend.ResultPacket;
import io.shardingsphere.proxy.backend.netty.mysql.MySQLQueryResult;
import io.shardingsphere.proxy.config.RuleRegistry;
import io.shardingsphere.proxy.config.ProxyTableMetaDataConnectionManager;
import io.shardingsphere.proxy.transport.common.packet.CommandPacketRebuilder;
import io.shardingsphere.proxy.transport.common.packet.DatabasePacket;
import io.shardingsphere.proxy.transport.mysql.constant.ColumnType;
import io.shardingsphere.proxy.transport.mysql.packet.command.CommandResponsePackets;
import io.shardingsphere.proxy.transport.mysql.packet.generic.ErrPacket;
import io.shardingsphere.proxy.transport.mysql.packet.generic.OKPacket;
import io.shardingsphere.proxy.util.ExecutorContext;
import io.shardingsphere.proxy.util.MySQLResultCache;
import io.shardingsphere.proxy.util.SynchronizedFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SQL packets backend handler.
 *
 * @author wangkai
 * @author linjiaqi
 * @author panjuan
 */
@Slf4j
@Getter
public final class SQLPacketsBackendHandler implements BackendHandler {
    
    private static final RuleRegistry RULE_REGISTRY = RuleRegistry.getInstance();
    
    private final CommandPacketRebuilder rebuilder;
    
    private final DatabaseType databaseType;
    
    private final Map<String, List<Channel>> channelsMap = Maps.newHashMap();
    
    private SynchronizedFuture synchronizedFuture;
    
    private int currentSequenceId;
    
    private int columnCount;
    
    private MergedResult mergedResult;
    
    public SQLPacketsBackendHandler(final CommandPacketRebuilder rebuilder, final DatabaseType databaseType) {
        this.rebuilder = rebuilder;
        this.databaseType = databaseType;
    }
    
    @Override
    public CommandResponsePackets execute() {
        return RULE_REGISTRY.isMasterSlaveOnly() ? executeForMasterSlave() : executeForSharding();
    }
    
    private CommandResponsePackets executeForMasterSlave() {
        String dataSourceName = new MasterSlaveRouter(RULE_REGISTRY.getMasterSlaveRule(), RULE_REGISTRY.isShowSQL()).route(rebuilder.sql()).iterator().next();
        synchronizedFuture = new SynchronizedFuture(1);
        MySQLResultCache.getInstance().putFuture(rebuilder.connectionId(), synchronizedFuture);
        executeCommand(dataSourceName, rebuilder.sql());
        List<QueryResult> queryResults = synchronizedFuture.get(RULE_REGISTRY.getProxyBackendConnectionTimeout(), TimeUnit.SECONDS);
        MySQLResultCache.getInstance().deleteFuture(rebuilder.connectionId());
        List<CommandResponsePackets> packets = new LinkedList<>();
        for (QueryResult each : queryResults) {
            packets.add(((MySQLQueryResult) each).getCommandResponsePackets());
        }
        return merge(new SQLJudgeEngine(rebuilder.sql()).judge(), packets, queryResults);
    }
    
    private CommandResponsePackets executeForSharding() {
        StatementRoutingEngine routingEngine = new StatementRoutingEngine(
                RULE_REGISTRY.getShardingRule(), RULE_REGISTRY.getMetaData().getTable(), databaseType, RULE_REGISTRY.isShowSQL(), RULE_REGISTRY.getMetaData().getDataSource());
        SQLRouteResult routeResult = routingEngine.route(rebuilder.sql());
        if (routeResult.getExecutionUnits().isEmpty()) {
            return new CommandResponsePackets(new OKPacket(1));
        }
        synchronizedFuture = new SynchronizedFuture(routeResult.getExecutionUnits().size());
        MySQLResultCache.getInstance().putFuture(rebuilder.connectionId(), synchronizedFuture);
        for (SQLExecutionUnit each : routeResult.getExecutionUnits()) {
            executeCommand(each.getDataSource(), each.getSqlUnit().getSql());
        }
        List<QueryResult> queryResults = synchronizedFuture.get(RULE_REGISTRY.getProxyBackendConnectionTimeout(), TimeUnit.SECONDS);
        MySQLResultCache.getInstance().deleteFuture(rebuilder.connectionId());
        
        List<CommandResponsePackets> packets = Lists.newArrayListWithCapacity(queryResults.size());
        for (QueryResult each : queryResults) {
            MySQLQueryResult queryResult = (MySQLQueryResult) each;
            if (0 == currentSequenceId) {
                currentSequenceId = queryResult.getCurrentSequenceId();
            }
            if (0 == columnCount) {
                columnCount = queryResult.getColumnCount();
            }
            packets.add(queryResult.getCommandResponsePackets());
        }
        CommandResponsePackets result = merge(routeResult.getSqlStatement(), packets, queryResults);
        SQLStatement sqlStatement = routeResult.getSqlStatement();
        if (!RULE_REGISTRY.isMasterSlaveOnly() && SQLType.DDL == sqlStatement.getType() && !sqlStatement.getTables().isEmpty()) {
            String logicTableName = sqlStatement.getTables().getSingleTableName();
            TableMetaDataLoader tableMetaDataLoader = new TableMetaDataLoader(
                    ExecutorContext.getInstance().getExecutorService(), new ProxyTableMetaDataConnectionManager(RULE_REGISTRY.getBackendDataSource()));
            RULE_REGISTRY.getMetaData().getTable().put(logicTableName, tableMetaDataLoader.load(logicTableName, RULE_REGISTRY.getShardingRule()));
        }
        return result;
    }
    
    private void executeCommand(final String dataSourceName, final String sql) {
        try {
            if (null == channelsMap.get(dataSourceName)) {
                channelsMap.put(dataSourceName, Lists.<Channel>newArrayList());
            }
            SimpleChannelPool pool = ShardingProxyClient.getInstance().getPoolMap().get(dataSourceName);
            Channel channel = pool.acquire().get(RULE_REGISTRY.getProxyBackendConnectionTimeout(), TimeUnit.SECONDS);
            channelsMap.get(dataSourceName).add(channel);
            MySQLResultCache.getInstance().putConnection(channel.id().asShortText(), rebuilder.connectionId());
            channel.writeAndFlush(rebuilder.rebuild(rebuilder.sequenceId(), rebuilder.connectionId(), sql));
        } catch (final InterruptedException | ExecutionException | TimeoutException ex) {
            log.error(ex.getMessage(), ex);
        }
    }
    
    private CommandResponsePackets merge(final SQLStatement sqlStatement, final List<CommandResponsePackets> packets, final List<QueryResult> queryResults) {
        CommandResponsePackets headPackets = new CommandResponsePackets();
        for (CommandResponsePackets each : packets) {
            headPackets.getPackets().add(each.getHeadPacket());
        }
        for (DatabasePacket each : headPackets.getPackets()) {
            if (each instanceof ErrPacket) {
                return new CommandResponsePackets(each);
            }
        }
        if (SQLType.DML == sqlStatement.getType()) {
            return mergeDML(headPackets);
        }
        if (SQLType.DQL == sqlStatement.getType() || SQLType.DAL == sqlStatement.getType()) {
            return mergeDQLorDAL(sqlStatement, packets, queryResults);
        }
        return packets.get(0);
    }
    
    private CommandResponsePackets mergeDML(final CommandResponsePackets firstPackets) {
        int affectedRows = 0;
        long lastInsertId = 0;
        for (DatabasePacket each : firstPackets.getPackets()) {
            if (each instanceof OKPacket) {
                OKPacket okPacket = (OKPacket) each;
                affectedRows += okPacket.getAffectedRows();
                lastInsertId = okPacket.getLastInsertId();
            }
        }
        return new CommandResponsePackets(new OKPacket(1, affectedRows, lastInsertId));
    }
    
    private CommandResponsePackets mergeDQLorDAL(final SQLStatement sqlStatement, final List<CommandResponsePackets> packets, final List<QueryResult> queryResults) {
        try {
            mergedResult = MergeEngineFactory.newInstance(RULE_REGISTRY.getShardingRule(), queryResults, sqlStatement, RULE_REGISTRY.getMetaData().getTable()).merge();
        } catch (final SQLException ex) {
            return new CommandResponsePackets(new ErrPacket(1, ex));
        }
        return packets.get(0);
    }
    
    @Override
    public boolean next() throws SQLException {
        if (null == mergedResult || !mergedResult.next()) {
            for (Entry<String, List<Channel>> entry : channelsMap.entrySet()) {
                for (Channel each : entry.getValue()) {
                    ShardingProxyClient.getInstance().getPoolMap().get(entry.getKey()).release(each);
                }
            }
            return false;
        }
        return true;
    }
    
    @Override
    public ResultPacket getResultValue() throws SQLException {
        List<Object> data = new ArrayList<>(columnCount);
        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            data.add(mergedResult.getValue(columnIndex, Object.class));
        }
        return new ResultPacket(++currentSequenceId, data, columnCount, Collections.<ColumnType>emptyList());
    }
}

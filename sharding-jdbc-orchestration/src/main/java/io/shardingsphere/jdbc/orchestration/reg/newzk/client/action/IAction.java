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

package io.shardingsphere.jdbc.orchestration.reg.newzk.client.action;

import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;

import java.util.List;

/*
 * The basic actions of the client.
 *
 * @author lidongbo
 */
public interface IAction {
    
    /**
     * Get string type data.
     *
     * @param key key
     * @return data String
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    String getDataString(String key) throws KeeperException, InterruptedException;
    
    /**
     * Get string type data.
     *
     * @param key key
     * @return data
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    byte[] getData(String key) throws KeeperException, InterruptedException;
    
    /**
     * Get string type data.
     *
     * @param key key
     * @param callback callback
     * @param ctx ctx
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    void getData(String key, AsyncCallback.DataCallback callback, Object ctx) throws KeeperException, InterruptedException;
    
    /**
     * Check exist.
     *
     * @param key key
     * @return exist
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    boolean checkExists(String key) throws KeeperException, InterruptedException;
    
    /**
     * Check exist.
     *
     * @param key key
     * @param watcher watcher
     * @return exist
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    boolean checkExists(String key, Watcher watcher) throws KeeperException, InterruptedException;
    
    /**
     * Get children's keys.
     *
     * @param key key
     * @return children keys
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    List<String> getChildren(String key) throws KeeperException, InterruptedException;
    
    /**
     * Only create target node.
     *
     * @param key key
     * @param value value
     * @param createMode createMode
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    void createCurrentOnly(String key, String value, CreateMode createMode) throws KeeperException, InterruptedException;
    
    /**
     * Update.
     *
     * @param key key
     * @param value value
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    void update(String key, String value) throws KeeperException, InterruptedException;
    
    /**
     * Only delete target node..
     *
     * @param key key
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    void deleteOnlyCurrent(String key) throws KeeperException, InterruptedException;
    
    /**
     * Only delete target node..
     *
     * @param key key
     * @param callback callback
     * @param ctx ctx
     * @throws KeeperException Zookeeper Exception
     * @throws InterruptedException InterruptedException
     */
    void deleteOnlyCurrent(String key, AsyncCallback.VoidCallback callback, Object ctx) throws KeeperException, InterruptedException;
}

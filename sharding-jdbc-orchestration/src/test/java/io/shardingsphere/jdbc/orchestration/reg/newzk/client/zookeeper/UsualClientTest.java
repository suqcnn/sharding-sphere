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

package io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper;

import io.shardingsphere.jdbc.orchestration.reg.newzk.client.action.IClient;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.base.BaseClientTest;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.base.TestSupport;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.junit.Test;

import java.io.IOException;

public class UsualClientTest extends BaseClientTest {
    
    @Override
    protected IClient createClient(final ClientFactory creator) throws IOException, InterruptedException {
        return creator.setClientNamespace(TestSupport.ROOT).authorization(TestSupport.AUTH, TestSupport.AUTH.getBytes(), ZooDefs.Ids.CREATOR_ALL_ACL)
                .newClient(TestSupport.SERVERS, TestSupport.SESSION_TIMEOUT).start();
    }
    
    @Test
    public void assertCreateRoot() throws KeeperException, InterruptedException {
        super.createRoot(getTestClient());
    }
    
    @Test
    public void assertCreateChild() throws KeeperException, InterruptedException {
        super.createChild(getTestClient());
    }
    
    @Test
    public void assertDeleteBranch() throws KeeperException, InterruptedException {
        super.deleteBranch(getTestClient());
    }
    
    @Test
    public void assertExisted() throws KeeperException, InterruptedException {
        super.isExisted(getTestClient());
    }
    
    @Test
    public void assertGet() throws KeeperException, InterruptedException {
        super.get(getTestClient());
    }
    
    @Test
    public void assertAsyncGet() throws KeeperException, InterruptedException {
        super.asyncGet(getTestClient());
    }
    
    @Test
    public void assertGetChildrenKeys() throws KeeperException, InterruptedException {
        super.getChildrenKeys(getTestClient());
    }
    
    @Test
    public void assertPersist() throws KeeperException, InterruptedException {
        super.persist(getTestClient());
    }
    
    @Test
    public void assertPersistEphemeral() throws KeeperException, InterruptedException {
        super.persistEphemeral(getTestClient());
    }
    
    @Test
    public void assertDelAllChildren() throws KeeperException, InterruptedException {
        super.delAllChildren(getTestClient());
    }
    
    @Test
    public void assertWatch() throws KeeperException, InterruptedException {
        super.watch(getTestClient());
    }
    
    @Test
    public void assertWatchRegister() throws KeeperException, InterruptedException {
        super.watchRegister(getTestClient());
    }
    
    @Test
    public void assertClose() {
        super.close(getTestClient());
    }
}

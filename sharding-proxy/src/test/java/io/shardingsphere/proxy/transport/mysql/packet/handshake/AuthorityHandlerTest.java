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

package io.shardingsphere.proxy.transport.mysql.packet.handshake;

import com.google.common.primitives.Bytes;
import io.shardingsphere.core.rule.ProxyAuthority;
import io.shardingsphere.proxy.config.RuleRegistry;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class AuthorityHandlerTest {
    
    private final RuleRegistry ruleRegistry = RuleRegistry.getInstance();
    
    private final AuthorityHandler authorityHandler = new AuthorityHandler();
    
    private final byte[] part1 = {84, 85, 115, 77, 68, 116, 85, 78};

    private final byte[] part2 = {83, 121, 75, 81, 87, 56, 120, 112, 73, 109, 77, 69};
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        reviseRuleRegistry();
        reviseAuthorityHandler();
    }
    
    private void reviseRuleRegistry() throws NoSuchFieldException, IllegalAccessException {
        ProxyAuthority proxyAuthority = new ProxyAuthority();
        proxyAuthority.setUsername("root");
        proxyAuthority.setPassword("root");
        Field field = ruleRegistry.getClass().getDeclaredField("proxyAuthority");
        field.setAccessible(true);
        field.set(ruleRegistry, proxyAuthority);
    }
    
    private void reviseAuthorityHandler() throws NoSuchFieldException, IllegalAccessException {
        AuthPluginData authPluginData = new AuthPluginData(part1, part2);
        Field field = authorityHandler.getClass().getDeclaredField("authPluginData");
        field.setAccessible(true);
        field.set(authorityHandler, authPluginData);
    }
    
    @Test
    public void assertLogin() {
        byte[] authResponse = {-27, 89, -20, -27, 65, -120, -64, -101, 86, -100, -108, -100, 6, -125, -37, 117, 14, -43, 95, -113};
        assertTrue(authorityHandler.login("root", authResponse));
    }
    
    @Test
    public void assertLoginWithoutPassword() {
        ruleRegistry.getProxyAuthority().setPassword("");
        byte[] authResponse = {-27, 89, -20, -27, 65, -120, -64, -101, 86, -100, -108, -100, 6, -125, -37, 117, 14, -43, 95, -113};
        assertTrue(authorityHandler.login("root", authResponse));
    }
    
    @Test
    public void assertGetAuthPluginData() {
        assertThat(authorityHandler.getAuthPluginData().getAuthPluginData(), is(Bytes.concat(part1, part2)));
    }
}

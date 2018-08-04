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

package io.shardingsphere.core.transaction;

import io.shardingsphere.core.constant.TransactionType;
import io.shardingsphere.core.transaction.event.TransactionEvent;
import io.shardingsphere.core.transaction.event.WeakXaTransactionEvent;
import io.shardingsphere.core.transaction.spi.TransactionManager;
import lombok.Getter;

/**
 * Hold Transaction Context.
 *
 * @author zhaojun
 */
@Getter
public final class TransactionContext {
    
    private TransactionManager transactionManager;
    
    private TransactionType transactionType = TransactionType.XA;
    
    private Class<? extends TransactionEvent> transactionEventClazz = WeakXaTransactionEvent.class;
    
    public TransactionContext() {
    }

    public TransactionContext(final TransactionManager transactionManager, final TransactionType transactionType, final Class<? extends TransactionEvent> clazz) {
        this.transactionManager = transactionManager;
        this.transactionType = transactionType;
        this.transactionEventClazz = clazz;
    }
}

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

package io.shardingsphere.core.transaction.event;

import io.shardingsphere.core.constant.TCLType;
import io.shardingsphere.core.transaction.TransactionContextHolder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Create TransactionEvent for current thread.
 *
 * @author zhaojun
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransactionEventFactory {
    
    /**
     * Create transaction event.
     *
     * @param tclType TCL type
     * @return transaction event
     */
    public static TransactionEvent create(final TCLType tclType) {
        switch (TransactionContextHolder.get().getTransactionType()) {
            case XA:
                return TransactionContextHolder.get().getTransactionEventClazz().isAssignableFrom(XaTransactionEvent.class)
                        ? new XaTransactionEvent(tclType, "") : new WeakXaTransactionEvent(tclType);
            case BASE:
            default:
                return null;
        }
    }
}

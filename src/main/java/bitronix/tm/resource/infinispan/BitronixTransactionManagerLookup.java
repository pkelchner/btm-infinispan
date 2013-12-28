/*
 * Copyright 2013 Patrick Kelchner
 * 
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
 */
package bitronix.tm.resource.infinispan;

import bitronix.tm.TransactionManagerServices;

import org.infinispan.transaction.lookup.TransactionManagerLookup;

import javax.transaction.TransactionManager;

/**
 * {@linkplain org.infinispan.configuration.cache.TransactionConfigurationBuilder#transactionManagerLookup(TransactionManagerLookup) Allows to configure} 
 * Bitronix as the {@code TransactionManager} used by Infinispan.
 * Bitronix won't be configurable as soon as a cache starts that uses this lookup.
 * 
 * @see BitronixCacheResourceRegistrator
 */
public final class BitronixTransactionManagerLookup implements TransactionManagerLookup {

	@Override
	public TransactionManager getTransactionManager() throws Exception {
		return TransactionManagerServices.getTransactionManager();
	}
}

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

import javax.transaction.TransactionManager;

import org.infinispan.factories.annotations.Inject;
import org.infinispan.transaction.lookup.TransactionManagerLookup;

import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.TransactionManagerServices;

/**
 * {@linkplain org.infinispan.configuration.cache.TransactionConfigurationBuilder#transactionManagerLookup(TransactionManagerLookup) Configures}
 * Bitronix as the {@code TransactionManager} used by a cache.
 * <p>
 * This Lookup also installs a cache component that registers caches as a Bitronix {@code XAResourceProducer}s. 
 * To be registered a cache must be {@linkplain org.infinispan.configuration.cache.TransactionConfigurationBuilder#transactionMode(org.infinispan.transaction.TransactionMode) transactional} 
 * and it must not {@linkplain org.infinispan.configuration.cache.TransactionConfigurationBuilder#useSynchronization(boolean) use synchronization}.
 * Otherwise it won't participate in transactions as a full {@code XAResource}.
 * How a cache handles recovery is determined by its {@linkplain org.infinispan.configuration.cache.RecoveryConfigurationBuilder recovery configuration}.
 * <p>
 * A typical configuration looks like this:
 *<pre>EmbeddedCacheManager cacheManager = new DefaultCacheManager();
 *
 *Configuration conf = new ConfigurationBuilder()
 *    .read(cacheManager.getDefaultCacheConfiguration())
 *    .transaction()
 *        .transactionManagerLookup(new BitronixTransactionManagerLookup())
 *        .transactionMode(TransactionMode.TRANSACTIONAL)
 *        .useSynchronization(false)
 *        .recovery().enable()
 *    .build();
 *
 *cacheManager.defineConfiguration("xaCache", conf);
 *Cache cache = cacheManager.getCache("xaCache");
 *</pre>
 *
 * @see org.infinispan.transaction.xa.recovery.RecoveryManager Infinispan: RecoveryManager
 */
public final class BitronixTransactionManagerLookup implements TransactionManagerLookup {
	private BitronixTransactionManager transactionManager;

	/**
	 * Constructor for dependency-injection
	 */
	@javax.inject.Inject
	public BitronixTransactionManagerLookup(BitronixTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}
	
	/**
	 * Looks up Bitronix via {@link TransactionManagerServices#getTransactionManager()}
	 */
	public BitronixTransactionManagerLookup() {
		this.transactionManager = null;
	}

	@Inject
	private void injectComponents(InfinispanXAResourceProducer resourceProducer) {
		if (resourceProducer == null) {
			throw new IllegalStateException("Failed to initialize cache component InfinispanXAResourceProducer");
		}
		
		// ensures the injected components participates in the cache's lifecycle
	}

	@Override
	public TransactionManager getTransactionManager() throws Exception {
		return transactionManager != null ? transactionManager : TransactionManagerServices.getTransactionManager();
	}
}

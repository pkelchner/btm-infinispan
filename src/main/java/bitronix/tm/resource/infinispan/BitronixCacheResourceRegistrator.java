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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.infinispan.AbstractDelegatingCache;
import org.infinispan.Cache;
import org.infinispan.CacheImpl;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachemanagerlistener.annotation.CacheStarted;
import org.infinispan.notifications.cachemanagerlistener.annotation.CacheStopped;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStartedEvent;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStoppedEvent;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.lookup.TransactionManagerLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.BitronixTransactionManager;

/**
 * An Infinispan-{@linkplain org.infinispan.manager.EmbeddedCacheManager#addListener(Object) Listener}
 * that automatically registers a {@code XAResourceProducer} with Bitronix for started {@code Cache}s. 
 * <p>
 * To be registered a cache must {@linkplain BitronixTransactionManagerLookup use Bitronix} as its {@code TransactionManager}
 * and it must not {@linkplain org.infinispan.configuration.cache.TransactionConfigurationBuilder#useSynchronization(boolean) use synchronization}.
 * Otherwise it won't participate in transactions as a full {@code XAResource}.
 * How a cache handles recovery is determined by its {@linkplain org.infinispan.configuration.cache.RecoveryConfigurationBuilder recovery configuration}.
 * <p>
 * A typical configuration looks like this:
 *<pre>EmbeddedCacheManager cacheManager = new DefaultCacheManager();
 *cacheManager.addListener(new BitronixCacheResourceRegistrator());
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
 * @see BitronixTransactionManagerLookup
 * @see org.infinispan.transaction.xa.recovery.RecoveryManager RecoveryManager
 */
@Listener
public class BitronixCacheResourceRegistrator {
	private final Logger logger = LoggerFactory.getLogger(BitronixCacheResourceRegistrator.class);
	
	private final ConcurrentMap<String, InfinispanXAResourceProducer> producers =
			new ConcurrentHashMap<String, InfinispanXAResourceProducer>();
	
	@CacheStarted
	public void onCacheStarted(final CacheStartedEvent event) {
		final CacheImpl<?, ?> cache = getCacheImpl(event);
		
		if (shouldRegisterCacheWithBitronix(cache)) {
			final InfinispanXAResourceProducer xaResourceProducer = new InfinispanXAResourceProducer(cache);

			producers.put(event.getCacheName(), xaResourceProducer);
			xaResourceProducer.init();

			logger.debug("Registered cache [{}] with Bitronix", cache);
		}
	}
	
	private CacheImpl<?, ?> getCacheImpl(CacheStartedEvent event) {
		final boolean wasInterruptedBefore = Thread.interrupted();

		Cache<?, ?> cache;
		
		try {
			// avoids a deadlock because getCache() otherwise blocks until after the CacheStartedEvent was delivered
			Thread.currentThread().interrupt();
			
			cache = event.getCacheManager().getCache(event.getCacheName());
		} finally {
			if (!wasInterruptedBefore) {
				Thread.interrupted(); // clear interrupted flag
			}
		}
		
		while (cache instanceof AbstractDelegatingCache<?, ?>) {
			cache = ((AbstractDelegatingCache<?, ?>)cache).getDelegate();
		}
		
		return (CacheImpl<?, ?>) cache;
	}

	private boolean shouldRegisterCacheWithBitronix(CacheImpl<?, ?> cache) {
		final Configuration config = cache.getCacheConfiguration();
		
		if (config.transaction().transactionMode() != TransactionMode.TRANSACTIONAL) {
			logger.debug("Will not register cache [{}] with Bitronix. The cache is not transactional.", cache);
			return false;
		}
		
		if (config.transaction().useSynchronization()) {
			logger.debug("Will not register cache [{}] with Bitronix. The cache merely synchronizes with the transaction.", cache);
			return false;
		}
			
		final TransactionManagerLookup lookup = config.transaction().transactionManagerLookup();

		if (!(lookup instanceof BitronixTransactionManagerLookup)) {
			final Object transactionManager;
			
			try {
				transactionManager = lookup.getTransactionManager();
			} catch (Exception e) {
				throw new IllegalStateException(String.format(
						"Failed to determine if Bitronix is the TransactionManager used by cache [%s]", cache), e);
			}
			
			if (!(transactionManager instanceof BitronixTransactionManager)) {
				logger.warn("Will not register cache [{}] with Bitronix. Bitronix is not the TransactionManager used by the cache.", cache);
				return false;
			}					
		}
		
		return true;
	}
	
	@CacheStopped
	public void onCacheStopped(final CacheStoppedEvent event) {
		InfinispanXAResourceProducer xaResourceProducer = producers.remove(event.getCacheName());
		
		if (xaResourceProducer != null) {
			xaResourceProducer.close();
		}
	}		
}
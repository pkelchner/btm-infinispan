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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.TransactionMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.infinispan.BitronixCacheResourceRegistrator;
import bitronix.tm.resource.infinispan.BitronixTransactionManagerLookup;

public class BitronixResourceCacheTest {
	private BitronixTransactionManager tm;
	private DefaultCacheManager cm;
	private Cache<String, String> cache;

	@Before
	public void setup() throws Exception {
		bitronix.tm.Configuration btmConfig = TransactionManagerServices.getConfiguration();
		
		btmConfig.setDefaultTransactionTimeout(999);
		btmConfig.setServerId("UnitTest");
		btmConfig.setJournal("null");
		btmConfig.setDisableJmx(true);
		
		tm = TransactionManagerServices.getTransactionManager();

		cm = new DefaultCacheManager();
		cm.addListener(new BitronixCacheResourceRegistrator());

		Configuration conf = new ConfigurationBuilder()
				.read(cm.getDefaultCacheConfiguration())
				.transaction()
					.transactionManagerLookup(new BitronixTransactionManagerLookup())
					.transactionMode(TransactionMode.TRANSACTIONAL)
					.useSynchronization(false)
					.recovery().enable()
				.build();
		
		cm.defineConfiguration("default", conf);
		cache = cm.getCache("default");
	}
	
	@After
	public void tearDown() {
		cm.stop();
		tm.shutdown();
	}
	
	@Test
	public void cacheContainsKeyAfterCommit() throws Exception {
		tm.begin();
		cache.put("test", "test");
		tm.commit();

		assertThat(cache.get("test"), is("test"));
	}

	@Test
	public void cacheDoesNotContainKeyAfterRollback() throws Exception {
		tm.begin();
		cache.put("test", "test");
		tm.rollback();

		assertThat(cache.get("test"), is(nullValue()));
	}
}

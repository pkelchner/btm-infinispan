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

import java.util.Date;
import java.util.List;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.infinispan.CacheImpl;

import bitronix.tm.internal.BitronixRuntimeException;
import bitronix.tm.internal.XAResourceHolderState;
import bitronix.tm.recovery.RecoveryException;
import bitronix.tm.resource.ResourceObjectFactory;
import bitronix.tm.resource.ResourceRegistrar;
import bitronix.tm.resource.common.AbstractXAResourceHolder;
import bitronix.tm.resource.common.RecoveryXAResourceHolder;
import bitronix.tm.resource.common.ResourceBean;
import bitronix.tm.resource.common.XAResourceHolder;
import bitronix.tm.resource.common.XAResourceProducer;
import bitronix.tm.resource.common.XAStatefulHolder;

/**
 * Provides Bitronix with {@code XAResourceHolder}s for a single Infinispan-{@code Cache}.
 *
 * @see BitronixCacheResourceRegistrator
 * @see BitronixTransactionManagerLookup
 */
/* package-private */ class InfinispanXAResourceProducer extends ResourceBean implements XAResourceProducer {
	private static final long serialVersionUID = 1L;
	
	private XAResource cacheXAResource;

	private RecoveryXAResourceHolder recoveryXAResourceHolder;

	/**
	 * @param cache the cache for which this XAResourceProducer is created
	 * 
	 * @throws IllegalArgumentException if the cache is not configured to be 
	 * 		{@linkplain org.infinispan.configuration.cache.TransactionConfigurationBuilder#useSynchronization(boolean) XA-capable}.
	 */
	public InfinispanXAResourceProducer(CacheImpl<?, ?> cache) {
		setUniqueName("infinispan-cache-"+cache.getName());
		
		try {
			cacheXAResource = cache.getXAResource();
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("The cache "+cache+" is not XA-capable. "
					+ "Configure the cache with 'transaction().useSynchronization(false)'.");
		}
	}
	
	@Override
	public XAResourceHolder findXAResourceHolder(final XAResource xaResource) {
		try {
			// Infinispan creates one XAResource adapter per transaction.
			// Luckily isSameRM() only returns true if the resource is for the same cache.
			return cacheXAResource.isSameRM(xaResource) ? newResourceHolder(xaResource) : null;
			
		} catch (XAException e) {
			// TransactionXaAdapter.isSameRM() has no code that can fail with a XAException
			throw new BitronixRuntimeException("AssertionFailure: unexpected exception", e);
		}
	}
	
	@Override
	public synchronized XAResourceHolderState startRecovery() throws RecoveryException {
		if (recoveryXAResourceHolder != null) {
			throw new RecoveryException("recovery already in progress on " + this);
		}

		// The cache knows best how recovery is configured so use its xaResource.
		// see configuration option transaction().recovery()
		recoveryXAResourceHolder = new RecoveryXAResourceHolder(newResourceHolder(cacheXAResource));
		return new XAResourceHolderState(recoveryXAResourceHolder, this);
	}

	@Override
	public void endRecovery() throws RecoveryException {
		recoveryXAResourceHolder = null;
	}

	@Override
	public void init() {
		try {
			ResourceRegistrar.register(this);
		} catch (RecoveryException e) {
			throw new BitronixRuntimeException("error recovering " + this, e);
		}
	}

	@Override
	public void close() {
		ResourceRegistrar.unregister(this);
	}

	@Override
	public void setFailed(boolean failed) {
		// Dunno? EhCacheXAResourceProvider does nothing here, 
		// claiming to be unfailable because it is "not connection oriented"...
	}

	@Override
	public XAStatefulHolder createPooledConnection(Object xaFactory, ResourceBean bean) throws Exception {
		throw notConnectionOriented();
	}

	@Override
	public Reference getReference() throws NamingException {
		return new Reference(
				InfinispanXAResourceProducer.class.getName(),
				new StringRefAddr("uniqueName", getUniqueName()),
				ResourceObjectFactory.class.getName(), null);
	}

	private final XAResourceHolder newResourceHolder(final XAResource xaResource) {
		return new AbstractXAResourceHolder() {

			@Override
			public XAResource getXAResource() {
				return xaResource;
			}

			@Override
			public ResourceBean getResourceBean() {
				return InfinispanXAResourceProducer.this;
			}

			@Override
			public List<XAResourceHolder> getXAResourceHolders() {
				throw notConnectionOriented();
			}

			@Override
			public Date getLastReleaseDate() {
				throw notConnectionOriented();
			}

			@Override
			public Object getConnectionHandle() throws Exception {
				throw notConnectionOriented();
			}

			@Override
			public void close() throws Exception {
				throw notConnectionOriented();
			}
		};
	}
	
	private static UnsupportedOperationException notConnectionOriented() {
		throw new UnsupportedOperationException("Infinispan is not connection-oriented");
	}
}

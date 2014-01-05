package bitronix.tm.resource.infinispan;

import org.infinispan.factories.AbstractNamedCacheComponentFactory;
import org.infinispan.factories.AutoInstantiableFactory;
import org.infinispan.factories.annotations.DefaultFactoryFor;

/**
 * Creates {@link InfinispanXAResourceProducer}s for Infinispan.
 */
@DefaultFactoryFor(classes=InfinispanXAResourceProducer.class)
public class InfinispanXAResourceProducerFactory extends AbstractNamedCacheComponentFactory implements AutoInstantiableFactory {
	@Override
	public <T> T construct(Class<T> componentType) {
		if (!componentType.isAssignableFrom(InfinispanXAResourceProducer.class)) {
			throw new IllegalStateException("Don't know how to handle type " + componentType);
		}
		
		return componentType.cast(new InfinispanXAResourceProducer());
	}
}
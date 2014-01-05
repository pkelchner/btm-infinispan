package bitronix.tm.resource.infinispan;

import org.infinispan.factories.components.ModuleMetadataFileFinder;

/**
 * Provides the name of the Infinispan annotation-metadata file.
 */
public class BitronixResourceMetadataLoader implements ModuleMetadataFileFinder {
	@Override
	public String getMetadataFilename() {
		return "btm-infinispan-component-metadata.dat";
	}
}

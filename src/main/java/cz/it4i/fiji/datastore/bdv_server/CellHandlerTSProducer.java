/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2021 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/

package cz.it4i.fiji.datastore.bdv_server;

import static cz.it4i.fiji.datastore.core.Version.stringToIntVersion;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import cz.it4i.fiji.datastore.ApplicationConfiguration;
import cz.it4i.fiji.datastore.core.Version;
import cz.it4i.fiji.datastore.register_service.DatasetRepository;
import cz.it4i.fiji.datastore.register_service.WriteToVersionListener;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
class CellHandlerTSProducer implements WriteToVersionListener {

	@Inject
	DatasetRepository repository;

	@Inject
	ApplicationConfiguration configuration;

	final private Map<String, CellHandlerTS> cellHandlersTS = new HashMap<>();

	synchronized Uni<CellHandlerTS> produce(URI baseURI, String uuidStr,
		final String versionStr)
	{
		final int version = stringToIntVersion(versionStr);
		String key = getKey(uuidStr, versionStr);
		String baseURL = baseURI.resolve("bdv/").resolve(uuidStr + "/").resolve(
				versionStr).toString();
		return create(baseURL, uuidStr,
				version).onItem().transform(ch -> {
			return cellHandlersTS.computeIfAbsent(key, x -> ch);
		});
	}

	@Override
	synchronized public void writingToVersion(String uuidStr, int version) {
		cellHandlersTS.remove(getKey(uuidStr, "" + version));
		clearCacheForMixedLatest(uuidStr);
	}

	@Override
	synchronized public void writeToAllVersions(String uuid) {
		String keyPrefix = getKey(uuid, "");
		for (Iterator<Entry<String, CellHandlerTS>> iter = cellHandlersTS.entrySet()
			.iterator(); iter.hasNext();)
		{
			Entry<String, CellHandlerTS> entry = iter.next();
			if (entry.getKey().startsWith(keyPrefix)) {
				iter.remove();
			}
		}
		clearCacheForMixedLatest(uuid);
	}

	private String getKey(String uuid, String version) {
		return uuid + ":" + version;
	}

	private void clearCacheForMixedLatest(String uuidStr) {
		cellHandlersTS.remove(getKey(uuidStr, Version.MIXED_LATEST_VERSION_NAME));
	}

	private Uni<CellHandlerTS> create(String baseURL, String uuid, int version) {

		// only for check that version exists
		repository.findByUUIDVersion(uuid, version);

		return repository.findByUUID(uuid).onItem().transform(ds -> {
			try {
				return new CellHandlerTS(configuration.getDatasetHandler(uuid), () -> ds,
						baseURL, version, uuid + "_version-" + version, GetThumbnailsDirectoryTS
						.$());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

	}

}

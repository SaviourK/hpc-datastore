/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2022 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;


public class CachingAttributesN5Writer extends N5WriterDecorator {

	private final Set<String> cachedAttributes;
	private final String uuid;

	private final Map<String, Map<String, Object>> asAttributePerValuePerPath =
		new HashMap<>();

	private final Map<String, DatasetAttributes> asDatasetAttributesPerPath =
		new HashMap<>();

	public CachingAttributesN5Writer(N5Writer writer, String uuid,
		String... cachedAttributes)
	{
		super(writer);
		this.cachedAttributes = Stream.of(cachedAttributes).collect(Collectors
			.toSet());
		this.uuid = uuid;
	}


	@Override
	public <T> T getAttribute(String pathName, String key, Class<T> clazz)
		throws IOException
	{
		if (!cachedAttributes.contains(key)) {
			return super.getAttribute(pathName, key, clazz);
		}
		Map<String, Object> asValuePerAttribute;
		synchronized (asAttributePerValuePerPath) {
			asValuePerAttribute = asAttributePerValuePerPath
				.computeIfAbsent(pathName, $ -> new HashMap<>());
		}
		synchronized (asValuePerAttribute) {
			@SuppressWarnings("unchecked")
			T result = (T) asValuePerAttribute.get(key);
			if (result == null) {
				result = super.getAttribute(pathName, key, clazz);
				asValuePerAttribute.put(key, result);
			}
			return result;
		}

	}

	@Override
	public <T> void setAttribute(String pathName, String key, T attribute)
		throws IOException
	{

		super.setAttribute(pathName, key, attribute);
		if (cachedAttributes.contains(key)) {
			Map<String, Object> asValuePerAttribute;
			synchronized (asAttributePerValuePerPath) {
				asValuePerAttribute = asAttributePerValuePerPath.get(pathName);
			}
			if (asValuePerAttribute == null) {
				return;
			}
			synchronized (asValuePerAttribute) {
				asValuePerAttribute.put(key, attribute);
			}
		}
	}

	@Override
	public DatasetAttributes getDatasetAttributes(String pathName)
		throws IOException
	{
		DatasetAttributes result;
		synchronized (asDatasetAttributesPerPath) {
			result = asDatasetAttributesPerPath.get(pathName);
			if (result == null) {
				result = super.getDatasetAttributes(pathName);
				asDatasetAttributesPerPath.put(pathName, result);
			}
		}
		return result;
	}

	@Override
	public void setDatasetAttributes(String pathName,
		DatasetAttributes datasetAttributes) throws IOException
	{
		synchronized (asDatasetAttributesPerPath) {
			asDatasetAttributesPerPath.put(pathName, datasetAttributes);
		}
		synchronized (asAttributePerValuePerPath) {
			asAttributePerValuePerPath.remove(pathName);
		}
		super.setDatasetAttributes(pathName, datasetAttributes);
	}

	@Override
	public boolean deleteBlock(String pathName, long[] gridPosition) throws IOException {
		BlockCacheService.invalidateFromCache(uuid, gridPosition, pathName);
		return super.deleteBlock(pathName, gridPosition);
	}

	@Override
	public DataBlock<?> readBlock(String pathName, DatasetAttributes datasetAttributes, long[] gridPosition) throws IOException {
		String cacheKey = BlockCacheService.generateCacheKey(uuid, gridPosition, pathName);
		DataBlock<?> result = BlockCacheService.getIfPresent(cacheKey);
		if (result != null) {
			return result;
		}
		return readAndAddToCache(pathName, datasetAttributes, gridPosition, cacheKey);
	}

	private DataBlock<?> readAndAddToCache(String pathName, DatasetAttributes datasetAttributes, long[] gridPosition, String cacheKey) throws IOException {
		final DataBlock<?> dataBlock = super.readBlock(pathName, datasetAttributes, gridPosition);
		BlockCacheService.put(cacheKey, dataBlock);
		return dataBlock;
	}

}

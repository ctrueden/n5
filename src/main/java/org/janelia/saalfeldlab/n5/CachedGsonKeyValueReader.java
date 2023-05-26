/**
 * Copyright (c) 2017--2021, Stephan Saalfeld
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.n5;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.janelia.saalfeldlab.n5.cache.N5JsonCache;
import org.janelia.saalfeldlab.n5.cache.N5JsonCacheableContainer;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * {@link N5Reader} implementation through {@link KeyValueAccess} with JSON
 * attributes parsed with {@link Gson}.
 *
 * @author Stephan Saalfeld
 * @author Igor Pisarev
 * @author Philipp Hanslovsky
 */

public interface CachedGsonKeyValueReader extends GsonKeyValueReader, N5JsonCacheableContainer {

	default N5JsonCache newCache() {
		return new N5JsonCache( this );
	}

	boolean cacheMeta();

	N5JsonCache getCache();

	default JsonElement getAttributesFromContainer(final String normalPathName, final String normalCacheKey) {

		// this implementation doesn't use cache key, but rather depends on
		// attributesPath being implemented
		return GsonKeyValueReader.super.getAttributes(normalPathName);
	}

	@Override
	default DatasetAttributes getDatasetAttributes(final String pathName) {

		final String normalPath = N5URL.normalizeGroupPath(pathName);
		final JsonElement attributes;

		if (!datasetExists(pathName))
			return null;

		if (cacheMeta()) {
			attributes = getCache().getAttributes(normalPath, N5KeyValueReader.ATTRIBUTES_JSON);
		} else {
			attributes = GsonKeyValueReader.super.getAttributes(normalPath);
		}

		return createDatasetAttributes(attributes);
	}

	default DatasetAttributes normalGetDatasetAttributes(final String pathName) throws N5Exception.N5IOException {

		final String normalPath = N5URL.normalizeGroupPath(pathName);
		final JsonElement attributes = GsonKeyValueReader.super.getAttributes(normalPath);
		return createDatasetAttributes(attributes);
	}

	@Override
	default <T> T getAttribute(
			final String pathName,
			final String key,
			final Class<T> clazz) throws IOException {

		final String normalPathName = N5URL.normalizeGroupPath(pathName);
		final String normalizedAttributePath = N5URL.normalizeAttributePath(key);

		final JsonElement attributes;
		if (cacheMeta()) {
			attributes = getCache().getAttributes(normalPathName, N5KeyValueReader.ATTRIBUTES_JSON);
		} else {
			attributes = GsonKeyValueReader.super.getAttributes(normalPathName);
		}
		return GsonUtils.readAttribute(attributes, normalizedAttributePath, clazz, getGson());
	}

	@Override
	default <T> T getAttribute(
			final String pathName,
			final String key,
			final Type type) throws IOException {

		final String normalPathName = N5URL.normalizeGroupPath(pathName);
		final String normalizedAttributePath = N5URL.normalizeAttributePath(key);
		JsonElement attributes;
		if (cacheMeta()) {
			attributes = getCache().getAttributes(normalPathName, N5KeyValueReader.ATTRIBUTES_JSON);
		} else {
			attributes = GsonKeyValueReader.super.getAttributes(normalPathName);
		}
		return GsonUtils.readAttribute(attributes, normalizedAttributePath, type, getGson());
	}

	@Override
	default boolean exists(final String pathName) {

		final String normalPathName = N5URL.normalizeGroupPath(pathName);
		if (cacheMeta())
			return getCache().isGroup(normalPathName, N5KeyValueReader.ATTRIBUTES_JSON);
		else {
			return existsFromContainer(normalPathName, null);
		}
	}

	default boolean existsFromContainer(final String normalPathName, final String normalCacheKey) {

		if( normalCacheKey == null )
			return getKeyValueAccess().isDirectory(groupPath(normalPathName));
		else
			return getKeyValueAccess().isFile(getKeyValueAccess().compose(groupPath(normalPathName), normalCacheKey));
	}

	default boolean groupExists(final String pathName) {

		final String normalPathName = N5URL.normalizeGroupPath(pathName);
		if (cacheMeta())
			return getCache().isGroup(normalPathName, null);
		else {
			return isGroupFromContainer(normalPathName);
		}
	}

	default boolean isGroupFromContainer(final String pathName) {

		final String normalPathName = N5URL.normalizeGroupPath(pathName);
		return GsonKeyValueReader.super.groupExists(groupPath(normalPathName));
	}

	default boolean isGroupFromAttributes(final String normalCacheKey, final JsonElement attributes) {
		return true;
	}

	@Override
	default boolean datasetExists(final String pathName) throws N5Exception.N5IOException {

		if (cacheMeta()) {
			final String normalPathName = N5URL.normalizeGroupPath(pathName);
			return getCache().isDataset(normalPathName, null);
		}
		return isDatasetFromContainer(pathName);
	}

	default boolean isDatasetFromContainer(final String pathName) throws N5Exception.N5IOException {

		return normalGetDatasetAttributes(pathName) != null;
	}

	default boolean isDatasetFromAttributes(final String normalCacheKey, final JsonElement attributes) {

		return isGroupFromAttributes(normalCacheKey, attributes) && createDatasetAttributes(attributes) != null;
	}

	/**
	 * Reads or creates the attributes map of a group or dataset.
	 *
	 * @param pathName group path
	 * @return
	 * @throws IOException
	 */
	default JsonElement getAttributes(final String pathName) throws N5Exception.N5IOException {

		final String groupPath = N5URL.normalizeGroupPath(pathName);

		/* If cached, return the cache*/
		if (cacheMeta()) {
			return getCache().getAttributes(groupPath, N5KeyValueReader.ATTRIBUTES_JSON);
		} else {
			return GsonKeyValueReader.super.getAttributes(groupPath);
		}

	}

	@Override
	default String[] list(final String pathName) throws N5Exception.N5IOException {

		final String normalPath = N5URL.normalizeGroupPath(pathName);
		if (cacheMeta()) {
			return getCache().list(normalPath);
		} else {
			return GsonKeyValueReader.super.list(normalPath);
		}
	}

	default String[] listFromContainer(final String normalPathName) {

		// this implementation doesn't use cache key, but rather depends on
		return GsonKeyValueReader.super.list(normalPathName);
	}

	/**
	 * Constructs the path for a data block in a dataset at a given grid position.
	 * <p>
	 * The returned path is
	 * <pre>
	 * $basePath/datasetPathName/$gridPosition[0]/$gridPosition[1]/.../$gridPosition[n]
	 * </pre>
	 * <p>
	 * This is the file into which the data block will be stored.
	 *
	 * @param normalPath   normalized dataset path
	 * @param gridPosition
	 * @return
	 */
	default String getDataBlockPath(
			final String normalPath,
			final long... gridPosition) {

		final String[] components = new String[gridPosition.length + 2];
		components[0] = getURI().getPath();
		components[1] = normalPath;
		int i = 1;
		for (final long p : gridPosition)
			components[++i] = Long.toString(p);

		return getKeyValueAccess().compose(components);
	}

	/**
	 * Check for attributes that are required for a group to be a dataset.
	 *
	 * @param attributes to check for dataset attributes
	 * @return if {@link DatasetAttributes#DIMENSIONS_KEY} and {@link DatasetAttributes#DATA_TYPE_KEY} are present
	 */
	static boolean hasDatasetAttributes(final JsonElement attributes) {

		if (attributes == null || !attributes.isJsonObject()) {
			return false;
		}

		final JsonObject metadataCache = attributes.getAsJsonObject();
		return metadataCache.has(DatasetAttributes.DIMENSIONS_KEY) && metadataCache.has(DatasetAttributes.DATA_TYPE_KEY);
	}
}

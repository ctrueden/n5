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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;

/**
 * {@link N5Reader} implementation through {@link KeyValueAccess} with JSON
 * attributes parsed with {@link Gson}.
 *
 * @author Stephan Saalfeld
 * @author Igor Pisarev
 * @author Philipp Hanslovsky
 */
public interface GsonKeyValueReader extends N5Reader {

	Gson getGson();

	KeyValueAccess getKeyValueAccess();

	@Override
	default Map<String, Class<?>> listAttributes(final String pathName) throws IOException {

		return GsonUtils.listAttributes(getAttributes(pathName));
	}

	@Override
	default DatasetAttributes getDatasetAttributes(final String pathName) throws N5Exception.N5IOException {

		final String normalPath = N5URL.normalizeGroupPath(pathName);
		final JsonElement attributes = getAttributes(normalPath);
		return createDatasetAttributes(attributes);
	}

	default DatasetAttributes createDatasetAttributes(JsonElement attributes) {

		final long[] dimensions = GsonUtils.readAttribute(attributes, DatasetAttributes.DIMENSIONS_KEY, long[].class, getGson());
		if (dimensions == null) {
			return null;
		}

		final DataType dataType = GsonUtils.readAttribute(attributes, DatasetAttributes.DATA_TYPE_KEY, DataType.class, getGson());
		if (dataType == null) {
			return null;
		}

		final int[] blockSize = GsonUtils.readAttribute(attributes, DatasetAttributes.BLOCK_SIZE_KEY, int[].class, getGson());

		final Compression compression = GsonUtils.readAttribute(attributes, DatasetAttributes.COMPRESSION_KEY, Compression.class, getGson());

		/* version 0 */
		final String compressionVersion0Name = compression
				== null
				? GsonUtils.readAttribute(attributes, DatasetAttributes.compressionTypeKey, String.class, getGson())
				: null;

		return createDatasetAttributes(dimensions, dataType, blockSize, compression, compressionVersion0Name);
	}

	@Override
	default <T> T getAttribute(final String pathName, final String key, final Class<T> clazz) throws IOException {

		final String normalPathName = N5URL.normalizeGroupPath(pathName);
		final String normalizedAttributePath = N5URL.normalizeAttributePath(key);

		final JsonElement attributes = getAttributes(normalPathName);
		return GsonUtils.readAttribute(attributes, normalizedAttributePath, clazz, getGson());
	}

	@Override
	default <T> T getAttribute(final String pathName, final String key, final Type type) throws IOException {

		final String normalPathName = N5URL.normalizeGroupPath(pathName);
		final String normalizedAttributePath = N5URL.normalizeAttributePath(key);
		JsonElement attributes = getAttributes(normalPathName);
		return GsonUtils.readAttribute(attributes, normalizedAttributePath, type, getGson());
	}

	default boolean groupExists(final String absoluteNormalPath) {

		return getKeyValueAccess().isDirectory(absoluteNormalPath);
	}

	@Override
	default boolean exists(final String pathName) {

		final String normalPath = N5URL.normalizeGroupPath(pathName);
		return groupExists(normalPath) || datasetExists(normalPath);
	}

	@Override
	default boolean datasetExists(final String pathName) throws N5Exception.N5IOException {

		return getDatasetAttributes(pathName) != null;
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
		final String attributesPath = attributesPath(groupPath);

		if (!getKeyValueAccess().isFile(attributesPath))
			return null;

		try (final LockedChannel lockedChannel = getKeyValueAccess().lockForReading(attributesPath)) {
			return GsonUtils.readAttributes(lockedChannel.newReader(), getGson());
		} catch (IOException e) {
			throw new N5Exception.N5IOException("Cannot open lock for Reading", e);
		}

	}

	@Override
	default DataBlock<?> readBlock( final String pathName, final DatasetAttributes datasetAttributes, final long... gridPosition) throws IOException {

		final String path = getDataBlockPath(N5URL.normalizeGroupPath(pathName), gridPosition);
		if (!getKeyValueAccess().isFile(path))
			return null;

		try (final LockedChannel lockedChannel = getKeyValueAccess().lockForReading(path)) {
			return DefaultBlockReader.readBlock(lockedChannel.newInputStream(), datasetAttributes, gridPosition);
		}
	}

	@Override
	default String[] list(final String pathName) throws N5Exception.N5IOException {

		try {
			return getKeyValueAccess().listDirectories(groupPath(pathName));
		} catch (IOException e) {
			throw new N5Exception.N5IOException("Cannot list directories for group " + pathName, e);
		}
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
	 * Constructs the absolute path (in terms of this store) for the group or
	 * dataset.
	 *
	 * @param normalGroupPath normalized group path without leading slash
	 * @return the absolute path to the group
	 */
	default String groupPath(final String normalGroupPath) {

		return getKeyValueAccess().compose(getURI().getPath(), normalGroupPath);
	}

	/**
	 * Constructs the absolute path (in terms of this store) for the attributes
	 * file of a group or dataset.
	 *
	 * @param normalPath normalized group path without leading slash
	 * @return the absolute path to the attributes
	 */
	default String attributesPath(final String normalPath) {

		return getKeyValueAccess().compose(getURI().getPath(), normalPath, N5KeyValueReader.ATTRIBUTES_JSON);
	}

	static DatasetAttributes createDatasetAttributes(
			final long[] dimensions,
			final DataType dataType,
			int[] blockSize,
			Compression compression,
			final String compressionVersion0Name
	) {

		if (blockSize == null)
			blockSize = Arrays.stream(dimensions).mapToInt(a -> (int)a).toArray();

		/* version 0 */
		if (compression == null) {
			switch (compressionVersion0Name) {
			case "raw":
				compression = new RawCompression();
				break;
			case "gzip":
				compression = new GzipCompression();
				break;
			case "bzip2":
				compression = new Bzip2Compression();
				break;
			case "lz4":
				compression = new Lz4Compression();
				break;
			case "xz":
				compression = new XzCompression();
				break;
			}
		}

		return new DatasetAttributes(dimensions, blockSize, dataType, compression);
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

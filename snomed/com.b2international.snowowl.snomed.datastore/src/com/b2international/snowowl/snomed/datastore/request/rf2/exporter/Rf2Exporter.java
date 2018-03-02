/*
 * Copyright 2018 B2i Healthcare Pte Ltd, http://b2i.sg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b2international.snowowl.snomed.datastore.request.rf2.exporter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.PageableCollectionResource;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.datastore.request.BranchRequest;
import com.b2international.snowowl.snomed.core.domain.Rf2MaintainerType;
import com.b2international.snowowl.snomed.core.domain.Rf2ReleaseType;
import com.b2international.snowowl.snomed.core.domain.SnomedComponent;
import com.b2international.snowowl.snomed.datastore.request.SnomedSearchRequestBuilder;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * @since 6.3
 */
public abstract class Rf2Exporter<B extends SnomedSearchRequestBuilder<B, R>, R extends PageableCollectionResource<C>, C extends SnomedComponent> {

	private static final Joiner TAB_JOINER = Joiner.on('\t');
	
	private static final ByteBuffer CR_LF = toByteBuffer("\r\n");

	private static final int BATCH_SIZE = 1000;
	
	/** Special value that can be returned from the mapping function to indicate that the row should not be exported. */
	protected static final List<String> SKIP_ROW = ImmutableList.of();

	// Parameters used for file name calculations
	protected final Rf2ReleaseType releaseType;
	protected final Rf2MaintainerType maintainerType;
	protected final String nrcCountryCode;
	protected final String namespace;
	protected final String latestEffectiveTime;
	protected final String transientEffectiveTime;
	protected final boolean includePreReleaseContent;

	private final Collection<String> modules;

	public Rf2Exporter(final Rf2ReleaseType releaseType, 
			final Rf2MaintainerType maintainerType, 
			final String nrcCountryCode,
			final String namespace, 
			final String latestEffectiveTime, 
			final String transientEffectiveTime, 
			final boolean includePreReleaseContent,
			final Collection<String> modules) {

		this.releaseType = releaseType;
		this.maintainerType = maintainerType;
		this.nrcCountryCode = nrcCountryCode;
		this.namespace = namespace;
		this.latestEffectiveTime = latestEffectiveTime;
		this.transientEffectiveTime = transientEffectiveTime;
		this.includePreReleaseContent = includePreReleaseContent;
		this.modules = modules;
	}

	protected abstract Path getRelativeDirectory();

	protected abstract Path getFileName();

	protected abstract String[] getHeader();

	protected abstract SnomedSearchRequestBuilder<B, R> createSearchRequestBuilder();

	protected abstract Function<C, List<String>> getMapFunction();

	protected final String getCountryNamespace() {
		switch (maintainerType) {
			case NRC:
				return nrcCountryCode + Strings.nullToEmpty(namespace);
			case OTHER_EXTENSION_PROVIDER:
				return Strings.nullToEmpty(namespace);
			case SNOMED_INTERNATIONAL:
				return "INT" + Strings.nullToEmpty(namespace);
			default:
				throw new IllegalStateException("Unexpected RF2 maintainer type '" + maintainerType + "'.");
		}
	}

	protected final String getEffectiveTime(final SnomedComponent component) {
		if (component.getEffectiveTime() == null) {
			return transientEffectiveTime;
		} else {
			return EffectiveTimes.format(component.getEffectiveTime(), DateFormats.SHORT); 
		}
	}

	protected final String getActive(final SnomedComponent component) {
		return component.isActive() ? "1" : "0";
	}

	public final void exportBranch(final Path releaseDirectory, final RepositoryContext context, final String branch, final long effectiveTime) throws IOException {
		// Ensure that the path leading to the export file exists
		final Path exportFileDirectory = releaseDirectory.resolve(getRelativeDirectory());
		Files.createDirectories(exportFileDirectory);

		final Path exportFile = exportFileDirectory.resolve(getFileName());
		try (RandomAccessFile randomAccessFile = new RandomAccessFile(exportFile.toFile(), "rw")) {
			try (FileChannel fileChannel = randomAccessFile.getChannel()) {

				// Add a header if the file is empty
				if (randomAccessFile.length() == 0L) {
					fileChannel.write(toByteBuffer(TAB_JOINER.join(getHeader())));
					fileChannel.write(CR_LF);
				}

				// We want to append rows, if the file already exists, so jump to the end
				fileChannel.position(fileChannel.size());

				String scrollId = null;
				R results = null;

				while (results == null || !results.isEmpty()) {

					/* 
					 * XXX: createSearchRequestBuilder() should handle namespace/language code filtering, if applicable;
					 * we will only handle the effective time and module filters here.
					 */
					final SnomedSearchRequestBuilder<B, R> requestBuilder = createSearchRequestBuilder()
							.setLimit(BATCH_SIZE)
							.filterByModules(modules)
							.setScrollId(scrollId);
					
					
					if (effectiveTime == EffectiveTimes.UNSET_EFFECTIVE_TIME) {
						// If we are in the final "layer", export only components if the effective time is not set
						requestBuilder.filterByEffectiveTime(effectiveTime);
					} else {
						// Version branches might include updated content; we will export them at this point
						requestBuilder.filterByEffectiveTime(effectiveTime, Long.MAX_VALUE);
					}

					final BranchRequest<R> branchRequest = new BranchRequest<R>(branch, requestBuilder.build());
					results = branchRequest.execute(context);

					results.stream()
							.map(getMapFunction())
							.forEachOrdered(row -> {
								try {
									if (!SKIP_ROW.equals(row)) {
										fileChannel.write(toByteBuffer(TAB_JOINER.join(row)));
										fileChannel.write(CR_LF);
									}
								} catch (final IOException e) {
									throw new SnowowlRuntimeException("Failed to write contents for file '" + exportFile.getFileName() + "'.");
								}
							});

					scrollId = results.getScrollId();
				}
			}
		}
	}

	private static ByteBuffer toByteBuffer(final String s) {
		return ByteBuffer.wrap(s.getBytes(Charsets.UTF_8));
	}
}

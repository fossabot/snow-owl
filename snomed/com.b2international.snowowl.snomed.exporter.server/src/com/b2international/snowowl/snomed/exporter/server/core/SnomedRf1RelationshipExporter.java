/*
 * Copyright 2011-2015 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.exporter.server.core;

import static com.b2international.commons.StringUtils.valueOfOrEmptyString;
import static com.b2international.snowowl.core.ApplicationContext.getServiceForClass;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import com.b2international.commons.CompareUtils;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.api.index.CommonIndexConstants;
import com.b2international.snowowl.datastore.index.IndexUtils;
import com.b2international.snowowl.datastore.server.index.IndexServerService;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetLookupService;
import com.b2international.snowowl.snomed.datastore.browser.SnomedIndexBrowserConstants;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexService;
import com.b2international.snowowl.snomed.datastore.services.ISnomedComponentService;
import com.b2international.snowowl.snomed.datastore.services.ISnomedComponentService.IdStorageKeyPair;
import com.b2international.snowowl.snomed.exporter.server.ComponentExportType;
import com.b2international.snowowl.snomed.exporter.server.Id2Rf1PropertyMapper;
import com.b2international.snowowl.snomed.exporter.server.SnomedReleaseFileHeaders;
import com.b2international.snowowl.snomed.exporter.server.SnomedRf1Exporter;
import com.b2international.snowowl.snomed.exporter.server.SnomedRfFileNameBuilder;
import com.b2international.snowowl.snomed.exporter.server.sandbox.SnomedExportConfiguration;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Sets;

/**
 * RF1 exporter for relationships.
 *
 */
public class SnomedRf1RelationshipExporter implements SnomedRf1Exporter {

	private final static TermQuery TYPE_QUERY = 
			new TermQuery(new Term(CommonIndexConstants.COMPONENT_TYPE, IndexUtils.intToPrefixCoded(SnomedTerminologyComponentConstants.RELATIONSHIP_NUMBER)));
	
	private static final Set<String> RELATIONSHIP_FILEDS_TO_LOAD = Collections.unmodifiableSet(Sets.newHashSet(
			SnomedIndexBrowserConstants.RELATIONSHIP_OBJECT_ID,
			SnomedIndexBrowserConstants.RELATIONSHIP_ATTRIBUTE_ID,
			SnomedIndexBrowserConstants.RELATIONSHIP_VALUE_ID,
			SnomedIndexBrowserConstants.RELATIONSHIP_CHARACTERISTIC_TYPE_ID,
			SnomedIndexBrowserConstants.RELATIONSHIP_GROUP
			));
	
	private static final Set<String> REFINABILITY_ID_FIELD_TO_LOAD = Collections.unmodifiableSet(Sets.newHashSet(
			SnomedIndexBrowserConstants.REFERENCE_SET_MEMBER_VALUE_ID
			));
	
	private final static TermQuery REFINABILITY_QUERY = 
			new TermQuery(new Term(SnomedIndexBrowserConstants.REFERENCE_SET_MEMBER_REFERENCE_SET_ID, IndexUtils.longToPrefixCoded(Concepts.REFSET_RELATIONSHIP_REFINABILITY)));
	
	private final Id2Rf1PropertyMapper mapper;
	private final SnomedExportConfiguration configuration;
	private final Supplier<Iterator<String>> itrSupplier;
	private boolean refinabilityExists;

	public SnomedRf1RelationshipExporter(final SnomedExportConfiguration configuration, final Id2Rf1PropertyMapper mapper) {
		this.configuration = checkNotNull(configuration, "configuration");
		this.mapper = checkNotNull(mapper, "mapper");
		refinabilityExists = new SnomedRefSetLookupService().exists(this.configuration.getCurrentBranchPath(), Concepts.REFSET_RELATIONSHIP_REFINABILITY);
		itrSupplier = createSupplier();
	}
	
	private Supplier<Iterator<String>> createSupplier() {
		return memoize(new Supplier<Iterator<String>>() {
			public Iterator<String> get() {
				return new AbstractIterator<String>() {
					
					private final Iterator<IdStorageKeyPair> idIterator = getServiceForClass(ISnomedComponentService.class)
							.getAllComponentIdStorageKeys(getBranchPath(), SnomedTerminologyComponentConstants.RELATIONSHIP_NUMBER).iterator();
					
					@SuppressWarnings("rawtypes")
					private final IndexServerService indexService = (IndexServerService) ApplicationContext.getInstance().getService(SnomedIndexService.class);
					private Object[] _values;
					
					@SuppressWarnings("unchecked")
					protected String computeNext() {
						
						while (idIterator.hasNext()) {
							
							final String relationshipId = idIterator.next().getId();
							_values = new Object[7];
							
							ReferenceManager<IndexSearcher> manager = null;
							IndexSearcher searcher = null;
							
							try {
								
								manager = indexService.getManager(getBranchPath());
								searcher = manager.acquire();
								
								
								final BooleanQuery relationshipQuery = new BooleanQuery(true);
								relationshipQuery.add(new TermQuery(new Term(CommonIndexConstants.COMPONENT_ID, IndexUtils.longToPrefixCoded(relationshipId))), Occur.MUST);
								relationshipQuery.add(TYPE_QUERY, Occur.MUST);
								
								final TopDocs conceptTopDocs = indexService.search(getBranchPath(), relationshipQuery, 1);
								
								Preconditions.checkState(null != conceptTopDocs && !CompareUtils.isEmpty(conceptTopDocs.scoreDocs));
								
								final Document doc = searcher.doc(conceptTopDocs.scoreDocs[0].doc, RELATIONSHIP_FILEDS_TO_LOAD);
								
								_values[0] = relationshipId;
								_values[1] = doc.get(SnomedIndexBrowserConstants.RELATIONSHIP_OBJECT_ID);
								_values[2] = doc.get(SnomedIndexBrowserConstants.RELATIONSHIP_ATTRIBUTE_ID);
								_values[3] = doc.get(SnomedIndexBrowserConstants.RELATIONSHIP_VALUE_ID);
								_values[4] = doc.get(SnomedIndexBrowserConstants.RELATIONSHIP_CHARACTERISTIC_TYPE_ID);
								_values[6] = doc.get(SnomedIndexBrowserConstants.RELATIONSHIP_GROUP);
								
								if (refinabilityExists) {
									final BooleanQuery inactivationQuery = new BooleanQuery(true);
									inactivationQuery.add(REFINABILITY_QUERY, Occur.MUST);
									inactivationQuery.add(new TermQuery(new Term(SnomedIndexBrowserConstants.REFERENCE_SET_MEMBER_REFERENCED_COMPONENT_ID, relationshipId)), Occur.MUST);
									final TopDocs inactivationTopDocs = indexService.search(getBranchPath(), inactivationQuery, 1);
									
									if (null != inactivationTopDocs && !CompareUtils.isEmpty(inactivationTopDocs.scoreDocs)) {
										_values[5] = searcher.doc(inactivationTopDocs.scoreDocs[0].doc, REFINABILITY_ID_FIELD_TO_LOAD).get(SnomedIndexBrowserConstants.REFERENCE_SET_MEMBER_VALUE_ID);
									} 
								}
								
								return new StringBuilder(valueOfOrEmptyString(_values[0])) //ID
									.append(HT)
									.append(valueOfOrEmptyString(_values[1])) //source
									.append(HT)
									.append(valueOfOrEmptyString(_values[2])) //type
									.append(HT)
									.append(valueOfOrEmptyString(_values[3])) //destination
									.append(HT)
									.append(mapper.getRelationshipType(valueOfOrEmptyString(_values[4]))) //characteristic type
									.append(HT)
									.append(mapper.getRefinabilityType(valueOfOrEmptyString(_values[5]))) //refinability
									.append(HT)
									.append(valueOfOrEmptyString(_values[6])) //group
									.toString();
								
							} catch (final IOException e) {
								
								throw new SnowowlRuntimeException(e);
								
							} finally {
								
								if (null != manager && null != searcher) {
									
									try {
										
										manager.release(searcher);
										
									} catch (final IOException e) {
										
										throw new SnowowlRuntimeException(e);
										
									}
									
								}
							
							}
							
						}
						return endOfData();
					}
					
					private IBranchPath getBranchPath() {
						return configuration.getCurrentBranchPath();
					}
					
				};
			}
		});
	}
	
	@Override
	public String getRelativeDirectory() {
		return RF1_CORE_RELATIVE_DIRECTORY;
	}

	@Override
	public String getFileName() {
		return SnomedRfFileNameBuilder.buildCoreRf1FileName(getType(), configuration);
	}

	@Override
	public ComponentExportType getType() {
		return ComponentExportType.RELATIONSHIP;
	}

	@Override
	public String[] getColumnHeaders() {
		return SnomedReleaseFileHeaders.RF1_RELATIONSHIP_HEADER;
	}

	@Override
	public boolean hasNext() {
		return itrSupplier.get().hasNext();
	}

	@Override
	public String next() {
		return itrSupplier.get().next();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<String> iterator() {
		return itrSupplier.get();
	}

	@Override
	public void close() throws Exception {
		//intentionally ignored
	}
	
	@Override
	public SnomedExportConfiguration getConfiguration() {
		return configuration;
	}
	
}
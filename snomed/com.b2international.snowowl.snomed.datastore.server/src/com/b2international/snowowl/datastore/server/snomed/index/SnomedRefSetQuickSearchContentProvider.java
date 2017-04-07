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
package com.b2international.snowowl.datastore.server.snomed.index;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.eclipse.emf.ecore.EPackage;

import com.b2international.commons.StringUtils;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.CoreTerminologyBroker;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.index.CommonIndexConstants;
import com.b2international.snowowl.core.quicksearch.CompactQuickSearchElement;
import com.b2international.snowowl.core.quicksearch.QuickSearchContentResult;
import com.b2international.snowowl.core.quicksearch.QuickSearchElement;
import com.b2international.snowowl.datastore.IBranchPathMap;
import com.b2international.snowowl.datastore.index.IndexUtils;
import com.b2international.snowowl.datastore.quicksearch.AbstractQuickSearchContentProvider;
import com.b2international.snowowl.datastore.quicksearch.IQuickSearchContentProvider;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.datastore.browser.SnomedIndexBrowserConstants;
import com.b2international.snowowl.snomed.datastore.browser.SnomedIndexQueries;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexService;
import com.b2international.snowowl.snomed.datastore.index.refset.SnomedRefSetIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.refset.SnomedRefSetIndexQueryAdapter;
import com.b2international.snowowl.snomed.datastore.quicksearch.SnomedRefSetQuickSearchProvider;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSetPackage;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSetType;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Server side, Net4j independent service for contributing SNOMED&nbsp;CT reference sets as the underlying elements for the quick search provider.
 * 
 * 
 * @see IIndexServerService
 * @see SnomedRefSetIndexServerService
 * @see IQuickSearchContentProvider
 */
public class SnomedRefSetQuickSearchContentProvider extends AbstractQuickSearchContentProvider implements IQuickSearchContentProvider {

	private static final Query CONCEPT_TYPE_QUERY = new TermQuery(new Term(SnomedIndexBrowserConstants.COMPONENT_TYPE, IndexUtils.intToPrefixCoded(SnomedTerminologyComponentConstants.CONCEPT_NUMBER)));
	
	private final class SnomedRefSetConverterFunction implements Function<SnomedRefSetIndexEntry, QuickSearchElement> {
		@Override public QuickSearchElement apply(@Nullable SnomedRefSetIndexEntry input) {
			return new CompactQuickSearchElement(input.getId(), input.getIconId(), input.getLabel(), false);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.b2international.snowowl.datastore.IQuickSearchContentProvider#getComponents(java.lang.String, com.b2international.snowowl.core.api.IBranchPath, int, java.util.Map)
	 */
	@Override
	public QuickSearchContentResult getComponents(final String queryExpression, final IBranchPathMap branchPathMap, final int limit, final Map<String, Object> configuration) {
		
		final int searchFlags = SnomedRefSetIndexQueryAdapter.SEARCH_BY_ID 
				| SnomedRefSetIndexQueryAdapter.SEARCH_BY_LABEL 
				| SnomedRefSetIndexQueryAdapter.SEARCH_PREFIXED_TERM
				| SnomedRefSetIndexQueryAdapter.SEARCH_REGULAR_ONLY;
		
		final SnomedRefSetType[] refSetTypes = getRefSetType(configuration);
		final SnomedRefSetIndexQueryAdapter queryAdapter = new SnomedRefSetIndexQueryAdapter(searchFlags, queryExpression, getReferencedComponentTypeValue(configuration), 
				getComponentIds(configuration), refSetTypes);
		final SnomedIndexServerService indexService = (SnomedIndexServerService) ApplicationContext.getInstance().getService(SnomedIndexService.class);
		final IBranchPath branchPath = getBranchPath(branchPathMap);
		final int totalHitCount = indexService.getHitCount(branchPath, queryAdapter);
		
		if (totalHitCount < 1) {
			return new QuickSearchContentResult();
		}
		
		final List<SnomedRefSetIndexEntry> refSets = indexService.search(branchPath, queryAdapter, limit);
		for (final Iterator<SnomedRefSetIndexEntry> itr = refSets.iterator(); itr.hasNext(); /**/) {
			
			final SnomedRefSetIndexEntry refSet = itr.next();

			final BooleanQuery activeConceptQuery = new BooleanQuery(true);
			activeConceptQuery.add(SnomedIndexQueries.ACTIVE_COMPONENT_QUERY, Occur.MUST);
			activeConceptQuery.add(CONCEPT_TYPE_QUERY, Occur.MUST);
			activeConceptQuery.add(new TermQuery(new Term(CommonIndexConstants.COMPONENT_ID, IndexUtils.longToPrefixCoded(refSet.getId()))), Occur.MUST);
			
			if (inactiveConcept(indexService, branchPath, activeConceptQuery)) {
				itr.remove();
			}
			
		}

		return new QuickSearchContentResult(totalHitCount, convertToDTO(refSets));
	}

	private boolean inactiveConcept(final SnomedIndexServerService indexService, final IBranchPath branchPath, final BooleanQuery activeConceptQuery) {
		return 1 > indexService.getHitCount(branchPath, activeConceptQuery, null);
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.b2international.snowowl.datastore.quicksearch.AbstractQuickSearchContentProvider#getEPackage()
	 */
	@Override
	protected EPackage getEPackage() {
		return SnomedRefSetPackage.eINSTANCE;
	}
	
	private List<QuickSearchElement> convertToDTO(List<SnomedRefSetIndexEntry> searchResults) {
		return Lists.transform(searchResults, new SnomedRefSetConverterFunction());
	}
	
	/*extracts the reference set type from the configuration*/
	private SnomedRefSetType[] getRefSetType(final Map<String, Object> configuration) {
		return null == configuration ? new SnomedRefSetType[] {} : getTypes(configuration);
	}

	/*returns with the reference set types given as an array of type ordinal.*/
	private SnomedRefSetType[] getTypes(final Map<String, Object> configuration) {
		
		final int[] typeOrdinals = (int[]) configuration.get(SnomedRefSetQuickSearchProvider.REFSET_TYPE_CONFIG_ID);
		final SnomedRefSetType[] types;
		
		if (typeOrdinals == null)
			types = new SnomedRefSetType[0];
		else {
			types = new SnomedRefSetType[typeOrdinals.length];
			for (int i = 0; i < typeOrdinals.length; i++) {
				types[i] = SnomedRefSetType.get(typeOrdinals[i]);
			}
		}
		
		return types;
	}
	
	/*extracts the reference component type from the configuration*/
	private String getReferencedComponentType(final Map<String, Object> configuration) {
		return null == configuration ? null : (String) configuration.get(SnomedRefSetQuickSearchProvider.REFERENCED_COMPONENT_TYPE_CONFIG_ID);
	}

	/*
	 * returns with the referenced component type as a Short. May return with null if the referenced component is
	 * unspecified or null or empty string. If null terminology component ID returned then the index query will not check the referenced component type 
	 * of the SNOMED CT concept.  
	 * */
	private Short getReferencedComponentTypeValue(final Map<String, Object> configuration) {
		if (StringUtils.isEmpty(getReferencedComponentType(configuration))) {
			return null;
		}
		
		if (CoreTerminologyBroker.UNSPECIFIED.equals(getReferencedComponentType(configuration))) {
			return null;
		}
		
		return CoreTerminologyBroker.getInstance().getTerminologyComponentIdAsShort(getReferencedComponentType(configuration));
	}
}
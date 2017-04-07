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
package com.b2international.snowowl.datastore.server.snomed.filteredrefset;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import bak.pcj.LongCollection;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.index.IndexUtils;
import com.b2international.snowowl.datastore.index.LongDocValuesCollector;
import com.b2international.snowowl.datastore.server.index.IndexServerService;
import com.b2international.snowowl.snomed.datastore.browser.SnomedIndexBrowserConstants;

public final class CollectMembersRunnable implements Runnable {
	
	private final TermQuery activeComponentQuery;
	private final TermQuery conceptTypeQuery;
	private final Query labelQuery;
	private final AtomicReference<LongCollection> collectedConceptIds;
	private final int maxDoc;
	private final boolean existingMembersOnly;
	private final IndexServerService<?> indexService;
	private final IBranchPath branchPath;
	private final long refSetId;

	public CollectMembersRunnable(TermQuery activeComponentQuery, TermQuery conceptTypeQuery, Query labelQuery,
			AtomicReference<LongCollection> collectedConceptIds, int maxDoc, boolean existingMembersOnly,
			IndexServerService<?> indexService, IBranchPath branchPath, long refSetId) {
		
		this.activeComponentQuery = activeComponentQuery;
		this.conceptTypeQuery = conceptTypeQuery;
		this.labelQuery = labelQuery;
		this.collectedConceptIds = collectedConceptIds;
		this.maxDoc = maxDoc;
		this.existingMembersOnly = existingMembersOnly;
		this.indexService = indexService;
		this.branchPath = branchPath;
		this.refSetId = refSetId;
	}

	@Override 
	public void run() {

		final BooleanQuery refSetMemberConceptQuery = new BooleanQuery(true);
		refSetMemberConceptQuery.add(activeComponentQuery, Occur.MUST);
		refSetMemberConceptQuery.add(conceptTypeQuery, Occur.MUST);

		final Occur refSetOccur = (existingMembersOnly) ? Occur.MUST : Occur.MUST_NOT;
		refSetMemberConceptQuery.add(new TermQuery(new Term(SnomedIndexBrowserConstants.CONCEPT_REFERRING_REFERENCE_SET_ID, IndexUtils.longToPrefixCoded(refSetId))), refSetOccur);

		//label
		if (null != labelQuery) {
			refSetMemberConceptQuery.add(labelQuery, Occur.MUST);
		}

		final LongDocValuesCollector conceptIdCollector = new LongDocValuesCollector(SnomedIndexBrowserConstants.COMPONENT_ID, maxDoc);
		indexService.search(branchPath, refSetMemberConceptQuery, conceptIdCollector);
		final LongCollection conceptIds = conceptIdCollector.getValues();
		conceptIds.trimToSize();
		
		collectedConceptIds.set(conceptIds);
	}
}
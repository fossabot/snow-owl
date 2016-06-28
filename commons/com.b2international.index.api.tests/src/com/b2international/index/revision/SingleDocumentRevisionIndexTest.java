/*
 * Copyright 2011-2016 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.index.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import java.util.Collection;

import org.junit.Test;

import com.b2international.index.query.Expressions;
import com.b2international.index.query.Query;
import com.b2international.index.revision.RevisionFixtures.Data;
import com.google.common.collect.ImmutableList;

public class SingleDocumentRevisionIndexTest extends BaseRevisionIndexTest {

	@Override
	protected Collection<Class<?>> getTypes() {
		return ImmutableList.<Class<?>>of(Data.class);
	}
	
	@Test
	public void searchEmptyIndexShouldReturnNullRevision() throws Exception {
		final Data revision = getRevision(MAIN, Data.class, STORAGE_KEY1);
		assertNull(revision);
	}
	
	@Test
	public void indexRevision() throws Exception {
		final Data data = new Data("field1", "field2");
		indexRevision(MAIN, STORAGE_KEY1, data);
		assertEquals(data, getRevision(MAIN, Data.class, STORAGE_KEY1));
	}

	@Test
	public void updateRevision() throws Exception {
		indexRevision();
		final Data data = new Data("field1Changed", "field2Changed");
		indexRevision(MAIN, STORAGE_KEY1, data);
		assertEquals(data, getRevision(MAIN, Data.class, STORAGE_KEY1));
	}

	@Test
	public void deleteRevision() throws Exception {
		indexRevision();
		deleteRevision(MAIN, Data.class, STORAGE_KEY1);
		assertNull(getRevision(MAIN, Data.class, STORAGE_KEY1));
	}
	
	@Test
	public void updateThenDeleteRevision() throws Exception {
		updateRevision();
		deleteRevision(MAIN, Data.class, STORAGE_KEY1);
		assertNull(getRevision(MAIN, Data.class, STORAGE_KEY1));
	}
	
	@Test
	public void searchDifferentRevisions() throws Exception {
		final Data first = new Data("field1", "field2");
		final Data second = new Data("field1Changed", "field2");
		
		indexRevision(MAIN, STORAGE_KEY1, first);
		indexRevision(MAIN, STORAGE_KEY2, second);
		
		final Query<Data> query = Query.builder(Data.class).selectAll().where(Expressions.exactMatch("field1", "field1")).build();
		final Iterable<Data> matches = search(MAIN, query);
		assertThat(matches).hasSize(1);
		assertThat(matches).containsOnly(first);
	}

	@Test
	public void searchMultipleRevisions() throws Exception {
		final Data first = new Data("field1", "field2");
		final Data second = new Data("field1", "field2Changed");
		
		indexRevision(MAIN, STORAGE_KEY1, first);
		indexRevision(MAIN, STORAGE_KEY1, second);
		
		final Query<Data> query = Query.builder(Data.class).selectAll().where(Expressions.exactMatch("field1", "field1")).build();
		final Iterable<Data> matches = search(MAIN, query);
		// only second version should match, the first revision should be unaccessible without timestamp
		assertThat(matches).hasSize(1);
		assertThat(matches).containsOnly(second);
	}
	
	@Test
	public void parentRevisionsStillVisibleAfterNewChildBranch() throws Exception {
		indexRevision();
		final String childBranch = "a";
		createBranch(MAIN, childBranch);
		assertNotNull(getRevision(childBranch, Data.class, STORAGE_KEY1));
		assertNotNull(getRevision(MAIN, Data.class, STORAGE_KEY1));
	}
	
	@Test
	public void updateRevisionShadowsRevisionOnParent() throws Exception {
		indexRevision();
		final String childBranch = "a";
		createBranch(MAIN, childBranch);
		
		// put updated revision to child branch
		final Data data = new Data("field1Changed", "field2Changed");
		indexRevision(childBranch, STORAGE_KEY1, data);
		// lookup by storageKey on child should return new revision
		assertEquals(data, getRevision(childBranch, Data.class, STORAGE_KEY1));

		final Data expectedRevisionOnMain = new Data("field1", "field2");
		final Data actualRevisionOnMain = getRevision(MAIN, Data.class, STORAGE_KEY1);
		assertEquals(expectedRevisionOnMain, actualRevisionOnMain);
	}
	
	@Test
	public void divergedBranchContentShouldBeAccessibleBasedOnSegments() throws Exception {
		updateRevisionShadowsRevisionOnParent();
		
		// put new revision to MAIN
		final Data data = new Data("field1ChangedOnMain", "field2ChangedOnMain");
		indexRevision(MAIN, STORAGE_KEY1, data);
		// lookup by storageKey on child should return new revision
		assertEquals(data, getRevision(MAIN, Data.class, STORAGE_KEY1));
		
		// child branch still has his own updated revision
		final Data expectedRevisionOnChild = new Data("field1Changed", "field2Changed");
		final Data actualRevisionOnChild = getRevision("a", Data.class, STORAGE_KEY1);
		assertEquals(expectedRevisionOnChild, actualRevisionOnChild);
	}
	
}

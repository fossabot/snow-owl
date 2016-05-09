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
package com.b2international.index.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.b2international.index.FSIndexAdmin;
import com.b2international.index.IndexClient;
import com.b2international.index.LuceneClient;
import com.b2international.index.Searcher;
import com.b2international.index.Writer;
import com.b2international.index.json.JsonDocumentMapping;
import com.b2international.index.query.Expressions;
import com.b2international.index.query.Query;
import com.b2international.index.tests.Fixtures.Data;
import com.b2international.index.tests.Fixtures.DeepData;
import com.b2international.index.tests.Fixtures.MultipleNestedData;
import com.b2international.index.tests.Fixtures.NestedData;
import com.b2international.index.tests.Fixtures.ParentData;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 4.7
 */
public class LuceneClientTest {

	private static final String KEY = "key";
	private static final String KEY2 = "key2";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	
	private IndexClient client;

	@Before
	public void givenClient() {
		final ObjectMapper mapper = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		client = new LuceneClient(new FSIndexAdmin(folder.getRoot(), UUID.randomUUID().toString()), mapper);
		client.admin().create();
	}
	
	@Test
	public void searchEmptyIndexShouldReturnNullDocument() throws Exception {
		try (Searcher searcher = client.searcher()) {
			assertNull(searcher.get(Data.class, KEY));
		}
	}
	
	@Test
	public void indexDocument() throws Exception {
		final Data data = new Data();
		try (final Writer writer = client.writer()) {
			writer.put(KEY, data);
			writer.commit();
		}
		try (Searcher searcher = client.searcher()) {
			final Data actual = searcher.get(Data.class, KEY);
			assertEquals(data, actual);
		}
	}
	
	@Test
	public void updateDocument() throws Exception {
		indexDocument();
		final Data data = new Data();
		data.field1 = "field1Updated";
		data.field2 = "field2Updated";
		try (final Writer writer = client.writer()) {
			writer.put(KEY, data);
			writer.commit();
		}
		try (Searcher searcher = client.searcher()) {
			final Iterable<Data> matches = searcher.search(Query.builder(Data.class).selectAll().where(JsonDocumentMapping.matchId(KEY)).build());
			assertThat(matches).hasSize(1);
			assertThat(matches).containsOnly(data);
		}
	}
	
	@Test
	public void indexDocumentWithSearchDuringTransaction() throws Exception {
		final Data data = new Data();
		try (final Writer writer = client.writer()) {
			writer.put(KEY, data);
			try (Searcher searcher = client.searcher()) {
				assertNull(searcher.get(Data.class, KEY));
			}
			writer.commit();
		}
	}
	
	@Test
	public void deleteDocument() throws Exception {
		indexDocument();
		try (final Writer writer = client.writer()) {
			writer.remove(Data.class, KEY);
			writer.commit();
		}
		try (Searcher searcher = client.searcher()) {
			assertNull(searcher.get(Data.class, KEY));
		}
	}
	
	@Test
	public void searchDocuments() throws Exception {
		final Data data = new Data();
		final Data data2 = new Data();
		data2.field1 = "field1Changed";
		try (final Writer writer = client.writer()) {
			writer.put(KEY, data);
			writer.put(KEY2, data2);
			writer.commit();
		}
		// seach for field1Changed value, it should return a single doc
		try (Searcher searcher = client.searcher()) {
			final Query<Data> query = Query.builder(Data.class).selectAll().where(Expressions.exactMatch("field1", "field1")).build();
			final Iterable<Data> matches = searcher.search(query);
			assertThat(matches).hasSize(1);
			assertThat(matches).containsOnly(data);
		}
	}
	
	@Test
	public void indexNestedDocument() throws Exception {
		final ParentData data = new ParentData(new NestedData("field2"));
		try (Writer writer = client.writer()) {
			writer.put(KEY, data);
			writer.commit();
		}
		// try to get nested document as is first
		try (Searcher searcher = client.searcher()) {
			final ParentData actual = searcher.get(ParentData.class, KEY);
			assertEquals(data, actual);
		}
	}
	
	@Test
	public void deleteDocumentWithNestedDocShouldDeleteNested() throws Exception {
		indexNestedDocument();
		try (Writer writer = client.writer()) {
			writer.remove(ParentData.class, KEY);
			writer.commit();
		}
		try (Searcher searcher = client.searcher()) {
			final Query<ParentData> parentDataQuery = Query.builder(ParentData.class).selectAll().where(Expressions.matchAll()).build();
			final Iterable<ParentData> matches = searcher.search(parentDataQuery);
			assertThat(matches).isEmpty();
			final Query<NestedData> nestedDataQuery = Query.builder(NestedData.class).selectAll().where(Expressions.matchAll()).build();
			final Iterable<NestedData> nestedMatches = searcher.search(nestedDataQuery);
			assertThat(nestedMatches).isEmpty();
		}
	}
	
	@Test
	public void searchNestedDocument() throws Exception {
		final ParentData data = new ParentData(new NestedData("field2"));
		final ParentData data2 = new ParentData(new NestedData("field2"));
		data2.nestedData.field2 = "field2Changed";
		try (Writer writer = client.writer()) {
			writer.put(KEY, data);
			writer.put(KEY2, data2);
			writer.commit();
		}
		// try to get nested document as is first
		try (Searcher searcher = client.searcher()) {
			final Query<ParentData> query = Query.builder(ParentData.class).selectAll().where(Expressions.nestedMatch("nestedData", Expressions.exactMatch("field2", "field2"))).build();
			final Iterable<ParentData> matches = searcher.search(query);
			assertThat(matches).hasSize(1);
			assertThat(matches).containsOnly(data);
		}
	}
	
	@Test
	public void indexDeeplyNestedDocument() throws Exception {
		final DeepData data = new DeepData(new ParentData(new NestedData("field2")));
		final DeepData data2 = new DeepData(new ParentData(new NestedData("field2")));
		data2.parentData.nestedData.field2 = "field2Changed";
		try (Writer writer = client.writer()) {
			writer.put(KEY, data);
			writer.put(KEY2, data2);
			writer.commit();
		}
		// try to get nested document as is first
		try (Searcher searcher = client.searcher()) {
			// get single data
			final DeepData actual = searcher.get(DeepData.class, KEY);
			assertEquals(data, actual);
			// try nested query
			final Query<DeepData> query = Query.builder(DeepData.class).selectAll()
					.where(Expressions.nestedMatch("parentData.nestedData", 
							Expressions.exactMatch("field2", "field2"))
							).build();
			final Iterable<DeepData> matches = searcher.search(query);
			assertThat(matches).hasSize(1);
			assertThat(matches).containsOnly(data);
		}
	}
	
	@Test
	public void indexCollectionOfNestedDocs() throws Exception {
		final MultipleNestedData data = new MultipleNestedData(Arrays.asList(new NestedData("field2"), new NestedData("field2Another")));
		final MultipleNestedData data2 = new MultipleNestedData(Arrays.asList(new NestedData("field2Changed"), new NestedData("field2AnotherChanged")));
		// index multi nested data
		try (Writer writer = client.writer()) {
			writer.put(KEY, data);
			writer.put(KEY2, data2);
			writer.commit();
		}
		
		try (Searcher searcher = client.searcher()) {
			// get data by key
			final MultipleNestedData actual = searcher.get(MultipleNestedData.class, KEY);
			assertEquals(data, actual);
			// try nested query on collections
			final Query<MultipleNestedData> query = Query.builder(MultipleNestedData.class).selectAll()
					.where(Expressions.nestedMatch("nestedDatas", 
							Expressions.exactMatch("field2", "field2"))
							).build();
			final Iterable<MultipleNestedData> matches = searcher.search(query);
			assertThat(matches).hasSize(1);
			assertThat(matches).containsOnly(data);
		}
	}
	
	@Test
	public void uncommittedTransactionShouldNotChangeTheIndex() throws Exception {
		final Data data = new Data();
		try (Writer writer = client.writer()) {
			writer.put(KEY, data);
		}
		// after the failed transaction data should not be in the index
		try (Searcher searcher = client.searcher()) {
			assertNull(searcher.get(Data.class, KEY));
		}
	}
	
	@Test
	public void tx1CommitShouldNotCommitTx2Changes() throws Exception {
		final Data data = new Data();
		try (Writer tx1 = client.writer(); 
				Writer tx2 = client.writer()) {
			tx1.put(KEY, data);
			tx2.put(KEY2, data);
			tx1.commit();
			
			// at this point tx2 content should not be visible
			try (Searcher searcher = client.searcher()) {
				assertEquals(data, searcher.get(Data.class, KEY));
				assertNull(searcher.get(Data.class, KEY2));
			}
			
			tx2.commit();
			
			try (Searcher searcher = client.searcher()) {
				assertEquals(data, searcher.get(Data.class, KEY));
				assertEquals(data, searcher.get(Data.class, KEY2));
			}
		}
	}
	
	@After
	public void after() {
		client.close();
	}
	
}

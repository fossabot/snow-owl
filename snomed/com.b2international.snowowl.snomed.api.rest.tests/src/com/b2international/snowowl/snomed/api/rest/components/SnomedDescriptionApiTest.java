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
package com.b2international.snowowl.snomed.api.rest.components;

import static com.b2international.snowowl.test.commons.rest.RestExtensions.givenAuthenticatedRequest;
import static org.hamcrest.CoreMatchers.equalTo;

import java.util.Map;

import org.junit.Test;

import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.api.domain.CaseSignificance;
import com.b2international.snowowl.snomed.api.rest.AbstractSnomedApiTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * @since 2.0
 */
public class SnomedDescriptionApiTest extends AbstractSnomedApiTest {

	private static final String DISEASE = "64572001";
	private static final String TEMPORAL_CONTEXT = "410510008";
	private static final String FINDING_CONTEXT = "408729009";
	
	private Builder<Object, Object> createRequestBuilder(String conceptId, String term, String moduleId, String typeId, String comment) {
		return ImmutableMap.builder()
				.put("conceptId", conceptId)
				.put("moduleId", moduleId)
				.put("typeId", typeId)
				.put("term", term)
				.put("languageCode", "en")
				.put("acceptability", ACCEPTABLE_ACCEPTABILITY_MAP)
				.put("commitComment", comment);
	}
	
	private Map<?, ?> createRequestBody(String conceptId, String term, String moduleId, String typeId, String comment) {
		return createRequestBuilder(conceptId, term, moduleId, typeId, comment).build();
	}
	
	private Map<?, ?> createRequestBody(String conceptId, String term, String moduleId, String typeId, CaseSignificance caseSignificance, String comment) {
		return createRequestBuilder(conceptId, term, moduleId, typeId, comment)
			.put("caseSignificance", caseSignificance.name())
			.build();
	}
	
	@Test
	public void createDescriptionNonExistentBranch() {
		final Map<?, ?> requestBody = createRequestBody(DISEASE, "Rare disease", Concepts.MODULE_SCT_CORE, Concepts.SYNONYM, "New description on a non-existent branch");
		assertComponentCreationStatus("descriptions", requestBody, 404, "MAIN", "1998-01-31") // !
		.and()
			.body("status", equalTo(404));
	}
	
	@Test
	public void createDescriptionWithNonExistentConcept() {
		final Map<?, ?> requestBody = createRequestBody("1", "Rare disease", Concepts.MODULE_SCT_CORE, Concepts.SYNONYM, "New description with a non-existent concept ID");		
		assertComponentCanNotBeCreated("descriptions", requestBody, "MAIN");
	}
	
	@Test
	public void createDescriptionWithNonexistentType() {
		final Map<?, ?> requestBody = createRequestBody(DISEASE, "Rare disease", Concepts.MODULE_SCT_CORE, "2", "New description with a non-existent type ID");		
		assertComponentCanNotBeCreated("descriptions", requestBody, "MAIN");
	}
	
	@Test
	public void createDescriptionWithNonexistentModule() {
		final Map<?, ?> requestBody = createRequestBody(DISEASE, "Rare disease", "3", Concepts.SYNONYM, "New description with a non-existent module ID");
		assertComponentCanNotBeCreated("descriptions", requestBody, "MAIN");
	}
	
	@Test
	public void createDescriptionWithoutCommitComment() {
		final Map<?, ?> requestBody = createRequestBody(DISEASE, "Rare disease", Concepts.MODULE_SCT_CORE, Concepts.SYNONYM, "");
		assertComponentCanNotBeCreated("descriptions", requestBody, "MAIN");
	}

	private void assertCaseSignificance(String descriptionId, CaseSignificance caseSignificance) {
		givenAuthenticatedRequest(SCT_API)
		.when()
			.get("/MAIN/descriptions/{id}", descriptionId)
		.then()
		.assertThat()
			.statusCode(200)
		.and()
			.body("caseSignificance", equalTo(caseSignificance.toString()));
	}

	@Test
	public void createDescription() {
		final Map<?, ?> requestBody = createRequestBody(DISEASE, "Rare disease", Concepts.MODULE_SCT_CORE, Concepts.SYNONYM, "New description on MAIN");
		String descriptionId = assertComponentCanBeCreated("descriptions", requestBody, "MAIN");
		assertCaseSignificance(descriptionId, CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
	}
	
	@Test
	public void createDescriptionCaseSensitive() {
		final Map<?, ?> requestBody = createRequestBody(DISEASE, "Rare disease", Concepts.MODULE_SCT_CORE, Concepts.SYNONYM, CaseSignificance.CASE_INSENSITIVE, "New description on MAIN");
		String descriptionId = assertComponentCanBeCreated("descriptions", requestBody, "MAIN");
		assertCaseSignificance(descriptionId, CaseSignificance.CASE_INSENSITIVE);
	}
}

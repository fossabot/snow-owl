/*
 * Copyright 2018-2019 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.fhir.core.codesystems;

/**
 * FHIR Code system hierarchy meaning code system
 * @since 6.4
 */
@FhirInternalCodeSystem(
	uri = "http://hl7.org/fhir/codesystem-hierarchy-meaning",
	resourceNarrative = "The meaning of the hierarchy of concepts in a code system."
)
public enum CodeSystemHierarchyMeaning implements FhirInternalCode {
	
	GROUPED_BY("Grouped By"),
	IS_A("Is-A"), 
	PART_OF("Part Of"), 
	CLASSIFIED_WITH("Classified With");
	
	private String displayName;

	private CodeSystemHierarchyMeaning(String displayName) {
		this.displayName = displayName;
	}
	
	@Override
	public String getDisplayName() {
		return displayName;
	}
	
	@Override
	public String getCodeValue() {
		return name().toLowerCase().replace("_", "-");
	}
	
}

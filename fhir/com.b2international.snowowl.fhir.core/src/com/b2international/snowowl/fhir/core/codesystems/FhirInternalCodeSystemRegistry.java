/*
 * Copyright 2019 B2i Healthcare Pte Ltd, http://b2i.sg
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

import java.util.List;
import java.util.stream.Collectors;

import com.b2international.commons.extension.ClassPathScanner;
import com.b2international.snowowl.fhir.core.model.codesystem.CodeSystem;
import com.b2international.snowowl.fhir.core.model.codesystem.CodeSystem.Builder;
import com.b2international.snowowl.fhir.core.model.codesystem.Concept;
import com.b2international.snowowl.fhir.core.model.dt.Narrative;
import com.b2international.snowowl.fhir.core.model.dt.Uri;
import com.google.common.base.Strings;

/**
 * @since 7.2
 */
public enum FhirInternalCodeSystemRegistry {

	INSTANCE;
	
	private final List<CodeSystem> codeSystems;
	
	private FhirInternalCodeSystemRegistry() {
		this.codeSystems = ClassPathScanner.INSTANCE.getComponentsClassesByInterface(FhirInternalCode.class)
				.stream()
				.filter(c -> c.isEnum() && c.isAnnotationPresent(FhirInternalCodeSystem.class))
				.map(c -> buildCodeSystem((Class) c, c.getAnnotation(FhirInternalCodeSystem.class)))
				.collect(Collectors.toList());
	}
	
	public List<CodeSystem> getCodeSystems() {
		return codeSystems;
	}
	
	private <T extends Enum<T>> CodeSystem buildCodeSystem(Class<T> enumType, FhirInternalCodeSystem codeSystem) {
		
		String supportedUri = codeSystem.uri();
		String id = supportedUri.substring(supportedUri.lastIndexOf("/") + 1, supportedUri.length());
		
		Builder builder = CodeSystem.builder(id)
			.language("en")
			.name(enumType.getSimpleName())
			.publisher("www.hl7.org")
			.copyright("© 2011+ HL7")
			.version(codeSystem.version())
			.caseSensitive(true)
			.status(PublicationStatus.ACTIVE)
			.url(new Uri(supportedUri))
			.content(CodeSystemContentMode.COMPLETE);
		
		// human-readable narrative
		String resourceNarrative = codeSystem.resourceNarrative();
		if (!Strings.isNullOrEmpty(resourceNarrative)) {
			builder.text(Narrative.builder()
				.div("<div>" + resourceNarrative + "</div>")
				.status(NarrativeStatus.GENERATED)
				.build());
		}
		
		int counter = 0;

		for (T constant : enumType.getEnumConstants()) {
			if (constant instanceof FhirInternalCode) {
				Concept concept = ((FhirInternalCode) constant).toConcept();
				builder.addConcept(concept);
				counter++;
			}
		}
		
		builder.count(counter);
		return builder.build();
	}
	
}

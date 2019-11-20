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
 * FHIR discriminator type
 * https://www.hl7.org/fhir/codesystem-discriminator-type.html#DiscriminatorType
 * 
 * @since 7.1
 */
@FhirInternalCodeSystem(
	uri = "http://hl7.org/fhir/discriminator-type",
	resourceNarrative = "How an element value is interpreted when discrimination is evaluated."
)
public enum DiscriminatorType implements FhirInternalCode {
	
	VALUE,
	EXISTS, 
	PATTERN,
	TYPE,
	PROFILE;
	
}

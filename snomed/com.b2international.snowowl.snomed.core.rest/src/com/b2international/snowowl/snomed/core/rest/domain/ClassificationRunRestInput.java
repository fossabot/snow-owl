/*
 * Copyright 2011-2020 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.core.rest.domain;

import org.hibernate.validator.constraints.NotEmpty;

/**
 * @since 4.0
 */
public class ClassificationRunRestInput {

	@NotEmpty
	private String reasonerId;

	@NotEmpty
	private String branch;

	public String getReasonerId() {
		return reasonerId;
	}

	public void setReasonerId(final String reasonerId) {
		this.reasonerId = reasonerId;
	}
	
	public String getBranch() {
		return branch;
	}

	public void setBranch(final String branch) {
		this.branch = branch;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("ClassificationRestInput [reasonerId=");
		builder.append(reasonerId);
		builder.append(", branch=");
		builder.append(branch);
		builder.append("]");
		return builder.toString();
	}
}

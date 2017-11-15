/*
 * Copyright 2017 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.core.validation.issue;

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.request.SearchResourceRequestBuilder;
import com.b2international.snowowl.core.request.SystemRequestBuilder;
import com.b2international.snowowl.core.validation.issue.ValidationIssueSearchRequest.OptionKey;

/**
 * @since 6.0
 */
public final class ValidationIssueSearchRequestBuilder
		extends SearchResourceRequestBuilder<ValidationIssueSearchRequestBuilder, ServiceProvider, ValidationIssues>
		implements SystemRequestBuilder<ValidationIssues> {

	ValidationIssueSearchRequestBuilder() {}
	
	public ValidationIssueSearchRequestBuilder filterByRule(final String ruleId) {
		return addOption(OptionKey.RULE_ID, ruleId);
	}
	
	public ValidationIssueSearchRequestBuilder filterByRules(final Iterable<? extends String> ruleIds) {
		return addOption(OptionKey.RULE_ID, ruleIds);
	}
	
	public ValidationIssueSearchRequestBuilder filterByBranchPath(final String branchPath) {
		return addOption(OptionKey.BRANCH_PATH, branchPath);
	}
	
	public ValidationIssueSearchRequestBuilder filterByBranchPaths(final Iterable<? extends String> branchPaths) {
		return addOption(OptionKey.BRANCH_PATH, branchPaths);
	}
 	
	@Override
	protected SearchResourceRequest<ServiceProvider, ValidationIssues> createSearch() {
		return new ValidationIssueSearchRequest();
	}

}

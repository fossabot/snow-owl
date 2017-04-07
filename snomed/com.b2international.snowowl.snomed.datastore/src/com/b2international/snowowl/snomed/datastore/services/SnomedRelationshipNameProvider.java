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
package com.b2international.snowowl.snomed.datastore.services;

import org.eclipse.core.runtime.IStatus;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.CoreTerminologyBroker;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.IComponent;
import com.b2international.snowowl.core.api.IComponentNameProvider;
import com.b2international.snowowl.snomed.Relationship;
import com.b2international.snowowl.snomed.datastore.SnomedRelationshipIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.SnomedClientIndexService;
import com.b2international.snowowl.snomed.datastore.index.SnomedRelationshipIndexQueryAdapter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

/**
 * Singleton class for providing some human readable label of a SNOMED CT relationship.
 * @see IComponentNameProvider
 */
public enum SnomedRelationshipNameProvider implements IComponentNameProvider {

	/**
	 * Singleton label provider for SNOMED CT relationships.
	 */
	INSTANCE;

	/**
	 * Accepts the followings as argument:
	 * <p>
	 * <ul>
	 * <li>{@link Relationship SNOMED CT relationship}</li>
	 * <li>{@link SnomedRelationshipIndexEntry SNOMED CT relationship (Snor)}</li>
	 * <li>{@link String SNOMED CT relationship identifier as string}</li>
	 * <li>{@link IStatus Status}</li>
	 * </ul>
	 * </p>
	 */
	/*
	 * (non-Javadoc)
	 * @see com.b2international.snowowl.core.api.IComponentNameProvider#getText(java.lang.Object)
	 */
	@Override
	public String getText(final Object object) {
		if (object instanceof String) {
			// TODO: put a meaningful label on SnomedRelationshipIndexEntry
			final SnomedRelationshipIndexEntry relationshipIndexEntry = searchRelationshipById(object);
			if (relationshipIndexEntry != null) {
				String[] labels = ApplicationContext.getInstance().getService(IClientSnomedComponentService.class).getLabels(new String[] {
						relationshipIndexEntry.getObjectId(), relationshipIndexEntry.getAttributeId(),relationshipIndexEntry.getValueId()});
				return labels[0] + " " + labels[1] + " " + labels[2];
			}
		}
		final IComponent<?> component = CoreTerminologyBroker.getInstance().adapt(object);
		return component == null ? null == object ? "" : String.valueOf(object) : component.getLabel();
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.core.api.IComponentNameProvider#getComponentLabel(com.b2international.snowowl.core.api.IBranchPath, java.lang.String)
	 */
	@Override
	public String getComponentLabel(final IBranchPath branchPath, final String componentId) {
		
		Preconditions.checkNotNull(branchPath, "Branch path argument cannot be null.");
		Preconditions.checkNotNull(componentId, "Component ID argument cannot be null.");
		
		return ApplicationContext.getInstance().getService(ISnomedComponentService.class).getLabels(branchPath, componentId)[0];
	}
	
	/*searches for a relationship index entry by its SNOMED CT identifier*/
	private SnomedRelationshipIndexEntry searchRelationshipById(final Object object) {
		return Iterables.getOnlyElement(getIndexSerivce().search(createQueryAdapter(object), 1), null);
	}

	/*creates a query adapter instance to search for a SNOMED CT relationship by its ID*/
	private SnomedRelationshipIndexQueryAdapter createQueryAdapter(final Object object) {
		return new SnomedRelationshipIndexQueryAdapter((String) object, SnomedRelationshipIndexQueryAdapter.SEARCH_RELATIONSHIP_ID);
	}

	/*returns with the Lucen index service*/
	private SnomedClientIndexService getIndexSerivce() {
		return ApplicationContext.getInstance().getService(SnomedClientIndexService.class);
	}

}
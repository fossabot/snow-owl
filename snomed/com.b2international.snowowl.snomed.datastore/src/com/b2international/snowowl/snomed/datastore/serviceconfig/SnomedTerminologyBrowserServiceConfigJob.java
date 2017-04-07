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
package com.b2international.snowowl.snomed.datastore.serviceconfig;

import com.b2international.snowowl.datastore.serviceconfig.ClientServiceConfigJob;
import com.b2international.snowowl.snomed.datastore.SnomedClientTerminologyBrowser;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;

/**
 * Configuration job to initialize and register the concept hierarchy browser service for the SNOMED&nbsp;CT ontology on the client side.
 * <p>
 * <b>Note:</b> this class belongs to the {@link SnomedDatastoreActivator#PLUGIN_ID SNOMED&nbsp;CT data store plug-in identifier} job
 * family.
 * 
 */
public class SnomedTerminologyBrowserServiceConfigJob extends ClientServiceConfigJob<SnomedTerminologyBrowser, SnomedClientTerminologyBrowser> {

	private static final String JOB_NAME = "SNOMED CT terminology browser configuration...";
	
	public SnomedTerminologyBrowserServiceConfigJob() {
		super(JOB_NAME, SnomedDatastoreActivator.PLUGIN_ID);
	}
	
	/* (non-Javadoc)
	 * @see com.b2international.snowowl.datastore.serviceconfig.ClientServiceConfigJob#getBranchAwareClass()
	 */
	@Override
	protected Class<SnomedTerminologyBrowser> getServiceClass() {
		return SnomedTerminologyBrowser.class;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.datastore.serviceconfig.ClientServiceConfigJob#getTrackingClass()
	 */
	@Override
	protected Class<SnomedClientTerminologyBrowser> getTrackingClass() {
		return SnomedClientTerminologyBrowser.class;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.datastore.serviceconfig.ClientServiceConfigJob#createTrackingService(java.lang.Object)
	 */
	@Override
	protected SnomedClientTerminologyBrowser createTrackingService(final SnomedTerminologyBrowser branchAwareService) {
		return new SnomedClientTerminologyBrowser(branchAwareService);
	}
}
/*
 * Copyright 2018 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.index.es;

import static com.google.common.base.Preconditions.checkState;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

import com.b2international.org.apache.lucene.Activator;

/**
 * @since 6.6
 */
public final class EsClient {

	public static final RestHighLevelClient create(final HttpHost host) {
		// XXX: Adjust the thread context classloader while ES client is initializing 
		return Activator.withTccl(() -> {
			final RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(host));
			checkState(client.ping(), "The cluster at '%s' is not available.", host.toURI());
			return client;
		}); 
	}
}
/*
 * Copyright 2017-2018 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.datastore.request.rf2.importer;

import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static com.google.common.collect.Maps.newHashMapWithExpectedSize;
import static com.google.common.collect.Sets.newHashSet;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.cdo.CDOObject;
import org.eclipse.emf.cdo.util.CommitException;
import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.collections.PrimitiveMaps;
import com.b2international.collections.PrimitiveSets;
import com.b2international.collections.longs.LongIterator;
import com.b2international.collections.longs.LongKeyMap;
import com.b2international.collections.longs.LongSet;
import com.b2international.commons.collect.LongSets;
import com.b2international.commons.graph.LongTarjan;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.core.domain.IComponent;
import com.b2international.snowowl.core.domain.TransactionContextProvider;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.datastore.cdo.CDOServerCommitBuilder;
import com.b2international.snowowl.datastore.oplock.impl.DatastoreLockContextDescriptions;
import com.b2international.snowowl.datastore.request.RepositoryRequests;
import com.b2international.snowowl.snomed.Concept;
import com.b2international.snowowl.snomed.Description;
import com.b2international.snowowl.snomed.Relationship;
import com.b2international.snowowl.snomed.core.domain.SnomedComponent;
import com.b2international.snowowl.snomed.core.domain.SnomedCoreComponent;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSetMember;
import com.b2international.snowowl.snomed.datastore.SnomedEditingContext;
import com.b2international.snowowl.snomed.datastore.id.SnomedIdentifiers;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.AbstractRf2RowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.RF2MRCMAttributeRangeRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2AssocationRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2AttributeValueRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2ComplexMapRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2ConceptRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2DescriptionRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2DescriptionTypeRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2ExtendedMapRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2LanguageRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2MRCMAttributeDomainRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2MRCMDomainRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2MRCMModuleScopeRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2ModuleDependencyRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2OWLExpressionRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2RelationshipRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2SimpleMapWithDescriptionRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2SimpleRefSetRowValidator;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2ValidationDefects;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2ValidationResponseEntity;
import com.b2international.snowowl.snomed.datastore.request.rf2.validation.Rf2ValidationType;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSet;
import com.b2international.snowowl.terminologymetadata.CodeSystem;
import com.b2international.snowowl.terminologymetadata.CodeSystemVersion;
import com.b2international.snowowl.terminologyregistry.core.builder.CodeSystemVersionBuilder;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * @since 6.0
 */
public final class Rf2EffectiveTimeSlice {
	
	private static final Logger LOG = LoggerFactory.getLogger("RF2 import");

	private static final int BATCH_SIZE = 5000;

	private final Date effectiveDate;
	private final String effectiveTime;
	
	private final LongKeyMap<Set<String>> membersByContainer;
	private final LongKeyMap<LongSet> dependenciesByComponent;
	
	// tmp map to quickly collect batch of items before flushing it to disk
	private final Map<String, String[]> tmpComponentsById;
	private final HTreeMap<String, String[]> componentsById;
	
	private final Map<String, Long> storageKeysByComponent;
	private final Map<String, Long> storageKeysByRefSet;
	private final boolean loadOnDemand;
	
	public Rf2EffectiveTimeSlice(DB db, String effectiveTime, Map<String, Long> storageKeysByComponent, Map<String, Long> storageKeysByRefSet, boolean loadOnDemand) {
		if (EffectiveTimes.UNSET_EFFECTIVE_TIME_LABEL.equals(effectiveTime)) {
			this.effectiveDate = null;
			this.effectiveTime = effectiveTime;
		} else {
			this.effectiveDate = EffectiveTimes.parse(effectiveTime, DateFormats.SHORT);
			this.effectiveTime = EffectiveTimes.format(effectiveDate, DateFormats.DEFAULT);
		}
		
		this.storageKeysByComponent = storageKeysByComponent;
		this.storageKeysByRefSet = storageKeysByRefSet;
		this.componentsById = db.hashMap(effectiveTime, Serializer.STRING, Serializer.ELSA).create();
		this.tmpComponentsById = newHashMapWithExpectedSize(BATCH_SIZE);
		this.dependenciesByComponent = PrimitiveMaps.newLongKeyOpenHashMap();
		this.membersByContainer = PrimitiveMaps.newLongKeyOpenHashMap();
		this.loadOnDemand = loadOnDemand;
	}

	private <T extends SnomedComponent> T getComponent(String componentId) {
		final String[] valuesWithType = componentsById.get(componentId);

		// skip non-RF2 componentIds
		if (valuesWithType == null) {
			return null;
		}
		
		for (Rf2ContentType<?> resolver : Rf2Format.getContentTypes()) {
			if (valuesWithType[0].equals(resolver.getType())) {
				String[] values = new String[valuesWithType.length - 1];
				System.arraycopy(valuesWithType, 1, values, 0, valuesWithType.length - 1);
				return (T) resolver.resolve(values);
			}
		}
		
		throw new IllegalArgumentException("Unrecognized RF2 component: " + componentId + " - " + valuesWithType);
	}
	
	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void register(String containerId, Rf2ContentType<?> type, String[] values, Rf2ValidationResponseEntity validationEntity) {
		String[] valuesWithType = new String[values.length + 1];
		valuesWithType[0] = type.getType();
		System.arraycopy(values, 0, valuesWithType, 1, values.length);

		final String componentId = values[0];
		final long containerIdL = Long.parseLong(containerId);
		// track refset members via membersByContainer map
		if (Rf2RefSetContentType.class.isAssignableFrom(type.getClass())) {
			if (!membersByContainer.containsKey(containerIdL)) {
				membersByContainer.put(containerIdL, newHashSet());
			}
			membersByContainer.get(containerIdL).add(componentId);
		} else {
			// register other non-concept components in the dependency graph to force strongly connected subgraphs
			if (!IComponent.ROOT_ID.equals(containerId)) {
				registerDependencies(containerIdL, PrimitiveSets.newLongOpenHashSet(Long.parseLong(componentId)));
			}
		}
		final AbstractRf2RowValidator validator = assignTerminologyRowValidator(type, values, validationEntity);
		if (validator != null) {
			validator.validateRows(type.getHeaderColumns());
		}
		tmpComponentsById.put(componentId, valuesWithType);
		if (tmpComponentsById.size() >= BATCH_SIZE) {
			flush();
		}
	}
	
	private AbstractRf2RowValidator assignTerminologyRowValidator(Rf2ContentType<?> type, String[] values, Rf2ValidationResponseEntity validationEntity) {
		if (type instanceof Rf2ConceptContentType) {
			return new Rf2ConceptRowValidator(validationEntity, values);
		} else if (type instanceof Rf2DescriptionContentType) {
			return new Rf2DescriptionRowValidator(validationEntity, values);
		} else if (type instanceof Rf2RelationshipContentType) {
			return new Rf2RelationshipRowValidator(validationEntity, values);
		} else if (type instanceof Rf2RefSetContentType) {
			return assignRefsetRowValidator(type, values, validationEntity); 
		}
		
		validationEntity.put(Rf2ValidationType.WARNING, Rf2ValidationDefects.ENCOUNTER_UNKNOWN_RELEASE_FILE.getLabel());
		return null;
	}
	
	private AbstractRf2RowValidator assignRefsetRowValidator(Rf2ContentType<?> type, String[] values, Rf2ValidationResponseEntity validationEntity) {
		if (type instanceof Rf2AssociationRefSetContentType) {
			return new Rf2AssocationRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2AttributeValueRefSetContentType) {
			return new Rf2AttributeValueRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2ComplexMapRefSetContentType) {
			return new Rf2ComplexMapRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2DescriptionTypeRefSetContentType) {
			return new Rf2DescriptionTypeRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2ExtendedMapRefSetContentType) {
			return new Rf2ExtendedMapRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2LanguageRefSetContentType) {
			return new Rf2LanguageRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2ModuleDependencyRefSetContentType) {
			return new Rf2ModuleDependencyRefSetRowValidator(validationEntity, values); 
		} else if (type instanceof Rf2MRCMAttributeDomainRefSetContentType) {
			return new Rf2MRCMAttributeDomainRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2MRCMAttributeRangeRefSetContentType) {
			return new RF2MRCMAttributeRangeRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2MRCMDomainRefSetContentType) {
			return new Rf2MRCMDomainRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2MRCMModuleScopeRefSetContentType) {
			return new Rf2MRCMModuleScopeRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2OwlExpressionRefSetContentType) {
			return new Rf2OWLExpressionRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2SimpleMapRefSetContentType) {
			return new Rf2SimpleRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2SimpleMapWithDescriptionContentType) {
			return new Rf2SimpleMapWithDescriptionRefSetRowValidator(validationEntity, values);
		} else if (type instanceof Rf2SimpleRefSetContentType) {
			return new Rf2SimpleRefSetRowValidator(validationEntity, values);
		}
		
		validationEntity.put(Rf2ValidationType.WARNING, Rf2ValidationDefects.ENCOUNTER_UNKNOWN_RELEASE_FILE.getLabel());
		return null;
	}
	
	public void registerDependencies(long componentId, LongSet dependencies) {
		if (!dependenciesByComponent.containsKey(componentId)) {
			dependenciesByComponent.put(componentId, dependencies);
		} else {
			dependenciesByComponent.get(componentId).addAll(dependencies);
		}
	}

	public void flush() {
		if (!tmpComponentsById.isEmpty()) {
			componentsById.putAll(tmpComponentsById);
		}
		tmpComponentsById.clear();
	}

	private List<LongSet> getImportPlan() {
		return new LongTarjan(60000, dependenciesByComponent::get).run(dependenciesByComponent.keySet());
	}
	
	public void doImport(Rf2ImportConfiguration importConfig, BranchContext context) throws Exception {
		final Stopwatch w = Stopwatch.createStarted();
		final String logMessage = isUnpublishedSlice() ? "Importing unpublished components" : String.format("Importing components from %s", effectiveTime);
		final String commitMessage = isUnpublishedSlice() ? "Imported unpublished components" : String.format("Imported components from %s", effectiveTime);
		final boolean createVersions = importConfig.isCreateVersions();
		final String userId = importConfig.getUserId();
		
		LOG.info(logMessage);
		try (Rf2TransactionContext tx = new Rf2TransactionContext(context.service(TransactionContextProvider.class).get(context, userId, null, DatastoreLockContextDescriptions.ROOT), storageKeysByComponent, storageKeysByRefSet, loadOnDemand)) {
			final Iterator<LongSet> importPlan = getImportPlan().iterator();
			while (importPlan.hasNext()) {
				LongSet componentsToImportInBatch = importPlan.next();
				LongIterator it = componentsToImportInBatch.iterator();
				final Collection<SnomedComponent> componentsToImport = newArrayListWithExpectedSize(componentsToImportInBatch.size());
				while (it.hasNext()) {
					long componentToImportL = it.next();
					String componentToImport = Long.toString(componentToImportL);
					final SnomedComponent component = getComponent(componentToImport);
					if (component != null) {
						componentsToImport.add(component);
					}
					// add all members of this component to this batch as well
					final Set<String> containerComponents = membersByContainer.remove(componentToImportL);
					if (containerComponents != null) {
						for (String containedComponentId : containerComponents) {
							SnomedReferenceSetMember containedComponent = getComponent(containedComponentId);
							if (containedComponent != null) {
								componentsToImport.add(containedComponent);
							}
						}
					}
				}
				
				tx.add(componentsToImport, getDependencies(componentsToImport));
				
				if (!isUnpublishedSlice() && createVersions && !importPlan.hasNext()) {
					final CodeSystemVersion version = new CodeSystemVersionBuilder()
							.withDescription("")
							.withEffectiveDate(effectiveDate)
							.withImportDate(new Date())
							.withParentBranchPath(context.branch().path())
							.withVersionId(effectiveTime)
							.build();
					createNewVersion(version, importConfig.getCodeSystemShortName(), context, userId);
					
				}
				
				// TODO consider moving preCommit into commit method
				tx.preCommit();
				tx.commit(userId, commitMessage, DatastoreLockContextDescriptions.ROOT);
			}
			
			if (!isUnpublishedSlice() && createVersions) {
				// purge index
//				PurgeRequest.builder()
//					.setBranchPath(context.branch().path())
//					.setPurge(Purge.LATEST)
//					.build()
//					.execute(context);
				
				// do actually create a branch with the effective time name
				RepositoryRequests
					.branching()
					.prepareCreate()
					.setParent(context.branch().path())
					.setName(effectiveTime)
					.build()
					.execute(context);
			}
		}
		LOG.info(commitMessage + " in " + w);
	}
	
	private void createNewVersion(CodeSystemVersion versionToCreate, String codeSystemShortName, BranchContext context, String userId) {
		final String parentBranch = context.branch().path();
		final String commitMessage = String.format("Created SNOMED CT version %s for branch '%s'", effectiveTime, parentBranch);
		
		try (final SnomedEditingContext codeSystemEditingContext = new SnomedEditingContext(BranchPathUtils.createMainPath())) {
			codeSystemEditingContext.lookup(codeSystemShortName, CodeSystem.class).getCodeSystemVersions().add(versionToCreate);
			if (codeSystemEditingContext.isDirty()) {
				LOG.info(commitMessage);

				new CDOServerCommitBuilder(userId, commitMessage, codeSystemEditingContext.getTransaction())
						.sendCommitNotification(false)
						.parentContextDescription(DatastoreLockContextDescriptions.IMPORT)
						.commit();
			}
		} catch (CommitException e) {
			throw new SnowowlRuntimeException(String.format("Unable to commit SNOMED CT version %s for branch '%s'", effectiveTime, versionToCreate));
		}
	}
	
	private boolean isUnpublishedSlice() {
		return EffectiveTimes.UNSET_EFFECTIVE_TIME_LABEL.equals(effectiveTime);
	}

	private Multimap<Class<? extends CDOObject>, String> getDependencies(Collection<SnomedComponent> componentsToImport) {
		final Multimap<Class<? extends CDOObject>, String> dependenciesByComponent = HashMultimap.create();
		for (SnomedComponent component : componentsToImport) {
			if (component instanceof SnomedCoreComponent) {
				LongSet dependencies = this.dependenciesByComponent.get(Long.parseLong(component.getId()));
				if (dependencies != null) {
					Set<String> requiredDependencies = LongSets.toStringSet(dependencies);
					for (String requiredDependency : requiredDependencies) {
						dependenciesByComponent.put(getCdoType(requiredDependency), requiredDependency);
					}
				}
			} else if (component instanceof SnomedReferenceSetMember) {
				dependenciesByComponent.put(SnomedRefSet.class, ((SnomedReferenceSetMember) component).getReferenceSetId());
			}
		}
		return dependenciesByComponent;
	}

	private Class<? extends CDOObject> getCdoType(String componentId) {
		switch (SnomedIdentifiers.getComponentCategory(componentId)) {
		case CONCEPT: return Concept.class;
		case DESCRIPTION: return Description.class;
		case RELATIONSHIP: return Relationship.class;
		default: throw new UnsupportedOperationException("Cannot determine cdo type from component ID: " + componentId);
		}
	}

}
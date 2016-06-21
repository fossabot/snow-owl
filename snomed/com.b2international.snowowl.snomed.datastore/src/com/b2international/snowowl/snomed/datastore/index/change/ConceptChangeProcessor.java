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
package com.b2international.snowowl.snomed.datastore.index.change;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.newHashMapWithExpectedSize;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.cdo.common.revision.delta.CDOAddFeatureDelta;
import org.eclipse.emf.cdo.common.revision.delta.CDOClearFeatureDelta;
import org.eclipse.emf.cdo.common.revision.delta.CDOContainerFeatureDelta;
import org.eclipse.emf.cdo.common.revision.delta.CDOFeatureDelta;
import org.eclipse.emf.cdo.common.revision.delta.CDOFeatureDeltaVisitor;
import org.eclipse.emf.cdo.common.revision.delta.CDOListFeatureDelta;
import org.eclipse.emf.cdo.common.revision.delta.CDOMoveFeatureDelta;
import org.eclipse.emf.cdo.common.revision.delta.CDORemoveFeatureDelta;
import org.eclipse.emf.cdo.common.revision.delta.CDOSetFeatureDelta;
import org.eclipse.emf.cdo.common.revision.delta.CDOUnsetFeatureDelta;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.b2international.collections.longs.LongCollection;
import com.b2international.collections.longs.LongIterator;
import com.b2international.collections.longs.LongSet;
import com.b2international.commons.Pair;
import com.b2international.commons.collect.LongSets;
import com.b2international.index.Hits;
import com.b2international.index.query.Query;
import com.b2international.index.revision.RevisionSearcher;
import com.b2international.snowowl.core.api.ComponentUtils;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.datastore.ICDOCommitChangeSet;
import com.b2international.snowowl.datastore.cdo.CDOIDUtils;
import com.b2international.snowowl.datastore.cdo.CDOUtils;
import com.b2international.snowowl.datastore.index.ChangeSetProcessorBase;
import com.b2international.snowowl.snomed.Concept;
import com.b2international.snowowl.snomed.Description;
import com.b2international.snowowl.snomed.Relationship;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.SnomedPackage;
import com.b2international.snowowl.snomed.datastore.PredicateUtils.ConstraintDomain;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument.Builder;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedDocument;
import com.b2international.snowowl.snomed.datastore.index.refset.RefSetMemberChange;
import com.b2international.snowowl.snomed.datastore.index.update.IconIdUpdater;
import com.b2international.snowowl.snomed.datastore.index.update.ParentageUpdater;
import com.b2international.snowowl.snomed.datastore.index.update.ReferenceSetMembershipUpdater;
import com.b2international.snowowl.snomed.datastore.taxonomy.ISnomedTaxonomyBuilder;
import com.b2international.snowowl.snomed.datastore.taxonomy.Taxonomy;
import com.b2international.snowowl.snomed.snomedrefset.SnomedLanguageRefSetMember;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSet;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * @since 4.3
 */
public final class ConceptChangeProcessor extends ChangeSetProcessorBase {
	
	private final IconIdUpdater iconId;
	private final ParentageUpdater inferred;
	private final ParentageUpdater stated;
	private final LongSet allConceptIds;
	private final Collection<ConstraintDomain> allConstraintDomains;
	private final Taxonomy statedTaxonomy;
	private final Taxonomy inferredTaxonomy;
	
	private Multimap<String, RefSetMemberChange> referringRefSets;
	private Multimap<String, String> referringPredicates;

	public ConceptChangeProcessor(LongSet allConceptIds, Collection<ConstraintDomain> allConstraintDomains, Collection<String> availableImages, Taxonomy statedTaxonomy, Taxonomy inferredTaxonomy) {
		super("concept changes");
		this.iconId = new IconIdUpdater(inferredTaxonomy.getNewTaxonomy(), statedTaxonomy.getNewTaxonomy(), availableImages);
		this.inferred = new ParentageUpdater(inferredTaxonomy.getNewTaxonomy(), false);
		this.stated = new ParentageUpdater(statedTaxonomy.getNewTaxonomy(), true);
		this.allConceptIds = allConceptIds;
		this.allConstraintDomains = allConstraintDomains;
		this.statedTaxonomy = statedTaxonomy;
		this.inferredTaxonomy = inferredTaxonomy;
	}
	
	@Override
	public void process(ICDOCommitChangeSet commitChangeSet, RevisionSearcher searcher) throws IOException {
		// process concept deletions first
		deleteRevisions(SnomedConceptDocument.class, commitChangeSet.getDetachedComponents(SnomedPackage.Literals.CONCEPT));
		// collect member changes
		this.referringRefSets = HashMultimap.create(new ConceptReferringMemberChangeProcessor().process(commitChangeSet, searcher));
		this.referringPredicates = HashMultimap.create(new ComponentReferringPredicateChangeProcessor(allConceptIds, allConstraintDomains).process(commitChangeSet, searcher));

		// collect new and dirty reference sets
		final Map<String, SnomedRefSet> newAndDirtyRefSetsById = newHashMap(FluentIterable.from(Iterables.concat(commitChangeSet.getNewComponents(), commitChangeSet.getDirtyComponents()))
				.filter(SnomedRefSet.class)
				.uniqueIndex(new Function<SnomedRefSet, String>() {
					@Override
					public String apply(SnomedRefSet input) {
						return input.getIdentifierId();
					}
				}));
		
		// index new concepts
		for (final Concept concept : commitChangeSet.getNewComponents(Concept.class)) {
			final String id = concept.getId();
			final Builder doc = SnomedConceptDocument.builder().id(id);
			update(doc, concept, null);
			SnomedRefSet refSet = newAndDirtyRefSetsById.remove(id);
			if (refSet != null) {
				doc.refSet(refSet);
			}
			indexRevision(concept.cdoID(), doc.build());
		}
		
		// collect dirty concepts for reindex
		final Iterable<Concept> dirtyConcepts = collectDirtyConcepts(searcher, commitChangeSet);
		
		// fetch dirty concept documents
		final Set<Long> dirtyConceptStorageKeys = FluentIterable.from(CDOIDUtils.getIds(dirtyConcepts)).transform(CDOIDUtils.CDO_ID_TO_LONG_FUNCTION).toSet();
		final Map<String, SnomedConceptDocument> currentConceptDocumentsById = Maps.uniqueIndex(searcher.get(SnomedConceptDocument.class, dirtyConceptStorageKeys), ComponentUtils.<String>getIdFunction());
		
		// update dirty concepts
		for (final Concept concept : dirtyConcepts) {
			final String id = concept.getId();
			final SnomedConceptDocument currentDoc = currentConceptDocumentsById.get(id);
			// current doc should exists at this point in time
			final Builder doc = SnomedConceptDocument.builder(currentDoc);
			update(doc, concept, currentDoc);
			SnomedRefSet refSet = newAndDirtyRefSetsById.remove(id);
			if (refSet != null) {
				doc.refSet(refSet);
			}
			indexRevision(concept.cdoID(), doc.build());
		}
		
		// TODO reindex remaining new/changed reference sets
		// TODO process deleted reference sets
	}
	
	/*Updates already existing concept document with changes from concept and other sources*/
	private void update(SnomedConceptDocument.Builder doc, Concept concept, SnomedConceptDocument currentVersion) {
		final String id = concept.getId();

		doc.active(concept.isActive())
			.released(concept.isReleased())
			.effectiveTime(concept.isSetEffectiveTime() ? concept.getEffectiveTime().getTime() : EffectiveTimes.UNSET_EFFECTIVE_TIME)
			.moduleId(concept.getModule().getId())
			.exhaustive(concept.isExhaustive())
			.primitive(concept.isPrimitive())
			.referringPredicates(referringPredicates.removeAll(id))
	//		.relevant() // TODO register change type
			;
		
		iconId.update(id, concept.isActive(), doc);
		inferred.update(id, doc);
		stated.update(id, doc);
		
		final Collection<String> currentReferringRefSets = currentVersion == null ? Collections.<String>emptySet() : currentVersion.getReferringRefSets();
		final Collection<String> currentReferringMappingRefSets = currentVersion == null ? Collections.<String>emptySet() : currentVersion.getReferringMappingRefSets();
		new ReferenceSetMembershipUpdater(referringRefSets.removeAll(id), currentReferringRefSets, currentReferringMappingRefSets).update(doc);
	}

	private Iterable<Concept> collectDirtyConcepts(final RevisionSearcher searcher, final ICDOCommitChangeSet commitChangeSet) throws IOException {
		// collect relevant concept changes
		final Set<Concept> dirtyConcepts = newHashSet(FluentIterable.from(commitChangeSet.getDirtyComponents(Concept.class)).filter(new Predicate<Concept>() {
			@Override
			public boolean apply(Concept input) {
				final DirtyConceptFeatureDeltaVisitor visitor = new DirtyConceptFeatureDeltaVisitor();
				commitChangeSet.getRevisionDeltas().get(input.cdoID()).accept(visitor);
				return visitor.hasAllowedChanges();
			}
		}));
		// collect description concepts for compare change
		for (final Description description : commitChangeSet.getDirtyComponents(Description.class)) {
			dirtyConcepts.add(description.getConcept());
		}
		// collection relationship source concepts for compare change
		for (final Relationship relationship : commitChangeSet.getDirtyComponents(Relationship.class)) {
			dirtyConcepts.add(relationship.getSource());
		}
		// collect preferred language member's description's concept for compare change
		// do we need this??? why not query all changed revision and all other required docs for compare
		final Set<Concept> newConcepts = newHashSet(commitChangeSet.getNewComponents(Concept.class));
		for (SnomedLanguageRefSetMember member : commitChangeSet.getNewComponents(SnomedLanguageRefSetMember.class)) {
			if (Concepts.REFSET_DESCRIPTION_ACCEPTABILITY_PREFERRED.equals(member.getAcceptabilityId()) && member.eContainer() instanceof Description) {
				final Description description = (Description) member.eContainer();
				if (!Concepts.FULLY_SPECIFIED_NAME.equals(description.getType().getId())) {
					// add only if the concept is not new
					if (!newConcepts.contains(description.getConcept())) {
						dirtyConcepts.add(description.getConcept());
					}
				}
			}
		}

		final Collection<String> currentDirtyConceptIds = FluentIterable.from(Iterables.concat(dirtyConcepts, commitChangeSet.getNewComponents(Concept.class))).transform(new Function<Concept, String>() {
			@Override
			public String apply(Concept input) {
				return input.getId();
			}
		}).toSet();
		
		// collect dirty concepts due to change in hierarchy
		final Set<String> conceptsToBeLoaded = newHashSet();
		conceptsToBeLoaded.addAll(referringPredicates.keySet());
		conceptsToBeLoaded.addAll(referringRefSets.keySet());
		conceptsToBeLoaded.addAll(getAffectedConcepts(searcher, commitChangeSet, inferredTaxonomy));
		conceptsToBeLoaded.addAll(getAffectedConcepts(searcher, commitChangeSet, statedTaxonomy));
		
		// collect inferred taxonomy changes
		conceptsToBeLoaded.addAll(registerConceptAndDescendants(inferredTaxonomy.getDifference().getA(), inferredTaxonomy.getNewTaxonomy()));
		conceptsToBeLoaded.addAll(registerConceptAndDescendants(inferredTaxonomy.getDifference().getB(), inferredTaxonomy.getOldTaxonomy()));
		// collect stated taxonomy changes
		conceptsToBeLoaded.addAll(registerConceptAndDescendants(statedTaxonomy.getDifference().getA(), statedTaxonomy.getNewTaxonomy()));
		conceptsToBeLoaded.addAll(registerConceptAndDescendants(statedTaxonomy.getDifference().getB(), statedTaxonomy.getOldTaxonomy()));
		conceptsToBeLoaded.removeAll(currentDirtyConceptIds);
		
		final Query<SnomedConceptDocument> query = Query.builder(SnomedConceptDocument.class)
				.selectAll()
				.where(SnomedConceptDocument.Expressions.ids(conceptsToBeLoaded))
				.limit(conceptsToBeLoaded.size())
				.build();
		final Hits<SnomedConceptDocument> hits = searcher.search(query);
		
		for (SnomedConceptDocument hit : hits) {
			final Concept concept = CDOUtils.getObjectIfExists(commitChangeSet.getView(), hit.getStorageKey());
			checkNotNull(concept, "Concept should not be null");
			dirtyConcepts.add(concept);
		}
		
		return dirtyConcepts;
	}
	
	private Set<String> registerConceptAndDescendants(LongCollection relationshipIds, ISnomedTaxonomyBuilder taxonomy) {
		final Set<String> ids = newHashSet();
		final LongIterator relationshipIdIterator = relationshipIds.iterator();
		while (relationshipIdIterator.hasNext()) {
			String relationshipId = Long.toString(relationshipIdIterator.next());
			String conceptId = taxonomy.getSourceNodeId(relationshipId);
			ids.add(conceptId);
			ids.addAll(LongSets.toStringSet(taxonomy.getAllDescendantNodeIds(conceptId)));
		}
		return ids;
	}

	private Collection<String> getAffectedConcepts(RevisionSearcher searcher, ICDOCommitChangeSet commitChangeSet, Taxonomy taxonomy) throws IOException {
		final Set<String> iconIdUpdates = newHashSet();
		final ISnomedTaxonomyBuilder newTaxonomy = taxonomy.getNewTaxonomy();
		final ISnomedTaxonomyBuilder oldTaxonomy = taxonomy.getOldTaxonomy();
		final Pair<LongSet, LongSet> diff = taxonomy.getDifference();
		// process new/reactivated relationships
		final LongIterator it = diff.getA().iterator();
		while (it.hasNext()) {
			final String relationshipId = Long.toString(it.next());
			final String sourceNodeId = newTaxonomy.getSourceNodeId(relationshipId);
			iconIdUpdates.add(sourceNodeId);
			// add all descendants
			iconIdUpdates.addAll(LongSets.toStringSet(newTaxonomy.getAllDescendantNodeIds(sourceNodeId)));
		}
		
		// process detached/inactivated relationships
		final LongIterator detachedIt = diff.getB().iterator();
		final Map<String, String> oldSourceConceptIconIds = getSourceConceptIconIds(searcher, oldTaxonomy, diff.getB());
		while (detachedIt.hasNext()) {
			final String relationshipId = Long.toString(detachedIt.next());
			final String sourceNodeId = oldTaxonomy.getSourceNodeId(relationshipId);
			// if concept still exists a relationship became inactive or deleted
			if (newTaxonomy.containsNode(sourceNodeId)) {
				final LongSet allAncestorNodeIds = newTaxonomy.getAllAncestorNodeIds(sourceNodeId);
				final String oldIconId = oldSourceConceptIconIds.get(sourceNodeId);
				if (!allAncestorNodeIds.contains(Long.parseLong(oldIconId))) {
					iconIdUpdates.add(sourceNodeId);
					// add all descendants
					iconIdUpdates.addAll(LongSets.toStringSet(newTaxonomy.getAllDescendantNodeIds(sourceNodeId)));
				}
			} else {
				iconIdUpdates.add(sourceNodeId);
				iconIdUpdates.addAll(LongSets.toStringSet(oldTaxonomy.getAllDescendantNodeIds(sourceNodeId)));
			}
		}
		
		return FluentIterable.from(commitChangeSet.getNewComponents(Concept.class)).transform(new Function<Concept, String>() {
			@Override
			public String apply(Concept input) {
				return input.getId();
			}
		}).copyInto(iconIdUpdates);
	}
	
	private Map<String, String> getSourceConceptIconIds(RevisionSearcher searcher, ISnomedTaxonomyBuilder oldTaxonomy, LongSet detachedRelationshipIds) throws IOException {
		final LongIterator it = detachedRelationshipIds.iterator();
		final Collection<String> sourceNodeIds = newHashSetWithExpectedSize(detachedRelationshipIds.size());
		while (it.hasNext()) {
			final String relationshipId = Long.toString(it.next());
			sourceNodeIds.add(oldTaxonomy.getSourceNodeId(relationshipId)); 
		}
		
		final Query<SnomedConceptDocument> query = Query.builder(SnomedConceptDocument.class)
				.selectAll()
				.where(SnomedDocument.Expressions.ids(sourceNodeIds))
				.limit(sourceNodeIds.size())
				.build();
		final Hits<SnomedConceptDocument> hits = searcher.search(query);
		final Map<String, String> iconsByIds = newHashMapWithExpectedSize(hits.getTotal());
		for (SnomedConceptDocument hit : hits) {
			iconsByIds.put(hit.getId(), hit.getIconId());
		}
		return iconsByIds;
 	}

	/**
	 * @since 4.3
	 */
	private static class DirtyConceptFeatureDeltaVisitor implements CDOFeatureDeltaVisitor {
		
		private static final Set<EStructuralFeature> ALLOWED_CONCEPT_CHANGE_FEATURES = ImmutableSet.<EStructuralFeature>builder()
				.add(SnomedPackage.Literals.COMPONENT__ACTIVE)
				.add(SnomedPackage.Literals.COMPONENT__EFFECTIVE_TIME)
				.add(SnomedPackage.Literals.COMPONENT__RELEASED)
				.add(SnomedPackage.Literals.COMPONENT__MODULE)
				.add(SnomedPackage.Literals.CONCEPT__DEFINITION_STATUS)
				.add(SnomedPackage.Literals.CONCEPT__EXHAUSTIVE)
				.add(SnomedPackage.Literals.CONCEPT__DESCRIPTIONS)
				.add(SnomedPackage.Literals.CONCEPT__OUTBOUND_RELATIONSHIPS)
				.build();
		private boolean hasAllowedChanges;

		@Override
		public void visit(CDOSetFeatureDelta delta) {
			visitDelta(delta);
		}
		
		@Override
		public void visit(CDOListFeatureDelta delta) {
			visitDelta(delta);
		}
		
		@Override
		public void visit(CDOAddFeatureDelta delta) {
			visitDelta(delta);
		}
		
		@Override
		public void visit(CDOClearFeatureDelta delta) {
			visitDelta(delta);
		}
		
		@Override
		public void visit(CDOMoveFeatureDelta delta) {
			visitDelta(delta);
		}
		
		@Override
		public void visit(CDORemoveFeatureDelta delta) {
			visitDelta(delta);
		}
		
		@Override
		public void visit(CDOUnsetFeatureDelta delta) {
			visitDelta(delta);
		}
		
		@Override
		public void visit(CDOContainerFeatureDelta delta) {
			visitDelta(delta);
		}
		
		private void visitDelta(CDOFeatureDelta delta) {
			hasAllowedChanges |= ALLOWED_CONCEPT_CHANGE_FEATURES.contains(delta.getFeature());
		}

		public boolean hasAllowedChanges() {
			return hasAllowedChanges;
		}
		
	}

}

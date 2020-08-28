package org.ktu.isd.bpmn2sbvr.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ConceptExtractionEntry implements Cloneable {
    private SourceEntry source;
    private List<SBVRExpressionModel> candidates;
    private List<Boolean> includedInTestCase;
    private boolean isMerged;
    private Map<SourceEntry, SBVRExpressionModel> mergedWith;

    public ConceptExtractionEntry(List<Object> source, List<String> sourceText) {
        this(new SourceEntry(source, sourceText));
    }
/*
    public ConceptExtractionEntry(List<String> sourceText) {
        this(new SourceEntry(sourceText));
    }
*/
    public ConceptExtractionEntry(List<Object> sourceText) {
        this(new SourceEntry(sourceText));
    }

    public ConceptExtractionEntry(SourceEntry source) {
        this.source = source;
        this.candidates = new ArrayList<>();
        this.includedInTestCase = new ArrayList<>();
        isMerged = false;
        this.mergedWith = new HashMap<>();
    }

    private ConceptExtractionEntry() {
    }

    public SourceEntry getSource() {
        return source;
    }

    public void setSource(SourceEntry source) {
        this.source = source;
    }

    public List<SBVRExpressionModel> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    public List<SBVRExpressionModel> getTestCaseSelectedCandidates() {
        List<SBVRExpressionModel> selected = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++)
            if (includedInTestCase.get(i))
                selected.add(candidates.get(i));
        return selected;
    }

    public boolean addCandidate(SBVRExpressionModel candidate, boolean includeInTestCase) {
        if (!candidates.isEmpty())
            for (SBVRExpressionModel sbvr : candidates)
                if (sbvr.originalEqualsTo(candidate))
                    return false;
        this.candidates.add(candidate);
        this.includedInTestCase.add(includeInTestCase);
        return true;
    }

    public boolean addCandidate(SBVRExpressionModel candidate) {
        return addCandidate(candidate, true);
    }

    public void removeCandidate(SBVRExpressionModel candidate) {
        candidates.remove(candidate);
    }

    public void removeAll() {
        candidates.clear();
    }

    public SBVRExpressionModel getCandidate(int index) {
        return candidates.get(index);
    }

    public void setCandidate(int index, SBVRExpressionModel candidate) {
        candidates.set(index, candidate);
    }

    public boolean isMerged() {
        return isMerged;
    }

    public void setMerged(boolean isMerged) {
        this.isMerged = isMerged;
    }

    public Map<SourceEntry, SBVRExpressionModel> getMergedWith() {
        return Collections.unmodifiableMap(mergedWith);
    }

    public void addMerge(SourceEntry source, SBVRExpressionModel sbvr) {
        this.mergedWith.put(source, sbvr);
    }

    public ConceptExtractionEntry clone() {
        ConceptExtractionEntry copy = new ConceptExtractionEntry();
        copy.source = source.clone();
        copy.isMerged = isMerged;
        copy.candidates = new ArrayList<>();
        Collections.copy(candidates, copy.candidates);
        copy.mergedWith = new HashMap<>();
        for (SourceEntry entry : mergedWith.keySet())
            copy.mergedWith.put(entry.clone(), mergedWith.get(entry).clone());
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConceptExtractionEntry))
            return false;
        if (obj == this)
            return true;
        ConceptExtractionEntry other = (ConceptExtractionEntry) obj;
        return source.equals(other.source) && this.candidates.equals(other.candidates);
    }


}

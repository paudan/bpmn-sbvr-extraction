package org.ktu.isd.bpmn2sbvr.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractConceptModel implements Cloneable {

    protected Map<SourceEntry, ConceptExtractionEntry> data;
    // Decision whether particular combinations of element rumblings can be used for manual extraction
    protected Map<SourceEntry, Boolean> flag;

    public AbstractConceptModel() {
        data = new HashMap<>();
        flag = new HashMap<>();
    }

    public boolean add(SourceEntry source, SBVRExpressionModel candidate, boolean includeInTestCase) {
        if (source == null)
            return false;
        ConceptExtractionEntry srcEntry = data.get(source);
        if (srcEntry == null) {
            srcEntry = new ConceptExtractionEntry(source);
            data.put(source, srcEntry);
        }
        srcEntry.addCandidate(candidate, includeInTestCase);
        return true;
    }

    public boolean add(SourceEntry source, SBVRExpressionModel candidate) {
        return add(source, candidate, true);
    }

    public boolean remove(SourceEntry source, SBVRExpressionModel candidate) {
        if (source == null || data.get(source) == null)
            return false;
        data.get(source).removeCandidate(candidate);
        return true;
    }

    public void removeAll() {
        for (SourceEntry source : data.keySet())
            data.get(source).removeAll();
        data.clear();
        flag.clear();
    }

    public void set(SourceEntry source, int index, SBVRExpressionModel candidate) {
        if (source == null || data.get(source) == null)
            return;
        data.get(source).setCandidate(index, candidate);
    }

    public SBVRExpressionModel get(SourceEntry source, int index) {
        if (source == null || data.get(source) == null)
            return null;
        return data.get(source).getCandidate(index);
    }

    public int indexOf(SourceEntry source, String candidate) {
        if (data == null || data.isEmpty())
            return -1;
        List<SBVRExpressionModel> res = data.get(source).getCandidates();
        if (res == null)
            return -1;
        for (int i = 0; i < res.size(); i++)
            if (candidate.compareTo(res.get(i).toString()) == 0)
                return i;
        return -1;
    }

    public int indexOf(SourceEntry source, SBVRExpressionModel model) {
        if (source == null || data.isEmpty() || data.get(source) == null)
            return -1;
        return data.get(source).getCandidates().indexOf(model);
    }

    public abstract Set<String> getConceptsListHTML();

    public abstract Set<String> getConceptsListText();

    public abstract Map<String, SBVRExpressionModel> getListMap();

    public int size() {
        return data.size();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public Map<SourceEntry, ConceptExtractionEntry> getDataset() {
        return data;
    }

    public boolean manualExtractionPossible(SourceEntry source) {
        return flag.get(source) != null ? flag.get(source) : false;
    }

    public Set<SourceEntry> manualExtractionCandidates() {
        Set<SourceEntry> list = new HashSet<>();
        for (SourceEntry concepts : flag.keySet())
            if (flag.get(concepts))
                list.add(concepts);
        return list;
    }

    public void setManualExtraction(SourceEntry source) {
        if (source == null || source.getSourceNames().isEmpty())
            return;
        flag.put(source, Boolean.TRUE);
    }

    public void setAutomaticExtraction(SourceEntry source) {
        if (source == null || source.getSourceNames().isEmpty())
            return;
        flag.put(source, Boolean.FALSE);
    }

    public void setAllIdentified(Boolean value) {
        for (SourceEntry concept : data.keySet())
            if (data.get(concept) != null)
                for (SBVRExpressionModel sbvr : data.get(concept).getCandidates())
                    if (value.equals(Boolean.TRUE))
                        sbvr.setIdentified(value);
                    else if (value.equals(Boolean.FALSE) && sbvr.isAuto())
                        sbvr.setIdentified(value);
    }

    protected AbstractConceptModel copyInstance(AbstractConceptModel copy) {
        copy.data = new HashMap<>();
        for (SourceEntry entry: data.keySet()) {
            ConceptExtractionEntry extr = data.get(entry).clone();
            copy.data.put(extr.getSource(), extr);
        }
        copy.flag = new HashMap<>();
        for (SourceEntry concept : flag.keySet())
            copy.flag.put(concept, flag.get(concept));
        return copy;
    }

}

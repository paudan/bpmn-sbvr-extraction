package org.ktu.isd.bpmn2sbvr.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FilteredConceptModel extends AbstractConceptModel {

    private Map<SourceEntry, List<Boolean>> selected, traceOpt;

    public FilteredConceptModel() {
        super();
        selected = new HashMap<>();
        traceOpt = new HashMap<>();
    }

    public void setSelectedState(SourceEntry source, SBVRExpressionModel model, Boolean state) {
        selected.get(source).set(data.get(source).getCandidates().indexOf(model), state);
    }

    public Boolean isSelected(SourceEntry source, SBVRExpressionModel model) {
        return selected.get(source).get(data.get(source).getCandidates().indexOf(model));
    }
    
    public void setCreateTrace(SourceEntry source, SBVRExpressionModel model, Boolean state) {
        traceOpt.get(source).set(data.get(source).getCandidates().indexOf(model), state);
    }

    public Boolean isCreateTrace(SourceEntry source, SBVRExpressionModel model) {
        if (traceOpt.get(source) == null)
            return null;
        return traceOpt.get(source).get(data.get(source).getCandidates().indexOf(model));
    }

    @Override
    public boolean add(SourceEntry source, SBVRExpressionModel candidate, boolean includeInTestCase) {
        boolean added = super.add(source, candidate, includeInTestCase);
        if (added) {
            List<Boolean> res = selected.get(source);
            if (res == null) {
                res = new ArrayList<>();
                selected.put(source, res);
            }
            res.add(Boolean.TRUE);
            List<Boolean> trace = traceOpt.get(source);
            if (trace == null) {
                trace = new ArrayList<>();
                traceOpt.put(source, trace);
            }
            trace.add(Boolean.FALSE);
        }
        return added;
    }

    @Override
    public boolean remove(SourceEntry source, SBVRExpressionModel candidate) {
        super.remove(source, candidate);
        List<Boolean> res = selected.get(source);
        if (res != null)
            res.remove(data.get(source).getCandidates().indexOf(candidate));
        super.remove(source, candidate);
        return true;
    }

    @Override
    public void removeAll() {
        super.removeAll();
        for (SourceEntry concepts : selected.keySet())
            selected.get(concepts).clear();
        selected.clear();
    }

    @Override
    public Set<String> getConceptsListHTML() {
        Set<String> candidates = new HashSet<>();
        for (SourceEntry concept : data.keySet())
            if (data.get(concept) != null) {
                List<SBVRExpressionModel> sbvrList = data.get(concept).getCandidates();
                for (int i = 0; i < sbvrList.size(); i++)
                    if (selected.get(concept).get(i))
                        candidates.add(sbvrList.get(i).toHTMLString(true, null));
            }
        return candidates;
    }

    @Override
    public Set<String> getConceptsListText() {
        Set<String> candidates = new HashSet<>();
        for (SourceEntry concept : data.keySet())
            if (data.get(concept) != null) {
                List<SBVRExpressionModel> sbvrList = data.get(concept).getCandidates();
                for (int i = 0; i < sbvrList.size(); i++)
                    if (selected.get(concept).get(i))
                        candidates.add(sbvrList.get(i).toString());
            }
        return candidates;
    }

    @Override
    public FilteredConceptModel clone() {
        FilteredConceptModel copy = new FilteredConceptModel();
        copy = (FilteredConceptModel) super.copyInstance(copy);
        copy.selected = cloneBool(selected);
        copy.traceOpt = cloneBool(traceOpt);
        return copy;
    }
    
    private Map<SourceEntry, List<Boolean>> cloneBool(Map<SourceEntry, List<Boolean>> map) {
        Map<SourceEntry, List<Boolean>> copy = new HashMap<>();
        for (SourceEntry concept : map.keySet()) {
            List<Boolean> bool = new ArrayList<>();
            List<SBVRExpressionModel> sbvrList = data.get(concept).getCandidates();
            for (int i = 0; i < sbvrList.size(); i++)
                bool.add(map.get(concept).get(i));
            copy.put(concept, bool);
        }
        return copy;
    }

    @Override
    public HashMap<String, SBVRExpressionModel> getListMap() {
        HashMap<String, SBVRExpressionModel> map = new HashMap<>();
        for (SourceEntry concept : data.keySet())
            if (data.get(concept) != null) {
                List<SBVRExpressionModel> sbvrList = data.get(concept).getCandidates();
                for (int i = 0; i < sbvrList.size(); i++)
                    if (selected.get(concept).get(i))
                        map.put(sbvrList.get(i).toString(), sbvrList.get(i));
            }
        return map;
    }

}

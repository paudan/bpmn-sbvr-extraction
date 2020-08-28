package org.ktu.isd.bpmn2sbvr.extract;

import java.io.File;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.ktu.isd.bpmn2sbvr.models.AbstractConceptModel;
import org.ktu.isd.bpmn2sbvr.models.DefaultConceptModel;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel;
import org.ktu.isd.bpmn2sbvr.models.SourceEntry;


public abstract class AbstractSbvrExtractor {

    protected File file;
    protected AbstractConceptModel gc_candidates, vc_candidates, br_candidates;
    protected Map<String, SBVRExpressionModel> metamodelVocabulary;
    protected boolean strictOnly, extractMMVoc;
    protected boolean extractedStrict, extractedAuto;
    protected Collection<ModelElementInstance> candidateElements;
    protected Map<String, SimpleImmutableEntry<String, SBVRExpressionModel>> gcReplacements, vcReplacements;

    protected AbstractSbvrExtractor(File file, boolean strictOnly, boolean extractMMVoc) {
        this.file = file;
        this.strictOnly = strictOnly;
        this.extractMMVoc = extractMMVoc;
        init();
    }

    protected AbstractSbvrExtractor(File file) {
        this(file, false, false);
    }

    private void init() {
        gc_candidates = new DefaultConceptModel();
        vc_candidates = new DefaultConceptModel();
        br_candidates = new DefaultConceptModel();
        extractedStrict = false;
        extractedAuto = false;
        gcReplacements = new HashMap<>();
        vcReplacements = new HashMap<>();
        metamodelVocabulary = new HashMap<>();
        candidateElements = new HashSet<>();
        String[] mmNames = getMetamodelVocabularyNames();
        if (mmNames != null && mmNames.length > 0)
            for (String name : mmNames)
                metamodelVocabulary.put(name, createModelVocabularyConcept(name));
    }

    protected abstract void extractGeneralConceptCandidates();

    protected void extractModelVocabularyCandidates() {
        if (extractedAuto)
            return;
        extractModelVocabulary();
    }

    protected abstract void extractVerbConceptCandidates();

    protected abstract void extractBusinessRuleCandidates();

    protected abstract void extractModelVocabulary();

    public abstract String[] getMetamodelVocabularyNames();

    public static String getProperName(ModelElementInstance el) {
        String name = el.getAttributeValue("name");
        if (name == null || name.trim().length() == 0)
            return null;
        return name.replaceAll("\n", " ").replaceAll("  ", " ").trim();
    }

    public static String getProperName(String name) {
        if (name == null)
            return name;
        if (name.trim().length() == 0)
            return null;
        return name.replaceAll("\n", " ").replaceAll("  ", " ").trim();
    }


    public void createGeneralConceptCandidates() {
        if (gc_candidates == null)
            gc_candidates = new DefaultConceptModel();
        else
            gc_candidates.removeAll();
        extractGeneralConceptCandidates();
    }

    public void createVerbConceptCandidates() {
        if (gc_candidates == null)
            createGeneralConceptCandidates();
        if (vc_candidates == null)
            vc_candidates = new DefaultConceptModel();
        else
            vc_candidates.removeAll();
        extractVerbConceptCandidates();
    }

    public void createBusinessRuleCandidates() {
        if (gc_candidates == null)
            extractGeneralConceptCandidates();
        if (vc_candidates == null)
            createVerbConceptCandidates();
        if (br_candidates == null)
            br_candidates = new DefaultConceptModel();
        else
            br_candidates.removeAll();
        extractBusinessRuleCandidates();
    }

    public void createModelVocabularyCandidates() {
        if (gc_candidates == null)
            gc_candidates = new DefaultConceptModel();
        if (vc_candidates == null)
            vc_candidates = new DefaultConceptModel();
        extractModelVocabularyCandidates();
    }

    public static String extractElementText(ModelElementInstance el) {
        if (el == null)
            return null;
        String name = el.getAttributeValue("name");
        if (name == null || name.trim().length() == 0)
            return null;
        return name.trim();
    }

    public void removeAll() {
        gc_candidates.removeAll();
        vc_candidates.removeAll();
        br_candidates.removeAll();
    }

    public void extractAll() {
        createGeneralConceptCandidates();
        createVerbConceptCandidates();
        createBusinessRuleCandidates();
        if (extractMMVoc)
            createModelVocabularyCandidates();
    }

    public AbstractConceptModel getGCCandidateModel() {
        return gc_candidates;
    }

    public void setGCCandidateModel(AbstractConceptModel gc_candidates) {
        this.gc_candidates = gc_candidates;
    }

    public AbstractConceptModel getVCCandidateModel() {
        return vc_candidates;
    }

    public void setVCCandidateModel(AbstractConceptModel vc_candidates) {
        this.vc_candidates = vc_candidates;
    }

    public AbstractConceptModel getBRCandidateModel() {
        return br_candidates;
    }

    public void setBRCandidateModel(AbstractConceptModel br_candidates) {
        this.br_candidates = br_candidates;
    }

    public boolean isStrictOnly() {
        return strictOnly;
    }

    public void setStrictOnly(boolean strictOnly) {
        this.strictOnly = strictOnly;
    }

    public boolean isExtractModelVocabulary() {
        return extractMMVoc;
    }

    public void setExtractModelVocabulary(boolean extractMMVoc) {
        this.extractMMVoc = extractMMVoc;
    }

    public boolean isExtractedStrict() {
        return extractedStrict;
    }

    public void setExtractedStrict(boolean extractedStrict) {
        this.extractedStrict = extractedStrict;
    }

    public boolean isExtractedAuto() {
        return extractedAuto;
    }

    public void setExtractedAuto(boolean extractedAuto) {
        this.extractedAuto = extractedAuto;
    }

    public Map<String, SimpleImmutableEntry<String, SBVRExpressionModel>> getGCReplacements() {
        return gcReplacements;
    }

    public Map<String, SimpleImmutableEntry<String, SBVRExpressionModel>> getVCReplacements() {
        return vcReplacements;
    }

    public Collection<ModelElementInstance> getExtractedDiagramElements() {
        return Collections.unmodifiableCollection(candidateElements);
    }

    private String containsCandidate(String text, AbstractConceptModel candidates,
                                     Map<String, SimpleImmutableEntry<String, SBVRExpressionModel>> replacements) {
        Set<String> gclist = candidates.getListMap().keySet();
        for (String gc : gclist)
            if (text.contains(gc))
                return gc;
        for (String gc : replacements.keySet())
            if (text.contains(gc))
                return gc;
        return null;
    }

    private SBVRExpressionModel getConcept(String cand, AbstractConceptModel candidates,
            Map<String, SimpleImmutableEntry<String, SBVRExpressionModel>> replacements) {
        Map<String, SBVRExpressionModel> map = candidates.getListMap();
        Set<String> gclist = map.keySet();
        if (cand == null)
            return null;
        if (!gclist.contains(cand) && replacements.containsKey(cand))
            return replacements.get(cand).getValue();
        else if (gclist.contains(cand))
            return map.get(cand);
        return null;
    }

    protected SBVRExpressionModel getGeneralConcept(String cand) {
        return getConcept(cand, gc_candidates, gcReplacements);
    }

    protected String containsGCCandidate(String text) {
        return containsCandidate(text, gc_candidates, gcReplacements);
    }

    protected SBVRExpressionModel getVerbConcept(String cand) {
        return getConcept(cand, vc_candidates, vcReplacements);
    }

    protected String containsVCCandidate(String text) {
        return containsCandidate(text, vc_candidates, vcReplacements);
    }

    public abstract String removeMetaconceptName(String name);

    protected SBVRExpressionModel createModelVocabularyConcept(String name) {
        SBVRExpressionModel candidate = new SBVRExpressionModel();
        candidate.addGeneralConcept(name, false);
        candidate.setAuto(true);
        candidate.setModelVocabularyConcept(true);
        return candidate;
    }

    protected SBVRExpressionModel createGeneralConcept(ModelElementInstance el, String name, boolean setAuto, boolean includeInTestCase) {
        if (name == null)
            return null;
        SBVRExpressionModel candidate = new SBVRExpressionModel();
        candidate.addGeneralConcept(name, false);
        candidate.setAuto(true);
        SourceEntry source = new SourceEntry(Collections.singletonList(el));
        gc_candidates.add(source, candidate, includeInTestCase);
        if (setAuto) {
            gc_candidates.setAutomaticExtraction(source);
        } else {
            gc_candidates.setManualExtraction(source);
        }
        return candidate;
    }

    /**
     * Extract verb-concept from action-like element (UseCase, Task, etc.).
     * Here, it is assumed that phrase starts with verb ("extract text")
     */
    protected String extractActionVC(ModelElementInstance el) {
        String name = extractElementText(el);
        if (name == null || name.length() == 0)
            return null;
        String[] parts = name.split(" ");
        if (parts.length < 1)
            return null;
        return (parts[0].trim().length() > 0 ? parts[0].trim().toLowerCase() : null);
    }

    protected String extractActionGC(ModelElementInstance el) {
        String proper = extractElementText(el);
        if (proper == null || proper.length() == 0)
            return null;
        String[] parts = proper.split(" ");
        if (parts.length < 2)
            return null;
        String name = "";
        for (int i = 1; i < parts.length; i++)
            name += parts[i] + " ";
        return name.trim();
    }

    /**
     * Extract noun-phrase from action-like element, modelled like condition (Event, etc.).
     * Here, it is assumed that phrase starts with noun and is passive voice-like ("text is extracted")
     */
    protected String extractActionUnaryLikeGC(ModelElementInstance el) {
        return extractActionVC(el);
    }

    protected String extractActionUnaryLikeVC(ModelElementInstance el) {
        return extractActionGC(el);
    }

    protected void createVerbConceptFromAction(ModelElementInstance actor, ModelElementInstance action) {
        Map<String, SBVRExpressionModel> map = gc_candidates.getListMap();
        Set<String> gclist = map.keySet();
        String uc = extractActionGC(action);
        String bname = extractElementText(actor);
        String vname = extractActionVC(action);
        if (bname != null && vname != null && gclist.contains(bname)) {
            SBVRExpressionModel first = getGeneralConcept(bname);
            if (first == null)
                return;
            SBVRExpressionModel candidate = new SBVRExpressionModel();
            candidate.addIdentifiedExpression(first).addVerbConcept(vname, false);
            if (uc != null) {
                SBVRExpressionModel second = getGeneralConcept(uc);
                if (second != null)
                    candidate.addIdentifiedExpression(second);
                else
                    candidate.addUnidentifiedText(uc);
            }
            candidate.setAuto(true);
            SourceEntry source = new SourceEntry(new ArrayList<Object>(Arrays.asList(actor, action)));
            vc_candidates.add(source, candidate);
            vc_candidates.setAutomaticExtraction(source);
        }
    }

    protected void createVerbConceptFromCondition(ModelElementInstance condEl, String condition) {
        if (condition == null)
            return;
        List<String> idgcs = new ArrayList<>();
        String gcand = containsGCCandidate(condition);
        if (gcand != null)
            idgcs.add(gcand);
        if (idgcs.size() > 0 && (!extractedAuto || (extractedAuto && extractedStrict))) {
            SBVRExpressionModel candidate = new SBVRExpressionModel();
            if (idgcs.size() == 1) {
                String gc = idgcs.get(0);
                SBVRExpressionModel gcExpr = getGeneralConcept(gc);
                if (condition.startsWith(gc) && gcExpr != null)
                    candidate.addIdentifiedExpression(gcExpr)
                            .addUnidentifiedText(condition.substring(gc.length()).trim());
                else if (condition.endsWith(gc) && gcExpr != null)
                    candidate.addUnidentifiedText(condition.substring(0, condition.length() - gc.length()))
                            .addIdentifiedExpression(gcExpr);
            } else if (idgcs.size() == 2) {
                String gc1 = idgcs.get(0);
                String gc2 = idgcs.get(1);
                SBVRExpressionModel gcExpr1 = getGeneralConcept(gc1);
                SBVRExpressionModel gcExpr2 = getGeneralConcept(gc2);
                if (condition.startsWith(gc1) && condition.endsWith(gc2) && gcExpr1 != null && gcExpr2 != null
                        && condition.length() - gc2.length() > gc1.length() + 1)
                    candidate.addIdentifiedExpression(gcExpr1)
                            .addUnidentifiedText(condition.substring(gc1.length(), condition.length() - gc2.length()))
                            .addIdentifiedExpression(gcExpr2);
                else if (condition.startsWith(gc2) && condition.endsWith(gc1) && gcExpr1 != null && gcExpr2 != null
                        && condition.length() - gc1.length() > gc2.length() + 1)
                    candidate.addIdentifiedExpression(gcExpr2)
                            .addUnidentifiedText(condition.substring(gc2.length() + 1, condition.length() - gc1.length()))
                            .addIdentifiedExpression(gcExpr1);
            }
            vc_candidates.add(new SourceEntry(Collections.singletonList(condEl)), candidate);
        }
    }

    protected void createUnaryVerbConcept(ModelElementInstance subject, ModelElementInstance characteristic) {
        String subj = extractElementText(subject);
        String char_ = extractElementText(characteristic);
        if (subj == null || char_ == null)
            return;
        SBVRExpressionModel first = getGeneralConcept(subj);
        if (first == null)
            return;
        SBVRExpressionModel candidate = new SBVRExpressionModel();
        candidate.addIdentifiedExpression(first).addVerbConcept("is " + char_, true);
        candidate.setAuto(true);
        SourceEntry source = new SourceEntry(new ArrayList<Object>(Arrays.asList(subject, characteristic)));
        vc_candidates.add(source, candidate);
        vc_candidates.setAutomaticExtraction(source);
    }

    protected void createUnaryVerbConcept(ModelElementInstance subject, String verb, String subjectOverride) {
        String subj = subjectOverride == null ? extractElementText(subject) : subjectOverride;
        if (subj == null || verb == null)
            return;
        SBVRExpressionModel first = getGeneralConcept(subj);
        if (first == null)
            return;
        SBVRExpressionModel candidate = new SBVRExpressionModel();
        candidate.addIdentifiedExpression(first).addVerbConcept(verb, true);
        candidate.setAuto(true);
        SourceEntry source = new SourceEntry(Collections.singletonList(subject));
        vc_candidates.add(source, candidate);
        vc_candidates.setAutomaticExtraction(source);
    }

    protected SBVRExpressionModel addGeneralConcept(SBVRExpressionModel candidate, String objText) {
        /*if (objText == null)
            return null;*/
        SBVRExpressionModel objConcept = getGeneralConcept(objText);
        return objConcept != null ? candidate.addIdentifiedExpression(objConcept) : candidate.addUnidentifiedText(objText);
    }

    protected SBVRExpressionModel addGeneralConcept(SBVRExpressionModel candidate, ModelElementInstance element) {
        return addGeneralConcept(candidate, extractElementText(element));
    }
}

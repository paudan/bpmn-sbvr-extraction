package org.ktu.isd.bpmn2sbvr.extract;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.ktu.isd.bpmn2sbvr.models.ConceptExtractionEntry;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel;
import org.ktu.isd.bpmn2sbvr.models.SourceEntry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class TestBpmnGatewayRules {

    private BpmnSbvrExtractor initExtractor(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("extract/" + fileName)).getFile());
        BpmnSbvrExtractor extractor = new BpmnSbvrExtractor(file, false, false);
        extractor.extractAll();
        return extractor;
    }

    protected void printExtractorOutput(Map<SourceEntry, ConceptExtractionEntry> objects) {
        for(Entry<SourceEntry, ConceptExtractionEntry> item: objects.entrySet()) {
            List<String> outputs = new ArrayList<>();
            for (SBVRExpressionModel sbvr: item.getValue().getCandidates())
                outputs.add(sbvr.toString());
            System.out.println("Source: " + String.join(",", item.getKey().getSourceNames()) + " -> output: " + String.join(",", outputs));
        }
    }

    protected List<String> getOutputsAsStrings(Map<SourceEntry, ConceptExtractionEntry> objects, boolean outputSources) {
        List<String> results = new ArrayList<>();
        for (Entry<SourceEntry, ConceptExtractionEntry> item: objects.entrySet()) {
            List<String> outputs = new ArrayList<>();
            for (SBVRExpressionModel sbvr : item.getValue().getCandidates())
                if (outputSources)
                    outputs.add(sbvr.toString());
                else
                    results.add(sbvr.toString());
            if (outputSources)
                results.add("Source: " + String.join(",", item.getKey().getSourceNames()) + " -> output: " + String.join(",", outputs));
        }
        return results;
    }

    @Test
    public void testTestModel1Extraction() {
        BpmnSbvrExtractor extractor = initExtractor("TestModel1.bpmn2");
        Map<SourceEntry, ConceptExtractionEntry> brObjects = extractor.getBRCandidateModel().getDataset();
        printExtractorOutput(brObjects);
        List<String> outputs = getOutputsAsStrings(brObjects, false);
        String[] expected = {
                "It is obligatory that provider process order, if provider apply VIP discount, after provider register order",
                "It is obligatory that provider apply VIP discount if customer is VIP customer, after provider register order",
                "It is obligatory that provider package order, after provider order additional packaging or provider process order",
                "It is obligatory that provider package order, after provider process order or provider order additional packaging",
                "It is obligatory that provider ship order after provider package order",
                "It is obligatory that provider order additional packaging additional packaging is required, if provider apply VIP discount, after provider register order",
                "It is obligatory that provider apply VIP discount",
                "It is obligatory that provider order additional packaging if additional packaging is required, otherwise provider process order"
        };
        MatcherAssert.assertThat(outputs, Matchers.containsInAnyOrder(expected));
    }

    @Test
    public void testTestModel2Extraction() {
        BpmnSbvrExtractor extractor = initExtractor("TestModel2.bpmn2");
        Map<SourceEntry, ConceptExtractionEntry> brObjects = extractor.getBRCandidateModel().getDataset();
        printExtractorOutput(brObjects);
    }

    @Test
    public void testTestModel4Extraction() {
        BpmnSbvrExtractor extractor = initExtractor("TestModel4.bpmn2");
        Map<SourceEntry, ConceptExtractionEntry> brObjects = extractor.getBRCandidateModel().getDataset();
        printExtractorOutput(brObjects);
    }

    @Test
    public void testTestModel5Extraction() {
        BpmnSbvrExtractor extractor = initExtractor("TestModel5.bpmn2");
        Map<SourceEntry, ConceptExtractionEntry> brObjects = extractor.getBRCandidateModel().getDataset();
        printExtractorOutput(brObjects);
    }

    @Test
    public void testTestModel6Extraction() {
        BpmnSbvrExtractor extractor = initExtractor("TestModel6.bpmn2");
        Map<SourceEntry, ConceptExtractionEntry> brObjects = extractor.getBRCandidateModel().getDataset();
        printExtractorOutput(brObjects);
    }
}

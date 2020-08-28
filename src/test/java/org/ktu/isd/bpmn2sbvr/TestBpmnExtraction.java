package org.ktu.isd.bpmn2sbvr;

import org.junit.Test;
import org.ktu.isd.bpmn2sbvr.extract.BpmnSbvrExtractor;
import org.ktu.isd.bpmn2sbvr.models.ConceptExtractionEntry;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel;
import org.ktu.isd.bpmn2sbvr.models.SourceEntry;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;


public class TestBpmnExtraction {

    private Map<String, Map<String, Integer>> ruleStats = new TreeMap<>();
    private Set<String> ruleNames;

    public TestBpmnExtraction() {
        ruleNames = new TreeSet<>();
        for (int i = 1; i <=9; i++)
            ruleNames.add("T" + i);
        ruleNames.add("Complex");
    }

    protected Map<String, Map<SourceEntry, ConceptExtractionEntry>> objectsByRuleName(Map<SourceEntry, ConceptExtractionEntry> objects) {
        Map<String, Map<SourceEntry, ConceptExtractionEntry>> output = new TreeMap<>();
        for (Entry<SourceEntry, ConceptExtractionEntry> item: objects.entrySet()) {
            String rule = item.getKey().getRule();
            if (rule == null) continue;
            output.putIfAbsent(rule, new HashMap<>());
            output.get(rule).put(item.getKey(), item.getValue());
        }
        return output;
    }

    private void outputStats(String filename) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            writer.write("Model;");
            writer.write(String.join(";", ruleNames));
            writer.newLine();
            for (Entry<String, Map<String, Integer>> modelEntry: ruleStats.entrySet()) {
                writer.write(modelEntry.getKey() + ";");
                for (Entry<String, Integer> ruleEntry: modelEntry.getValue().entrySet())
                    writer.write(ruleEntry.getValue() + ";");
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void extract(File file) {
        String dirName = "results";
        String modelName = file.toPath().getFileName().toString().replaceFirst("[.][^.]+$", "");
        try {
            Files.createDirectories(Paths.get(dirName));
        } catch (IOException e) {
            Logger.getLogger(TestBpmnExtraction.class.getName()).log(Level.SEVERE, null, e);
        }
        BpmnSbvrExtractor extractor = new BpmnSbvrExtractor(file, false, false);
        extractor.extractAll();
        String concepts = Paths.get(dirName, modelName + ".voc.txt").toString();
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(concepts), StandardCharsets.UTF_8))) {
            writer.write("General concepts");
            writer.write("\n");
            writer.write(String.join("\n", extractor.getGCCandidateModel().getConceptsListText()));
            writer.write("\n\n");
            writer.write("Verb concepts");
            writer.write("\n");
            writer.write(String.join("\n", extractor.getVCCandidateModel().getConceptsListText()));
        } catch (IOException e) {
            Logger.getLogger(TestBpmnExtraction.class.getName()).log(Level.SEVERE, null, e);
        }
        Map<String, Integer> modelStats = new TreeMap<>();
        for (String ruleName: ruleNames)
            modelStats.put(ruleName, 0);
        ruleStats.put(modelName, modelStats);
        String filePath = Paths.get(dirName, modelName + ".rules.txt").toString();
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(String.join("\n", extractor.getBRCandidateModel().getConceptsListText()));
        } catch (IOException e) {
            Logger.getLogger(TestBpmnExtraction.class.getName()).log(Level.SEVERE, null, e);
        }
        outputStats(Paths.get(dirName, modelName + ".stats.txt").toString());
    }

    @Test
    public void testExtractModel1() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("1000531133.bpmn")).getFile());
        extract(file);
    }

    @Test
    public void testExtractModel2() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("1465771593_rev2.bpmn")).getFile());
        extract(file);
    }
}

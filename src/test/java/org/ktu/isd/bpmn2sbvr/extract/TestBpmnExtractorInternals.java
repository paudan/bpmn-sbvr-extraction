package org.ktu.isd.bpmn2sbvr.extract;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.Task;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.ktu.isd.bpmn2sbvr.extract.BpmnSbvrExtractor.GatewayNeighborhood;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel.Conjunction;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;


public class TestBpmnExtractorInternals {

    private BpmnSbvrExtractor initExtractor(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("extract/" + fileName)).getFile());
        BpmnSbvrExtractor extractor = new BpmnSbvrExtractor(file, false, false);
        extractor.extractAll();
        return extractor;
    }

    private void outputInternalStructures(String fileName, boolean fillIncoming) {
        BpmnSbvrExtractor extractor = initExtractor(fileName);
        BpmnModelInstance modelInstance = extractor.getModelInstance();
        Collection<Gateway> gateways = modelInstance.getModelElementsByType(Gateway.class);
        for (Gateway el: gateways) {
            System.out.println();
            GatewayNeighborhood tuple = extractor.new GatewayNeighborhood(el, fillIncoming);
            System.out.println(tuple);
        }
    }

    private void testGatewaySearch(String fileName, String taskName, String gatewayName, int sizeAssertion) {
        BpmnSbvrExtractor extractor = initExtractor(fileName);
        BpmnModelInstance modelInstance = extractor.getModelInstance();
        Collection<Task> elements = modelInstance.getModelElementsByType(Task.class);
        for (Task el: elements)
            if (el.getName().equalsIgnoreCase(taskName)){
                for (SequenceFlow edge: el.getIncoming())
                    if (extractor.isGatewayElement(edge.getSource())) {
                        GatewayNeighborhood nhood = extractor.gatewayNeighborhoods.get(edge.getSource());
                        assertEquals(gatewayName, edge.getSource().getName());
                        assertEquals(sizeAssertion, nhood.outgoingGateways.size());
                    }
            }
    }

    private Gateway getBoundaryGateway(GatewayNeighborhood nhood) {
        if (nhood.outgoingGateways.isEmpty())
            return nhood.gatewayNode;
        for (Entry<Gateway, GatewayNeighborhood> neighborNode: nhood.outgoingGateways.entrySet())
            return getBoundaryGateway(neighborNode.getValue());
        return null;
    }

    private void testFindBoundaryGateway(String fileName, String taskName, String gatewayName) {
        BpmnSbvrExtractor extractor = initExtractor(fileName);
        BpmnModelInstance modelInstance = extractor.getModelInstance();
        Collection<Task> elements = modelInstance.getModelElementsByType(Task.class);
        for (Task el: elements)
            if (el.getName().equalsIgnoreCase(taskName)){
                for (SequenceFlow edge: el.getIncoming())
                    if (extractor.isGatewayElement(edge.getSource())) {
                        GatewayNeighborhood nhood = extractor.gatewayNeighborhoods2.get(edge.getSource());
                        assertNotNull(nhood);
                        Gateway gateway = getBoundaryGateway(nhood);
                        assertNotNull(gateway);
                        assertEquals(gatewayName, gateway.getName());
                        Conjunction conjunction = getGatewayConjunction(gateway);
                        Object[] results = extractor.getRuleWithGateways(gateway, conjunction, null);
                        System.out.println(results[0]);
                    }
                break;
            }
    }

    private void getAllBoundaryGateways(GatewayNeighborhood nhood, Set<Gateway> nodes) {
        if (nhood.outgoingGateways.isEmpty())
            nodes.add(nhood.gatewayNode);
        for (GatewayNeighborhood neighborNode: nhood.outgoingGateways.values())
            getAllBoundaryGateways(neighborNode, nodes);
    }


    private Conjunction getGatewayConjunction(Gateway gateway) {
        if (gateway instanceof ExclusiveGateway)
            return Conjunction.OR;
        else if (gateway instanceof InclusiveGateway)
            return Conjunction.AND;
        return null;
    }

    private void testFindAllBoundaryGateways(String fileName, String taskName, int numAssert) {
        BpmnSbvrExtractor extractor = initExtractor(fileName);
        BpmnModelInstance modelInstance = extractor.getModelInstance();
        Collection<Task> elements = modelInstance.getModelElementsByType(Task.class);
        for (Task el: elements)
            if (el.getName().equalsIgnoreCase(taskName)){
                for (SequenceFlow edge: el.getIncoming())
                    if (extractor.isGatewayElement(edge.getSource())) {
                        GatewayNeighborhood nhood = extractor.gatewayNeighborhoods2.get(edge.getSource());
                        Set<Gateway> boundaryGateways = new HashSet<>();
                        getAllBoundaryGateways(nhood, boundaryGateways);
                        assertEquals(numAssert, boundaryGateways.size());
                    }
                break;
            }
    }

    private void getAllBoundaryGatewaysLeft(GatewayNeighborhood nhood, Set<Gateway> nodes) {
        if (nhood.incomingGateways.isEmpty())
            nodes.add(nhood.gatewayNode);
        for (GatewayNeighborhood neighborNode: nhood.incomingGateways.values())
            getAllBoundaryGatewaysLeft(neighborNode, nodes);
    }

    private Set<Gateway> testFindAllBoundaryGatewaysLeft(String fileName, String gatewayName) {
        BpmnSbvrExtractor extractor = initExtractor(fileName);
        BpmnModelInstance modelInstance = extractor.getModelInstance();
        Collection<Gateway> elements = modelInstance.getModelElementsByType(Gateway.class);
        Set<Gateway> boundaryGateways = new HashSet<>();
        for (Gateway el: elements) {
            if (el.getName().equalsIgnoreCase(gatewayName)) {
                GatewayNeighborhood nhood = extractor.gatewayNeighborhoods.get(el);
                getAllBoundaryGatewaysLeft(nhood, boundaryGateways);
                break;
            }
        }
        return boundaryGateways;
    }

    private void testProcessPartialRules(String fileName, String... gatewayNames) {
        BpmnSbvrExtractor extractor = initExtractor(fileName);
        BpmnModelInstance modelInstance = extractor.getModelInstance();
        extractor.extractBusinessRuleCandidates();
        Collection<Gateway> elements = modelInstance.getModelElementsByType(Gateway.class);
        for (String gatewayName: gatewayNames)
            for (Gateway el: elements)
                if (el.getName().equalsIgnoreCase(gatewayName)){
                    GatewayNeighborhood nhood = extractor.gatewayNeighborhoods.get(el);
                    System.out.println(nhood);
                }
    }

    @Test
    public void testInternalStructures() {
        outputInternalStructures("TestModel1.bpmn2", true);
    }

    @Test
    public void testInternalStructures_TestModel2() {
        outputInternalStructures("TestModel2.bpmn2", true);
    }

    @Test
    public void testInternalStructures2() {
        System.out.println("Structures, when incoming element structures are filled recursively:");
        outputInternalStructures("TestModel2", true);
        System.out.println("Structures, when outgoing element structures are filled recursively:");
        outputInternalStructures("TestModel2", false);
    }

    @Test
    public void testBoundaryGatewaySearchTestModel1() {
        Set<Gateway> boundaryGateways = testFindAllBoundaryGatewaysLeft("TestModel1.bpmn2", "Inclusive Gateway 1");
        assertEquals(1, boundaryGateways.size());
        for (Gateway gateway: boundaryGateways)
            System.out.println("Gateway: " + gateway.getName());
    }

    @Test
    public void testBoundaryGatewaySearchTestModel2() {
        testGatewaySearch("TestModel2.bpmn2", "a2", "Excl1", 0);
        testGatewaySearch("TestModel2.bpmn2", "t2", "Inc1", 1);
        testFindBoundaryGateway("TestModel2.bpmn2", "t2", "Excl1");
    }

    @Test
    public void testBoundaryGatewaySearchTestModel3() {
        testGatewaySearch("TestModel3.bpmn2", "a2", "Excl2", 0);
        testGatewaySearch("TestModel3.bpmn2", "top", "Excl2", 0);
        testGatewaySearch("TestModel3.bpmn2", "t2", "Inc1", 1);
        testFindBoundaryGateway("TestModel3.bpmn2", "t2", "Excl2");
    }

    @Test
    public void testBoundaryGatewaySearchTestModel4() {
        testGatewaySearch("TestModel4.bpmn2", "a2", "Excl2", 0);
        testGatewaySearch("TestModel4.bpmn2", "top", "Excl2", 0);
        testGatewaySearch("TestModel4.bpmn2", "t2", "Inc1", 2);
        testFindAllBoundaryGateways("TestModel4.bpmn2", "t2", 2);
    }

    @Test
    public void testPartialRuleProcessing() {
        //testProcessPartialRules("TestModel4", "Exclusive Gateway Excl1", "Inclusive Gateway Inc1");
        testProcessPartialRules("TestModel1.bpmn2", "Exclusive Gateway2", "Inclusive Gateway2");
    }
}

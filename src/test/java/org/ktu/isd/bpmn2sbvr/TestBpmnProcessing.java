package org.ktu.isd.bpmn2sbvr;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.Event;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.Lane;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.ModelParseException;
import org.junit.Test;
import org.ktu.isd.bpmn2sbvr.process.BpmnFileReader;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TestBpmnProcessing {

    private Map<FlowElement, BaseElement> createTaskLaneMap(BpmnModelInstance modelInstance) {
        Map<FlowElement, BaseElement> taskLaneMap = new HashMap<>();
        Collection<Process> processes = modelInstance.getModelElementsByType(Process.class);
        for (Process proc: processes)
            for (FlowElement node: proc.getFlowElements())
                taskLaneMap.put(node, proc);
        Collection<Lane> lanes = modelInstance.getModelElementsByType(Lane.class);
        for (Lane lane: lanes)
            for (FlowNode node: lane.getFlowNodeRefs())
                if (lane.getName() != null)
                    taskLaneMap.put(node, lane);
        return taskLaneMap;
    }

    private String getElementName(BaseElement el) {
        String name = null;
        if (el instanceof Process)
            name = ((Process) el).getName();
        else if (el instanceof Lane)
            name = ((Lane) el).getName();
        else if (el instanceof Event)
            name = ((Event) el).getName();
        else if (el instanceof FlowElement)
            name = ((FlowElement) el).getName();
        else
            name = el.getAttributeValue("name");
        if (name == null)
            return name;
        return name.trim().replace("\n", "");
    }

    private boolean isTimerEvent(BaseElement el) {
        if (el == null)
            return false;
        return el instanceof BoundaryEvent || el instanceof IntermediateCatchEvent;
        //return BPMN2Profile.isTimerBoundaryEvent(el) || BPMN2Profile.isTimerCatchIntermediateEvent(el);
    }

    private String getActivityText(FlowElement el) {
        if (el == null)
            return null;
        if (el instanceof StartEvent){
            String name = getElementName(el);
            return name == null ? "Process starts" : name;
        } else if (el instanceof EndEvent){
            String name = getElementName(el);
            return name == null ? "Process ends" : name;
        } else if (isTimerEvent(el)) {
            String name = getElementName(el);
            if (name != null)
                return name + " has passed";
        } else
            return getElementName(el);
        return null;
    }

    @Test
    public void testReadBPMNModel1() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("1465771593_rev2.bpmn")).getFile());
        BpmnModelInstance modelInstance = Bpmn.readModelFromFile(file);
        System.out.println(modelInstance.getModel().getModelName());
        Collection<Task> tasks = modelInstance.getModelElementsByType(Task.class);
        Map<FlowElement, BaseElement> taskLaneMap = createTaskLaneMap(modelInstance);
        System.out.println("\ntasks\\lanes");
        System.out.println(taskLaneMap.entrySet().stream().map(el -> {
            return getElementName(el.getKey()) + ", parent: " + getElementName(el.getValue());
        }).collect(Collectors.joining("\n")));
        ArrayList<Task> taskList = new ArrayList<>(tasks);
        Task tested = taskList.get(0);
        System.out.println(getActivityText(tested) + ", lane: " + getElementName(taskLaneMap.get(tested)));
        tested = taskList.get(1);
        System.out.println(getActivityText(tested) + ", lane: " + getElementName(taskLaneMap.get(tested)));
    }

    @Test
    public void testReadBPMNModel2() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("1000531133.bpmn")).getFile());
        BpmnModelInstance modelInstance = BpmnFileReader.readModelFromFile(file);
        System.out.println(modelInstance.getModel().getModelName());
        System.out.println(modelInstance.getModelElementsByType(Task.class).stream().map(el -> el.getAttributeValue("name").trim()).collect(Collectors.joining(", ")));
    }

    @Test(expected = ModelParseException.class)
    public void testReadBPMNModel2_1() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("1000531133.bpmn")).getFile());
        Bpmn.readModelFromFile(file);  // BPMN file has some errors, validation fails
     }

}

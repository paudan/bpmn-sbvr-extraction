package org.ktu.isd.bpmn2sbvr.extract;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.CallActivity;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.DataInput;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataOutput;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.Event;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.InteractionNode;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.IntermediateThrowEvent;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.Lane;
import org.camunda.bpm.model.bpmn.instance.LaneSet;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.camunda.bpm.model.bpmn.instance.Resource;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel.Bracket;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel.Conditional;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel.Conjunction;
import org.ktu.isd.bpmn2sbvr.models.SBVRExpressionModel.RuleType;
import org.ktu.isd.bpmn2sbvr.models.SourceEntry;
import org.ktu.isd.bpmn2sbvr.process.BpmnFileReader;

/**
 * @author Paulius Danenas, 2019
 */
public class BpmnSbvrExtractor extends AbstractSbvrExtractor {

    protected BpmnModelInstance modelInstance;
    Map<FlowNode, GatewayNeighborhood> gatewayNeighborhoods, gatewayNeighborhoods2;
    protected Map<FlowElement, BaseElement> taskLaneMap;

    class ActivityNeighborhood {
        FlowNode activity;
        String activityText;
        Map<ModelElementInstance, String> activitySubjects;
        Map<FlowNode, Map<SequenceFlow, String>> incomingConditions, outgoingConditions;
        Map<FlowNode, Map<SequenceFlow, String>> correctionsIncoming, correctionsOutgoing;
        private Map<FlowNode, Integer> nullCountIncoming, nullCountOutgoing;
        int nullsTotalIncoming, nullsTotalOutgoing;

        private ActivityNeighborhood(FlowNode node) {
            this.activity = node;
            this.activityText = getActivityText(node);
            activitySubjects = getSubjectNames(node, false);
            incomingConditions = new HashMap<>();
            nullCountIncoming = new HashMap<>();
            for (SequenceFlow edge : node.getIncoming())
                addCondition(incomingConditions, nullCountIncoming, edge, edge.getSource());
            outgoingConditions = new HashMap<>();
            nullCountOutgoing = new HashMap<>();
            for (SequenceFlow edge : node.getOutgoing())
                addCondition(outgoingConditions, nullCountOutgoing, edge, edge.getTarget());
            // Resolve cases when multiple sequence flows are incoming/outgoing from the same node, with contradictions
            correctionsIncoming = createCorrections(cloneConditions(incomingConditions), nullCountIncoming);
            correctionsOutgoing = createCorrections(cloneConditions(outgoingConditions), nullCountOutgoing);
        }

        private Map<FlowNode, Map<SequenceFlow, String>> cloneConditions(Map<FlowNode, Map<SequenceFlow, String>> conditions) {
            Map<FlowNode, Map<SequenceFlow, String>> cloned = new HashMap<>();
            for (Entry<FlowNode, Map<SequenceFlow, String>> nodeEntry : conditions.entrySet()) {
                Map<SequenceFlow, String> copy = new HashMap<>();
                for (Entry<SequenceFlow, String> condEl : nodeEntry.getValue().entrySet())
                    copy.put(condEl.getKey(), condEl.getValue());
                cloned.put(nodeEntry.getKey(), copy);
            }
            return cloned;
        }

        private void addCondition(Map<FlowNode, Map<SequenceFlow, String>> conditions,
                                  Map<FlowNode, Integer> nullCounts, SequenceFlow edge, FlowNode node) {
            String condition = getCondition(edge);
            nullCounts.putIfAbsent(node, 0);
            if (condition == null) {
                nullCounts.put(node, nullCounts.get(node) + 1);
                if (conditions == this.incomingConditions)
                    nullsTotalIncoming += 1;
                else
                    nullsTotalOutgoing += 1;
            }
            Map<SequenceFlow, String> condList = conditions.get(node);
            if (condList == null) {
                condList = new HashMap<>();
                conditions.put(node, condList);
            }
            condList.put(edge, condition);
        }

        private Map<FlowNode, Map<SequenceFlow, String>> createCorrections(Map<FlowNode, Map<SequenceFlow, String>> conditions,
                                                                           Map<FlowNode, Integer> nullCounts) {
            for (Entry<FlowNode, Integer> nodeEntry : nullCounts.entrySet()) {
                Integer nullStats = nodeEntry.getValue();
                Map<SequenceFlow, String> condList = conditions.get(nodeEntry.getKey());
                if (nullStats >= 1 && condList != null && condList.size() > 1)
                    // We have contradictions, transition condition is undefined
                    condList.clear();
            }
            return conditions;
        }

        private String formatPadding(String str) {
            return StringUtils.removeEnd(str, "\n").replaceAll("\n", "\n\t");
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Activity element:").append(activity.getAttributeValue("name")).append("\n")
                    .append("Extracted text: ").append(activityText).append("\n");
            Set<ModelElementInstance> elements = activitySubjects.keySet().stream().filter(Objects::nonNull).collect(Collectors.toSet());
            if (elements.size() > 0) {
                sb.append("Subjects (executing elements): ");
                sb.append(elements.stream().map(BpmnSbvrExtractor.this::getElementName).collect(Collectors.joining(",")));
            }
            if (!incomingConditions.isEmpty()) {
                sb.append("\nConditions from incoming edges:\n");
                sb.append(formatPadding(getConditionsRepresentation(incomingConditions)));
            }
            if (!outgoingConditions.isEmpty()) {
                sb.append("\nConditions from outgoing edges:\n");
                sb.append(formatPadding(getConditionsRepresentation(outgoingConditions)));
            }
            if (!correctionsIncoming.isEmpty()) {
                sb.append("\nConditions from incoming edges after resolving default conditions:\n");
                sb.append(formatPadding(getConditionsRepresentation(correctionsIncoming)));
            }
            if (!correctionsOutgoing.isEmpty()) {
                sb.append("\nConditions from outgoing edges after resolving default conditions:\n");
                sb.append(formatPadding(getConditionsRepresentation(correctionsOutgoing)));
            }
            if (!nullCountIncoming.isEmpty()) {
                sb.append("\nNumber of incoming edges with null conditions:\n");
                for (Entry<FlowNode, Integer> nullEntry: nullCountIncoming.entrySet())
                    sb.append("\t").append(nullEntry.getKey().getName()).append(": ").append(nullEntry.getValue()).append("\n");
            }
            if (!nullCountOutgoing.isEmpty()) {
                sb.append("Number of outgoing edges with null conditions: ").append("\n");
                for (Entry<FlowNode, Integer> nullEntry: nullCountOutgoing.entrySet())
                    sb.append(nullEntry.getKey().getName()).append(": ").append(nullEntry.getValue()).append("\n");
            }
            return sb.toString().trim();
        }
    }

    class GatewayNeighborhood {
        Gateway gatewayNode;
        Map<FlowNode, ActivityNeighborhood> incomingActivities, outgoingActivities;
        Map<FlowNode, SequenceFlow> outgoingDefault;
        Map<FlowNode, Map<SequenceFlow, String>> incomingConditions, outgoingConditions;
        private Map<FlowNode, Integer> nullCountIncoming, nullCountOutgoing;
        Map<Gateway, GatewayNeighborhood> incomingGateways, outgoingGateways;
        int nullsTotalIncoming, nullsTotalOutgoing;
        SBVRExpressionModel partialRule;
        List<Object> partialRuleSource;

        public GatewayNeighborhood(Gateway gatewayNode, boolean fillIncoming) {
            this.gatewayNode = gatewayNode;
            incomingActivities = new HashMap<>();
            outgoingActivities = new HashMap<>();
            incomingGateways = new HashMap<>();
            outgoingGateways = new HashMap<>();
            incomingConditions = new HashMap<>();
            outgoingConditions = new HashMap<>();
            nullCountIncoming = new HashMap<>();
            nullCountOutgoing = new HashMap<>();
            outgoingDefault = new HashMap<>();
            partialRule = new SBVRExpressionModel();
            partialRuleSource = new ArrayList<>();
            for (SequenceFlow edge : gatewayNode.getIncoming()) {
                FlowNode srcElement = edge.getSource();
                if (srcElement == null)
                    continue;
                addIncomingCondition(edge, srcElement);
                if (isActivityElement(srcElement) || isEventElement(srcElement)) {
                    ActivityNeighborhood taskTuple = new ActivityNeighborhood(srcElement);
                    incomingActivities.put(taskTuple.activity, taskTuple);
                } else if (isGatewayElement(srcElement)) {
                    Gateway node = (Gateway) srcElement;
                    GatewayNeighborhood nnode = null;
                    if (fillIncoming)
                        nnode = new GatewayNeighborhood(node, fillIncoming);
                    incomingGateways.put(node, nnode);
                }
            }
            outgoingActivities = new HashMap<>();
            for (SequenceFlow edge : gatewayNode.getOutgoing()) {
                FlowNode targetElement = edge.getTarget();
                if (targetElement == null)
                    continue;
                /*if (BPMNHelper.isDefaultSequenceFlow(gatewayNode, edge))
                    outgoingDefault.put(targetElement, edge);
                else*/
                    addOutgoingCondition(edge, targetElement);
                if (isActivityElement(targetElement) || isEventElement(targetElement)) {
                    ActivityNeighborhood taskTuple = new ActivityNeighborhood(targetElement);
                    outgoingActivities.put(taskTuple.activity, taskTuple);
                } else if (isGatewayElement(targetElement)) {
                    Gateway node = (Gateway) targetElement;
                    GatewayNeighborhood nnode = null;
                    if (!fillIncoming)
                        nnode = new GatewayNeighborhood(node, fillIncoming);
                    outgoingGateways.put(node, nnode);
                }
            }
        }

        private void addIncomingCondition(SequenceFlow edge, FlowNode node) {
            String condition = getCondition(edge);
            nullCountIncoming.putIfAbsent(node, 0);
            if (condition == null) {
                nullCountIncoming.put(node, nullCountIncoming.get(node) + 1);
                nullsTotalIncoming += 1;
            }
            Map<SequenceFlow, String> condList = incomingConditions.get(node);
            if (condList == null) {
                condList = new HashMap<>();
                incomingConditions.put(node, condList);
            }
            condList.put(edge, condition);
        }

        private void addOutgoingCondition(SequenceFlow edge, FlowNode node) {
            String condition = getCondition(edge);
            nullCountOutgoing.putIfAbsent(node, 0);
            if (condition == null) {
                nullCountOutgoing.put(node, nullCountOutgoing.get(node) + 1);
                nullsTotalOutgoing += 1;
            }
            Map<SequenceFlow, String> condList = outgoingConditions.get(node);
            if (condList == null) {
                condList = new HashMap<>();
                outgoingConditions.put(node, condList);
            }
            condList.put(edge, condition);
        }

        private String formatPadding(String str) {
            return StringUtils.removeEnd(str, "\n").replaceAll("\n", "\n\t");
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Gateway element:").append(gatewayNode.getName());
            sb.append("\n");
            if (!incomingActivities.isEmpty()) {
                sb.append("Incoming activity nodes:\n");
                for (ActivityNeighborhood taskNode: incomingActivities.values())
                    sb.append("\t").append(formatPadding(taskNode.toString())).append("\n\n");
            }
            if (!outgoingActivities.isEmpty()) {
                sb.append("Outgoing activity nodes:\n");
                for (ActivityNeighborhood taskNode: outgoingActivities.values())
                    sb.append("\t").append(formatPadding(taskNode.toString())).append("\n\n");
            }
            if (!incomingGateways.isEmpty()) {
                sb.append("Incoming gateway nodes:\n");
                for (Entry<Gateway, GatewayNeighborhood> gatewayNode: incomingGateways.entrySet()) {
                    sb.append("\t").append("Element: ").append(gatewayNode.getKey().getName()).append("\n");
                    if (gatewayNode.getValue() != null)
                        sb.append("\t").append(formatPadding(gatewayNode.getValue().toString())).append("\n\n");
                }
            }
            if (!outgoingGateways.isEmpty()) {
                sb.append("Outgoing gateway nodes:\n");
                for (Entry<Gateway, GatewayNeighborhood> gatewayNode: outgoingGateways.entrySet()) {
                    sb.append("\t").append("Element: ").append(gatewayNode.getKey().getName()).append("\n");
                    if (gatewayNode.getValue() != null)
                        sb.append("\t").append(formatPadding(gatewayNode.getValue().toString())).append("\n\n");
                }
            }
            if (!incomingConditions.isEmpty()) {
                sb.append("Conditions from incoming edges:\n");
                sb.append(formatPadding(getConditionsRepresentation(incomingConditions)));
                sb.append("\n");
            }
            if (!outgoingConditions.isEmpty()) {
                sb.append("Conditions from outgoing edges:\n");
                sb.append(formatPadding(getConditionsRepresentation(outgoingConditions)));
                sb.append("\n");
            }
            if (!nullCountIncoming.isEmpty()) {
                sb.append("Number of incoming edges with null conditions:\n");
                for (Entry<FlowNode, Integer> nullEntry: nullCountIncoming.entrySet())
                    sb.append("\t").append(nullEntry.getKey().getName()).append(": ").append(nullEntry.getValue()).append("\n");
                sb.append("\n");
            }
            if (!nullCountOutgoing.isEmpty()) {
                sb.append("Number of outgoing edges with null conditions:\n");
                for (Entry<FlowNode, Integer> nullEntry: nullCountOutgoing.entrySet())
                    sb.append("\t").append(nullEntry.getKey().getName()).append(": ").append(nullEntry.getValue()).append("\n");
                sb.append("\n");
            }
            if (!partialRule.isEmpty())
                sb.append("Partial rule:\n").append(partialRule);
            return sb.toString().trim();
        }
    }

    public BpmnSbvrExtractor(File file, boolean strictOnly, boolean extractMMVoc) {
        super(file, strictOnly, extractMMVoc);
        modelInstance = BpmnFileReader.readModelFromFile(file);
        taskLaneMap = createTaskLaneMap(modelInstance);
        extractGatewayNeighborhoods();
    }

    public BpmnModelInstance getModelInstance() {
        return modelInstance;
    }

    private boolean isActivityElement(ModelElementInstance el) {
        if (el == null)
            return false;
        return el instanceof Task || el instanceof CallActivity || el instanceof SubProcess;
    }

    private boolean isEventElement(ModelElementInstance el) {
        return el instanceof StartEvent || el instanceof BoundaryEvent || isIntermediaryEvent(el) ||
               el instanceof EndEvent || isTimerEvent(el);
    }

    private boolean isTimerEvent(ModelElementInstance el) {
        if (el == null)
            return false;
        return el instanceof BoundaryEvent || el instanceof IntermediateCatchEvent;
        //return BPMN2Profile.isTimerBoundaryEvent(el) || BPMN2Profile.isTimerCatchIntermediateEvent(el);
    }

    private boolean isIntermediaryEvent(ModelElementInstance el) {
        if (el == null)
            return false;
        return el instanceof IntermediateCatchEvent || el instanceof IntermediateThrowEvent;
    }

    private boolean isDataObject(ModelElementInstance el) {
        if (el == null)
            return false;
        return el instanceof DataObject || el instanceof DataInput || el instanceof DataOutput;
    }

    boolean isGatewayElement(ModelElementInstance el) {
        if (el == null)
            return false;
        return el instanceof ExclusiveGateway || el instanceof InclusiveGateway || el instanceof ParallelGateway || el instanceof EventBasedGateway;
    }

    private boolean isLaneElement(ModelElementInstance el) {
        if (el == null)
            return false;
        return el instanceof Lane || el instanceof LaneSet;
    }

    String getCondition(SequenceFlow el) {
        ConditionExpression conditionExpression = el.getConditionExpression();
        if (conditionExpression == null)
            return null;
        String cond = conditionExpression.getRawTextContent();
        if (cond != null && cond.trim().length() > 0)
            return cond;
        cond = el.getName().trim();
        return cond.length() > 0 ? cond : null;
    }

    protected String getElementName(ModelElementInstance el) {
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

    private Map<ModelElementInstance, String> getSubjectNames(ModelElementInstance element, boolean getEventSubjects) {
        Map<ModelElementInstance, String> names = new HashMap<>();
        // Should be modelled as unary concepts
        if (!getEventSubjects && isEventElement(element)) {
            // Add null values to allow for single step in loops
            names.put(null, null);
            return names;
        }
        if (isActivityElement(element)) {
            BaseElement lane = taskLaneMap.get(element);
            names.put(lane, getElementName(lane));
        }
        else if (isLaneElement(element) || element instanceof Process)
            names.put(element, getElementName(element));
        return names;
    }

    private String extractActivityGC(BaseElement el) {
        if (el == null || (!isActivityElement(el) && !isEventElement(el)))
            return null;
        if (el instanceof StartEvent){
            String name = extractActionUnaryLikeGC(el);
            if (name == null) {
                ModelElementInstance owner = el.getParentElement();
                if (owner != null) {
                    String extracted = extractElementText(owner);
                    if (extracted != null)
                        return extracted;
                }
            } else
                return name;
        } else if (el instanceof EndEvent){
            String name = extractActionUnaryLikeGC(el);
            if (name == null) {
                ModelElementInstance owner = el.getParentElement();
                if (owner != null) {
                    String extracted = extractElementText(owner);
                    if (extracted != null)
                        return extracted;
                }
            } else
                return name;
        } else if (isTimerEvent(el))
            return extractElementText(el);
        else
            return extractActionGC(el);
        return null;
    }

    private SBVRExpressionModel addLaneGeneralConcept(SBVRExpressionModel candidate, ModelElementInstance element) {
        String objText = getElementName(element);
        SBVRExpressionModel objConcept = getGeneralConcept(objText);
        return objConcept != null ? candidate.addIdentifiedExpression(objConcept) : candidate.addUnidentifiedText(objText);
    }

    @Override
    protected void extractGeneralConceptCandidates() {
        Collection<BaseElement> candidates = new HashSet<>();
        candidates.addAll(modelInstance.getModelElementsByType(Process.class));
        candidates.addAll(modelInstance.getModelElementsByType(Lane.class));
        candidates.addAll(modelInstance.getModelElementsByType(Resource.class));
        candidates.addAll(modelInstance.getModelElementsByType(DataObject.class));
        candidates.addAll(modelInstance.getModelElementsByType(DataStore.class));
        candidates.addAll(modelInstance.getModelElementsByType(DataInput.class));
        candidates.addAll(modelInstance.getModelElementsByType(DataOutput.class));
        candidates.addAll(modelInstance.getModelElementsByType(Message.class));
        for (BaseElement el: candidates) {
            createGeneralConcept(el, getElementName(el), true, true);
            if (isDataObject(el) || el instanceof DataStore) {
                String stateText = getElementName(((ItemAwareElement)el).getDataState());
                String elText = getElementName(el);
                if (stateText != null && elText != null)
                    createGeneralConcept(el, stateText + " " + elText, true, true);
            }
        }
        if (!extractedAuto) {
            candidates.clear();
            candidates.addAll(modelInstance.getModelElementsByType(Activity.class));
            candidates.addAll(modelInstance.getModelElementsByType(Event.class));
            for (BaseElement el: candidates) {
                createGeneralConcept(el, extractActivityGC(el), false, false);
                gc_candidates.setManualExtraction(new SourceEntry(Collections.singletonList(el)));
            }
        }
        candidates.clear();
        candidates.addAll(modelInstance.getModelElementsByType(SequenceFlow.class));
        for (BaseElement el: candidates) {
            SequenceFlow seqFlow = (SequenceFlow) el;
            ConditionExpression conditionExpression = seqFlow.getConditionExpression();
            if (conditionExpression != null) {
                String condition = getProperName(conditionExpression.getTextContent());
                if (condition != null)
                    gc_candidates.setManualExtraction(new SourceEntry(Collections.singletonList(el)));
            }
        }
    }

    @Override
    protected void extractVerbConceptCandidates() {
        Collection<BaseElement> candidates = new HashSet<>();
        candidates.addAll(modelInstance.getModelElementsByType(Activity.class));
        candidates.addAll(modelInstance.getModelElementsByType(Event.class));
        candidates.addAll(modelInstance.getModelElementsByType(SequenceFlow.class));
        for (BaseElement el: candidates) {
            if (isActivityElement(el) && !isEventElement(el) && !strictOnly) {
                Map<ModelElementInstance, String> subjects = getSubjectNames(el, false);
                for (Entry<ModelElementInstance, String> subject: subjects.entrySet())
                    createVerbConceptFromAction(subject.getKey(), el);
            } else if (isEventElement(el)) {
                if (el instanceof StartEvent)
                    createUnaryVerbConcept(el, "starts", extractActivityGC(el));
                else if (el instanceof EndEvent)
                    createUnaryVerbConcept(el, "ends", extractActivityGC(el));
                else if (isTimerEvent(el))
                    createUnaryVerbConcept(el, "has passed", extractActivityGC(el));
                else
                    createVerbConceptFromCondition(el, extractElementText(el));
            } else if (isEventElement(el) && !strictOnly) {
                createVerbConceptFromCondition(el, extractElementText(el));
                vc_candidates.setManualExtraction(new SourceEntry(Collections.singletonList(el)));
            }
            else if (el instanceof SequenceFlow && !strictOnly)
                createVerbConceptFromCondition(el, getCondition((SequenceFlow) el));
        }
        candidates.clear();
        candidates.addAll(modelInstance.getModelElementsByType(DataObject.class));
        candidates.addAll(modelInstance.getModelElementsByType(DataStore.class));
        candidates.addAll(modelInstance.getModelElementsByType(DataInput.class));
        candidates.addAll(modelInstance.getModelElementsByType(DataOutput.class));
        for (BaseElement el: candidates) {
            DataState state = ((ItemAwareElement)el).getDataState();
            if (state != null)
                createUnaryVerbConcept(el, state);
        }
    }
    @Override
    protected void extractBusinessRuleCandidates() {
        Collection<SequenceFlow> sequenceFlows = modelInstance.getModelElementsByType(SequenceFlow.class);
        for (SequenceFlow el: sequenceFlows)
            extractRuleT1(el);
        Collection<Gateway> gateways = modelInstance.getModelElementsByType(Gateway.class);
        for (Gateway el: gateways) {
            extractRuleT2(el);
            extractRuleT3(el);
        }
        Collection<EventBasedGateway> eventBasedGateways = modelInstance.getModelElementsByType(EventBasedGateway.class);
        for (EventBasedGateway el: eventBasedGateways)
            extractRuleT4(el);
        Collection<ParallelGateway> parallelGateways = modelInstance.getModelElementsByType(ParallelGateway.class);
        for (ParallelGateway el: parallelGateways)
            extractRuleT5(el);
        Collection<BoundaryEvent> boundaryEvents = modelInstance.getModelElementsByType(BoundaryEvent.class);
        for (BoundaryEvent el: boundaryEvents)
            extractRuleT6(el);
        Collection<Activity> activities = modelInstance.getModelElementsByType(Activity.class);
        for (Activity el: activities) {
            extractRuleT7(el);
            extractRuleT8(el);
        }
        Collection<MessageFlow> messageFlows = modelInstance.getModelElementsByType(MessageFlow.class);
        for (MessageFlow el: messageFlows)
            extractRuleT9(el);
        Collection<BaseElement> activitiesEvents = new HashSet<>();
        activitiesEvents.addAll(activities);
        activitiesEvents.addAll(modelInstance.getModelElementsByType(Event.class));
        for (BaseElement el: activitiesEvents)
            extractComplexRule(el);
    }

    private SBVRExpressionModel addActivity(SBVRExpressionModel model, FlowNode activity, String subject) {
        if (activity == null)
            return model;
        String taskText = getActivityText(activity);
        if (!isEventElement(activity))
            taskText = subject + " " + taskText;
        SBVRExpressionModel binary2 = getVerbConcept(taskText);
        return binary2 != null ? model.addIdentifiedExpression(binary2) : model.addUnidentifiedText(taskText);
    }

    private SBVRExpressionModel addCondition(SBVRExpressionModel model, String condition) {
        if (condition == null)
            return model;
        condition = condition.replaceAll("\n", " ").replaceAll("_", " ").replaceAll("  ", " ").trim();
        SBVRExpressionModel binary2 = getVerbConcept(condition);
        return binary2 != null ? model.addIdentifiedExpression(binary2) : model.addUnidentifiedText(condition);
    }

    private SBVRExpressionModel createMultipleConditions(Map<SequenceFlow, String> conditions, List<Object> objects) {
        SBVRExpressionModel model = new SBVRExpressionModel();
        if (conditions == null || conditions.isEmpty())
            return model;
        boolean added_or_first = true;
        Set<String> strConditions = conditions.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (strConditions.isEmpty())
            return model;
        for (Entry<SequenceFlow, String> cond : conditions.entrySet()) {
            if (!added_or_first)
                model.addConjunction(Conjunction.OR);
            else
                added_or_first = false;
            String condition = cond.getValue();
            if (condition != null) {
                model = addCondition(model, cond.getValue());
                if (objects != null)
                    objects.add(cond.getKey());
            }
        }
        return model;
    }

    private Conjunction getGatewayConjunction(Gateway gateway) {
        if (gateway == null)
            return null;
        if (gateway instanceof ExclusiveGateway || gateway instanceof EventBasedGateway)
            return Conjunction.OR;
        else if (gateway instanceof InclusiveGateway || gateway instanceof ParallelGateway)
            return Conjunction.AND;
        return null;
    }

    private void extractRuleT1(SequenceFlow el) {
        FlowNode before = el.getSource();
        FlowNode after = el.getTarget();
        if (getActivityText(before) == null || getActivityText(after) == null)
            return;
        if (!((isActivityElement(before) || before instanceof StartEvent || isIntermediaryEvent(before)) &&
              (isActivityElement(after) || after instanceof EndEvent || isIntermediaryEvent(after))))
            return;
        Map<ModelElementInstance, String> subjectsFrom = getSubjectNames(before, false);
        Map<ModelElementInstance, String> subjectsTo = getSubjectNames(after, false);
        for (Entry<ModelElementInstance, String> subjectFrom : subjectsFrom.entrySet()) {
            for (Entry<ModelElementInstance, String> subjectTo : subjectsTo.entrySet()) {
                SBVRExpressionModel candidate = new SBVRExpressionModel()
                        .addRuleExpression(SBVRExpressionModel.RuleType.OBLIGATION);
                candidate = addActivity(candidate, after, subjectTo.getValue());
                candidate = candidate.addRuleConditional(Conditional.AFTER);
                candidate = addActivity(candidate, before, subjectFrom.getValue());
                String incomingCondition = getCondition(el);
                if (incomingCondition != null) {
                    candidate = candidate.addConjunction(Conjunction.AND).addRuleConditional(Conditional.IF);
                    candidate = addCondition(candidate, incomingCondition);
                }
                List<Object> src = new ArrayList<>();
                if (subjectFrom.getKey() != null)
                    src.add(subjectFrom.getKey());
                src.add(after);
                if (subjectFrom.getKey() != null)
                    src.add(subjectFrom.getKey());
                src.add(before);
                SourceEntry source = new SourceEntry(src, "T1");
                br_candidates.add(source, candidate);
                br_candidates.setAutomaticExtraction(source);
            }
        }
    }

    private void extractRuleT2(Gateway el) {
        extractRulesWithGatewaysSimplified(el, el instanceof ExclusiveGateway, Conjunction.OR, "T2");
        //extractRuleWithGateways(el, el instanceof ExclusiveGateway, Conjunction.OR);
    }

    private void extractRuleT3(Gateway el) {
        extractRulesWithGatewaysSimplified(el, el instanceof InclusiveGateway, Conjunction.AND, "T3");
        //extractRuleWithGateways(el, el instanceof InclusiveGateway, Conjunction.AND);
    }

    private void extractRulesWithGatewaysSimplified(Gateway el, boolean checkGatewayCondition, Conjunction conjunction, String rule) {
        if (!checkGatewayCondition)
            return;
        GatewayNeighborhood tuple = gatewayNeighborhoods.get(el);
        if (tuple.incomingActivities.isEmpty() && tuple.outgoingActivities.isEmpty())
            return;
        if (!tuple.outgoingActivities.isEmpty()) {
            //Calculate joint conditions for exclusion by negation, if no conditions are set
            SBVRExpressionModel jointConditions = new SBVRExpressionModel();
            boolean first_added = true;
            for (Map<SequenceFlow, String> outEntry: tuple.outgoingConditions.values()) {
                for (String condition: outEntry.values())
                    if (condition != null && condition.trim().length() > 0) {
                        if (!first_added)
                            jointConditions.addConjunction(Conjunction.OR);
                        else
                            first_added = false;
                        jointConditions = addCondition(jointConditions, condition);
                    }
            }
            for (Entry<FlowNode, ActivityNeighborhood> actOut: tuple.outgoingActivities.entrySet()) {
                Map<ModelElementInstance, String> subjectsOut = actOut.getValue().activitySubjects;
                for (Entry<ModelElementInstance, String> subjectOut: subjectsOut.entrySet()) {
                    if (tuple.incomingActivities.isEmpty())
                        continue;
                    SBVRExpressionModel partial = new SBVRExpressionModel();
                    List<Object> objects = new ArrayList<>();
                    objects.add(actOut.getKey());
                    if (subjectOut.getKey() != null)
                        objects.add(subjectOut.getKey());
                    partial = addActivity(partial, actOut.getKey(), subjectOut.getValue());
                    partial.addRuleConditional(Conditional.AFTER);
                    first_added = true;
                    for (Entry<FlowNode, ActivityNeighborhood> incNode: tuple.incomingActivities.entrySet()) {
                        Map<ModelElementInstance, String> subjectsIn = incNode.getValue().activitySubjects;
                        for (Entry<ModelElementInstance, String> subjectIn: subjectsIn.entrySet()) {
                            if (!first_added)
                                partial.addConjunction(conjunction);
                            else
                                first_added = false;
                            partial = addActivity(partial, incNode.getKey(), subjectIn.getValue());
                            if (subjectIn.getKey() != null)
                                objects.add(subjectIn.getKey());
                            SBVRExpressionModel conditionModel = createMultipleConditions(incNode.getValue().outgoingConditions.get(el), objects);
                            if (!conditionModel.isEmpty())
                                partial.addRuleConditional(Conditional.IF).addIdentifiedExpression(conditionModel);
                        }
                    }
                    SBVRExpressionModel conditionModel = createMultipleConditions(actOut.getValue().incomingConditions.get(el), objects);
                    SBVRExpressionModel candidate = new SBVRExpressionModel();
                    if (!conditionModel.isEmpty()) {
                        candidate.addRuleExpression(RuleType.OBLIGATION)
                                .addIdentifiedExpression(partial)
                                .addConjunction(Conjunction.AND)
                                .addRuleConditional(Conditional.IF)
                                .addIdentifiedExpression(conditionModel);
                    } else {
                        RuleType ruleType = RuleType.OBLIGATION;
                        // Check if activity has no default incoming sequence flows from exclusive gateway; otherwise, set rule type to "PERMISSION"
                        if (el instanceof ExclusiveGateway && !tuple.outgoingDefault.containsKey(actOut.getKey()))
                                ruleType = RuleType.PERMISSION;
                        candidate.addRuleExpression(ruleType).addIdentifiedExpression(partial);
                        if (!jointConditions.isEmpty())
                            candidate.addConjunction(Conjunction.AND)
                                    .addRuleConditional(Conditional.IF_NOT)
                                    .addBracket(Bracket.LEFT)
                                    .addIdentifiedExpression(jointConditions)
                                    .addBracket(Bracket.RIGHT);
                    }
                    SourceEntry source = new SourceEntry(objects, rule);
                    br_candidates.add(source, candidate);
                    br_candidates.setAutomaticExtraction(source);
                }
            }
        }
    }

    private SBVRExpressionModel addTasksWithConditions(SBVRExpressionModel candidate, Map<FlowNode, ActivityNeighborhood> tasksData,
                                                       boolean incomingActivities, List<Object> objects, Conjunction conjunction, Gateway gate) {
        List<Object> tasksDefault = new ArrayList<>();
        boolean added_first = true;
        boolean rules_added = false;
        for (Entry<FlowNode, ActivityNeighborhood> entryOut : tasksData.entrySet()) {
            Map<ModelElementInstance, String> subjectsOut = entryOut.getValue().activitySubjects;
            Map<FlowNode, Map<SequenceFlow, String>> conditionsOut = entryOut.getValue().incomingConditions;
            Map<SequenceFlow, String> gateConditions = conditionsOut.get(gate);
            if (gateConditions != null) {
                for (Entry<SequenceFlow, String> cond : gateConditions.entrySet())
                    if (cond.getValue() == null)
                        gateConditions.remove(cond.getKey());
            }
            for (Entry<ModelElementInstance, String> subjectOut : subjectsOut.entrySet()) {
                // Add verb concept from rule and subject (lane, resource, etc.)
                if (subjectOut.getKey() != null)
                    objects.add(subjectOut.getKey());
                objects.add(entryOut.getValue().activity);
                if (gateConditions != null && !gateConditions.isEmpty()) {
                    if (!added_first)
                        candidate.addUnidentifiedText(",").addConjunction(conjunction);
                    else
                        added_first = false;
                    candidate = addActivity(candidate, entryOut.getValue().activity, subjectOut.getValue());
                    // Add verb concepts from conditions
                    SBVRExpressionModel conditionModel = createMultipleConditions(gateConditions, objects);
                    if (!conditionModel.isEmpty())
                        candidate.addConjunction(Conjunction.AND)
                                .addRuleConditional(Conditional.IF)
                                .addIdentifiedExpression(conditionModel);
                    rules_added = true;
                } else {
                    // No conditions are present, process as default
                    String outTaskText = extractElementText(entryOut.getValue().activity);
                    String vbTextRepr = subjectOut.getValue() != null ? (subjectOut.getValue() + " " + outTaskText) : outTaskText;
                    if (vbTextRepr != null) {
                        SBVRExpressionModel taskModel = getVerbConcept(vbTextRepr);
                        tasksDefault.add(taskModel != null ? taskModel : vbTextRepr);
                    }
                }
            }
        }
        if (!tasksDefault.isEmpty()) {
            if (rules_added)
                candidate.addUnidentifiedText(",").addRuleConditional(Conditional.OTHERWISE);
            added_first = true;
            // If we have inclusive converging gateway, conditions will be combined with AND
            if (incomingActivities)
                conjunction = Conjunction.AND;
            for (Object model : tasksDefault) {
                if (!added_first)
                    candidate.addConjunction(conjunction);
                else
                    added_first = false;
                if (model instanceof SBVRExpressionModel)
                    candidate.addIdentifiedExpression((SBVRExpressionModel) model);
                else
                    candidate.addUnidentifiedText(model.toString());
            }
        }
        return candidate;
    }

    private void extractRuleT4(EventBasedGateway el) {
        for (SequenceFlow edgeInc: el.getIncoming()) {
            if (isActivityElement(edgeInc.getSource()) && getActivityText(edgeInc.getSource()) != null) {
                Map<ModelElementInstance, String> subjectsInc = getSubjectNames(edgeInc.getSource(), false);
                for (Entry<ModelElementInstance, String> subjectInc: subjectsInc.entrySet()) {
                    for (SequenceFlow edgeOut: el.getOutgoing()) {
                        if (isIntermediaryEvent(edgeOut.getTarget()) && getActivityText(edgeOut.getTarget()) != null) {
                            SBVRExpressionModel model = new SBVRExpressionModel().addRuleExpression(RuleType.PERMISSION);
                            model = addActivity(model, edgeOut.getTarget(), null);
                            model.addRuleConditional(Conditional.AFTER);
                            model = addActivity(model, edgeInc.getSource(), subjectInc.getValue());
                            String incomingCondition = getCondition(edgeInc);
                            if (incomingCondition != null) {
                                model.addConjunction(Conjunction.AND).addRuleConditional(Conditional.IF);
                                model = addCondition(model, incomingCondition);
                            }
                            String outgoingCondition = getCondition(edgeOut);
                            if (outgoingCondition != null) {
                                model.addConjunction(Conjunction.AND).addRuleConditional(Conditional.IF);
                                model = addCondition(model, outgoingCondition);
                            }
                            List<Object> src = new ArrayList<>(Arrays.asList(edgeOut.getTarget(), edgeInc.getSource()));
                            if (subjectInc.getKey() != null)
                                src.add(subjectInc.getKey());
                            SourceEntry source = new SourceEntry(src, "T4");
                            br_candidates.add(source, model);
                            br_candidates.setAutomaticExtraction(source);
                        }
                    }
                }
            }
        }
    }

    private void extractRuleT5(ParallelGateway el) {
        GatewayNeighborhood tuple = gatewayNeighborhoods.get(el);
        if (tuple.incomingActivities.isEmpty() && tuple.outgoingActivities.isEmpty())
            return;
        if (!tuple.outgoingActivities.isEmpty()) {
            for (Entry<FlowNode, ActivityNeighborhood> actOut: tuple.outgoingActivities.entrySet()) {
                Map<ModelElementInstance, String> subjectsOut = actOut.getValue().activitySubjects;
                for (Entry<ModelElementInstance, String> subjectOut: subjectsOut.entrySet()) {
                    if (tuple.incomingActivities.isEmpty())
                        continue;
                    SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION);
                    List<Object> objects = new ArrayList<>();
                    objects.add(actOut.getKey());
                    if (subjectOut.getKey() != null)
                        objects.add(subjectOut.getKey());
                    candidate = addActivity(candidate, actOut.getKey(), subjectOut.getValue());
                    SBVRExpressionModel conditionModel = createMultipleConditions(actOut.getValue().incomingConditions.get(el), objects);
                    if (!conditionModel.isEmpty())
                        candidate.addConjunction(Conjunction.AND)
                                .addRuleConditional(Conditional.IF)
                                .addIdentifiedExpression(conditionModel);
                    candidate.addRuleConditional(Conditional.AFTER);
                    boolean first_added = true;
                    for (Entry<FlowNode, ActivityNeighborhood> incNode: tuple.incomingActivities.entrySet()) {
                        Map<ModelElementInstance, String> subjectsIn = incNode.getValue().activitySubjects;
                        for (Entry<ModelElementInstance, String> subjectIn: subjectsIn.entrySet()) {
                            if (!first_added)
                                candidate.addConjunction(Conjunction.AND);
                            else
                                first_added = false;
                            candidate = addActivity(candidate, incNode.getKey(), subjectIn.getValue());
                            objects.add(incNode.getKey());
                            if (subjectIn.getKey() != null)
                                objects.add(subjectIn.getKey());
                            conditionModel = createMultipleConditions(incNode.getValue().outgoingConditions.get(el), objects);
                            if (!conditionModel.isEmpty())
                                candidate.addConjunction(Conjunction.AND)
                                        .addRuleConditional(Conditional.IF)
                                        .addIdentifiedExpression(conditionModel);
                        }
                    }
                    SourceEntry source = new SourceEntry(objects, "T5");
                    br_candidates.add(source, candidate);
                    br_candidates.setAutomaticExtraction(source);
                }
            }
        }
    }

    private void extractRuleT6(BoundaryEvent el) {
        boolean isInterrupting = el.cancelActivity();
        Activity activity = el.getAttachedTo();
        Map<ModelElementInstance, String> activitySubjects = getSubjectNames(activity, true);
        for (Entry<ModelElementInstance, String> subject: activitySubjects.entrySet()) {
            String boundaryText = extractElementText(el);
            if (boundaryText == null)
                continue;
            SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(RuleType.PERMISSION);
            candidate = addCondition(candidate, boundaryText).addRuleConditional(Conditional.WHEN);
            candidate = addActivity(candidate, activity, subject.getValue());
            List<Object> src = new ArrayList<>();
            if (subject.getKey() != null)
                src.add(subject.getKey());
            src.add(activity);
            src.add(el);
            SourceEntry source = new SourceEntry(src, "T6");
            br_candidates.add(source, candidate);
            br_candidates.setAutomaticExtraction(source);
            for (SequenceFlow outNode: el.getOutgoing()) {
                FlowNode outTask = outNode.getTarget();
                if (isActivityElement(outTask)) {
                    if (getActivityText(outTask) == null)
                        continue;
                    candidate = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION);
                    candidate = addActivity(candidate, outTask, subject.getValue())
                            .addRuleConditional(Conditional.AFTER);
                    candidate.addBracket(Bracket.LEFT);
                    candidate = addCondition(candidate, getProperName(el))
                            .addRuleConditional(Conditional.WHEN);
                    candidate = addActivity(candidate, activity, subject.getValue());
                    candidate.addBracket(Bracket.RIGHT);
                    List<Object> srcOut = new ArrayList<>();
                    if (subject.getKey() != null)
                        srcOut.add(subject.getKey());
                    srcOut.addAll(Arrays.asList(activity, el, outTask));
                    source = new SourceEntry(srcOut, "T6");
                    br_candidates.add(source, candidate);
                    br_candidates.setAutomaticExtraction(source);
                }
            }
            if (!isInterrupting) {
                candidate = new SBVRExpressionModel().addRuleExpression(RuleType.PROHIBITION);
                candidate = addActivity(candidate, activity, subject.getValue())
                        .addRuleConditional(Conditional.AFTER);
                candidate = addCondition(candidate, getProperName(el));
                src = new ArrayList<>();
                if (subject.getKey() != null)
                    src.add(subject.getKey());
                src.add(activity);
                src.add(el);
                source = new SourceEntry(src, "T6");
                br_candidates.add(source, candidate);
                br_candidates.setAutomaticExtraction(source);
            }
        }
    }


    private void extractRuleT7(Activity el) {
        if (isActivityElement(el))
            extractTasksWithDataObjects(el, false, "is produced", "is provided to", "T7");
    }

    private void extractRuleT8(Activity el) {
        if (isActivityElement(el))
            extractTasksWithDataObjects(el, true, "is available to", "is provided with data", "T8");
    }

    private void extractTasksWithDataObjects(Activity el, boolean checkDataStore, String reservedVerb1, String reservedVerb2, String rule) {
        // Outgoing tasks with data objects
        List<ItemAwareElement> dataObjects = new ArrayList<>();
        for (DataOutputAssociation outAssoc: el.getDataOutputAssociations()) {
            ItemAwareElement dataObj = outAssoc.getTarget();
            if (checkDataStore ? dataObj instanceof DataStore : isDataObject(dataObj))
                dataObjects.add(dataObj);
        }
        processOutgoingConnectionsWithDataObjects(dataObjects, el, checkDataStore, reservedVerb1, rule);
        // Incoming tasks with data objects
        dataObjects.clear();
        for (DataInputAssociation inAssoc: el.getDataInputAssociations()) {
            for (ItemAwareElement dataObj: inAssoc.getSources())
                if (checkDataStore ? dataObj instanceof DataStore : isDataObject(dataObj))
                    dataObjects.add(dataObj);
        }
        if (dataObjects.isEmpty())
            return;
        processIncomingConnectionsWithDataObjects(dataObjects, el, checkDataStore, reservedVerb2, rule);
    }


    private void processOutgoingConnectionsWithDataObjects(List<ItemAwareElement> dataObjects, Activity taskElement, boolean checkDataStore,
                                                           String reservedVerb1, String rule) {
        if (dataObjects.isEmpty() || getActivityText(taskElement) == null)
            return;
        Map<ModelElementInstance, String> taskSubjects = getSubjectNames(taskElement, false);
        if (!dataObjects.isEmpty()) {
            for (Entry<ModelElementInstance, String> taskSubject : taskSubjects.entrySet()) {
                SBVRExpressionModel subjectConcept = getGeneralConcept(taskSubject.getValue());
                SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION);
                boolean added_first_obj = true;
                for (ItemAwareElement dataObj : dataObjects) {
                    if (!added_first_obj)
                        candidate = candidate.addConjunction(Conjunction.AND);
                    else
                        added_first_obj = false;
                    String objText = getElementName(dataObj);
                    if (objText == null)
                        continue;
                    DataState state = dataObj.getDataState();
                    if (state == null) {
                        SBVRExpressionModel objConcept = getGeneralConcept(objText);
                        if (objConcept != null)
                            candidate.addIdentifiedExpression(objConcept);
                        else
                            candidate.addUnidentifiedText(objText);
                        candidate = candidate.addVerbConcept(reservedVerb1, true);
                        if (checkDataStore)
                            candidate = subjectConcept != null ?
                                    candidate.addIdentifiedExpression(subjectConcept) :
                                    candidate.addUnidentifiedText(taskSubject.getValue());
                    } else {
                        String stateText = getElementName(state);
                        if (stateText == null)
                            continue;
                        SBVRExpressionModel objConcept = getGeneralConcept(stateText + " " + objText);
                        candidate = objConcept != null ?
                                candidate.addIdentifiedExpression(objConcept) :
                                candidate.addUnidentifiedText(stateText + " " + objText);
                        candidate = candidate.addVerbConcept(reservedVerb1, true);
                        if (checkDataStore)
                            candidate = subjectConcept != null ?
                                    candidate.addIdentifiedExpression(subjectConcept) :
                                    candidate.addUnidentifiedText(taskSubject.getValue());
                        }
                }
                candidate = candidate.addRuleConditional(Conditional.WHEN);
                candidate = addActivity(candidate, taskElement, taskSubject.getValue());
                List<Object> srcElements = new ArrayList<>(dataObjects);
                if (taskSubject.getKey() != null)
                    srcElements.add(taskSubject.getKey());
                srcElements.add(taskElement);
                SourceEntry source = new SourceEntry(srcElements, rule);
                br_candidates.add(source, candidate);
                br_candidates.setAutomaticExtraction(source);
            }
        }
    }


    private void processIncomingConnectionsWithDataObjects(List<ItemAwareElement> dataObjects, Activity taskElement, boolean checkDataStore,
                                                           String reservedVerb2, String rule) {
        if (dataObjects.isEmpty() || getActivityText(taskElement) == null)
            return;
        Map<ModelElementInstance, String> taskSubjects = getSubjectNames(taskElement, false);
        for (Entry<ModelElementInstance, String> taskSubject : taskSubjects.entrySet()) {
            SBVRExpressionModel subjectConcept = getGeneralConcept(taskSubject.getValue());
            SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(RuleType.PERMISSION);
            candidate = addActivity(candidate, taskElement, taskSubject.getValue())
                    .addRuleConditional(Conditional.ONLY_IF);
            boolean added_first_obj = true;
            for (ItemAwareElement dataObj: dataObjects) {
                if (!added_first_obj)
                    candidate = candidate.addConjunction(Conjunction.AND);
                else
                    added_first_obj = false;
                String objText = getElementName(dataObj);
                if (objText == null)
                    continue;
                DataState state = dataObj.getDataState();
                if (state == null) {
                    SBVRExpressionModel objConcept = getGeneralConcept(objText);
                    if (objConcept != null)
                        candidate.addIdentifiedExpression(objConcept);
                    else
                        candidate.addUnidentifiedText(objText);
                    candidate = candidate.addVerbConcept(reservedVerb2, true);
                    if (!checkDataStore)
                        candidate = subjectConcept != null ?
                                candidate.addIdentifiedExpression(subjectConcept) :
                                candidate.addUnidentifiedText(taskSubject.getValue());
                } else {
                    String stateText = getElementName(state);
                    if (stateText == null)
                        continue;
                    SBVRExpressionModel objConcept = getGeneralConcept(stateText + " " + objText);
                    candidate = objConcept != null ?
                            candidate.addIdentifiedExpression(objConcept) :
                            candidate.addUnidentifiedText(stateText + " " + objText);
                    candidate = candidate.addVerbConcept(reservedVerb2, true);
                    if (!checkDataStore)
                        candidate = subjectConcept != null ?
                                candidate.addIdentifiedExpression(subjectConcept) :
                                candidate.addUnidentifiedText(taskSubject.getValue());
                }
            }
            List<Object> srcElements = new ArrayList<>();
            srcElements.add(taskElement);
            if (taskSubject.getKey() != null)
                srcElements.add(taskSubject.getKey());
            srcElements.addAll(dataObjects);
            SourceEntry source = new SourceEntry(srcElements, rule);
            br_candidates.add(source, candidate);
            br_candidates.setAutomaticExtraction(source);
        }
    }

    private void extractRuleT9(MessageFlow el) {
        Message conveyed = el.getMessage();
        if (conveyed == null)
            extractMessageFlow(el, extractElementText(el));
        else
            extractMessageFlow(el, conveyed);
    }

    private void extractMessageFlow(MessageFlow el, Object convObj) {
        InteractionNode source = el.getSource();
        if (source == null)
            return;
        InteractionNode target = el.getTarget();
        if (isLaneElement(source) && isLaneElement(target)) {
            addMessageFlowBetweenLanes(el, convObj, source, null, target, null, "sends", "to", RuleType.PERMISSION);
            addMessageFlowBetweenLanes(el, convObj, target, null, source, null, "receives", "from", RuleType.PERMISSION);
        } else if ((isActivityElement(source) || isEventElement(source)) && isLaneElement(target)) {
            if (getActivityText((FlowElement) source) == null)
                return;
            Set<ModelElementInstance> subjects = getSubjectNames(source, true).keySet();
            RuleType ruleType = RuleType.PERMISSION;
            if (el instanceof SendTask || el instanceof ReceiveTask)
                ruleType = RuleType.OBLIGATION;
            for (ModelElementInstance subject: subjects) {
                addMessageFlowBetweenLanes(el, convObj, subject, (FlowNode) source, target, null, "sends", "to", ruleType);
                addMessageFlowBetweenLanes(el, convObj, target, null, subject, null, "receives", "from", RuleType.PERMISSION);
            }
        } else if (isLaneElement(source) && (isActivityElement(target) || isEventElement(target))) {
            if (getActivityText((FlowElement) target) == null)
                return;
            Set<ModelElementInstance> subjects = getSubjectNames(target, true).keySet();
            for (ModelElementInstance subject: subjects)
                addMessageFlowBetweenLanes(el, convObj, source, null, subject, (FlowNode)target, "sends", "to", RuleType.PERMISSION);
            addReceivingNodeEventRules(el, convObj, source, (FlowNode) target);
        } else if ((isActivityElement(source) || isEventElement(source)) && (isActivityElement(target) || isEventElement(target))) {
            if (getActivityText((FlowElement) source) == null || getActivityText((FlowElement) target) == null)
                return;
            RuleType ruleType = RuleType.PERMISSION;
            if (source instanceof SendTask || source instanceof ReceiveTask)
                ruleType = RuleType.OBLIGATION;
            // Typically should be single entry per subject
            Set<ModelElementInstance> subjects = getSubjectNames(source, true).keySet();
            Set<ModelElementInstance> subjectsT = getSubjectNames(target, true).keySet();
            for (ModelElementInstance subject: subjects)
                for (ModelElementInstance subjectT: subjectsT)
                    addMessageFlowBetweenLanes(el, convObj, subject, (FlowNode) source, subjectT, (FlowNode) target, "sends", "to", ruleType);
            addReceivingNodeEventRules(el, convObj, source, (FlowNode) target);
        }
    }

    private void addMessageFlowBetweenLanes(MessageFlow el, Object convObj, ModelElementInstance subject1, FlowNode task1,
                                            ModelElementInstance subject2, FlowNode task2, String verb1, String verb2, RuleType ruleType) {
        SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(ruleType);
        if (task1 != null) {
            String objText = extractElementText(subject1);
            if (isLaneElement(subject1))
                objText = getElementName(subject1);
            candidate = addActivity(candidate, task1, objText).addRuleConditional(Conditional.WHEN);
        } else {
            if (isLaneElement(subject1))
                candidate = addLaneGeneralConcept(candidate, subject1);
            else
                candidate = addGeneralConcept(candidate, subject1);
        }
        candidate = addConveyedObject(candidate, convObj, verb1, verb2);
        if (isLaneElement(subject2))
            candidate = addLaneGeneralConcept(candidate, subject2);
        else
            candidate = addGeneralConcept(candidate, subject2);
        if (task2 != null) {
            candidate = candidate.addRuleConditional(Conditional.WHEN);
            String objText = extractElementText(subject2);
            if (isLaneElement(subject2))
                objText = getElementName(subject2);
            candidate = addActivity(candidate, task2, objText);
        }
        List<Object> source = new ArrayList<>();
        if (subject1 != null)
            source.add(subject1);
        source.add(el);
        if (subject2 != null)
            source.add(subject2);
        if (task1 != null)
            source.add(task1);
        if (task2 != null)
            source.add(task2);
        SourceEntry src = new SourceEntry(source, "T9");
        br_candidates.add(src, candidate);
        br_candidates.setAutomaticExtraction(src);
    }

    private SBVRExpressionModel addConveyedObject(SBVRExpressionModel candidate, Object convObj, String verb1, String verb2) {
        if (convObj != null) {
            candidate = candidate.addVerbConcept(verb1, true);
            if (convObj instanceof String)
                candidate = addGeneralConcept(candidate, (String) convObj);
            else if (convObj instanceof Message)
                candidate = addGeneralConcept(candidate, (Message) convObj);
            candidate = candidate.addVerbConcept(verb2, true);
        } else
            candidate = candidate.addVerbConcept(verb1 + " message " + verb2, true);
        return candidate;
    }

    private void addReceivingNodeEventRules(MessageFlow el, Object convObj, ModelElementInstance source, FlowNode target) {
        Map<ModelElementInstance, String> subjects = getSubjectNames(target, true);
        RuleType ruleType = RuleType.PERMISSION;
        if (isActivityElement(target)) {
            if (target instanceof SendTask || target instanceof ReceiveTask)
                ruleType = RuleType.OBLIGATION;
            for (ModelElementInstance subject : subjects.keySet())
                if (source instanceof Activity) {
                    if (getActivityText((Activity) source) == null)
                        continue;
                    Map<ModelElementInstance, String> subjectsS = getSubjectNames(source, true);
                    for (ModelElementInstance subjectS: subjectsS.keySet())
                        addMessageFlowBetweenLanes(el, convObj, subject, null, subjectS, target, "receives", "from", ruleType);
                } else
                    addMessageFlowBetweenLanes(el, convObj, subject, null, source, target, "receives", "from", ruleType);
        } else if (isEventElement(target))
            for (ModelElementInstance subject : subjects.keySet()) {
                SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(ruleType);
                candidate = addActivity(candidate, target, null).addRuleConditional(Conditional.ONLY_WHEN);
                candidate = addGeneralConcept(candidate, subject);
                candidate = addConveyedObject(candidate, convObj, "receives", "from");
                candidate = addGeneralConcept(candidate, source);
                List<Object> src = new ArrayList<>();
                src.add(el);
                if (subject != null)
                    src.add(subject);
                src.add(source);
                src.add(target);
                SourceEntry sourceEntry = new SourceEntry(src, "T9");
                br_candidates.add(sourceEntry, candidate);
                br_candidates.setAutomaticExtraction(sourceEntry);
            }
    }

    private void extractGatewayNeighborhoods() {
        gatewayNeighborhoods = new HashMap<>();
        Collection<Gateway> gateways = modelInstance.getModelElementsByType(Gateway.class);
        for (Gateway el: gateways)
            gatewayNeighborhoods.put(el, new GatewayNeighborhood(el, true));
        gatewayNeighborhoods2 = new HashMap<>();
        for (Gateway el: gateways)
            gatewayNeighborhoods2.put(el, new GatewayNeighborhood(el, false));
    }


    Object[] getRuleWithGateways(Gateway el, Conjunction conjunction, Gateway targetGateway) {
        GatewayNeighborhood tuple = gatewayNeighborhoods.get(el);
        List<Object> objects = new ArrayList<>();
        List<String> representations = new ArrayList<>();
        SBVRExpressionModel candidate = new SBVRExpressionModel();
        Object[] results = new Object[3];
        results[0] = candidate;
        results[1] = objects;
        results[2] = representations;
        if (tuple.incomingActivities.isEmpty() && tuple.outgoingActivities.isEmpty())
            return results;
        if (!tuple.incomingActivities.isEmpty())
            candidate = addTasksWithConditions(candidate, tuple.incomingActivities, true, objects, conjunction, el);
        SBVRExpressionModel ruleModel = null;
        if (targetGateway != null) {
            Map<SequenceFlow, String> conditions = tuple.outgoingConditions.get(targetGateway);
            if (conditions != null)
                ruleModel = createMultipleConditions(conditions, objects);
        }
        // If sequence flow has condition, add it
        if (ruleModel != null && !ruleModel.isEmpty())
            candidate.addConjunction(Conjunction.AND).addRuleConditional(Conditional.IF)
                    .addIdentifiedExpression(ruleModel);
        else {
            // Add outgoing activities as negation condition
            if (!tuple.outgoingActivities.isEmpty()) {
                candidate.addRuleConditional(Conditional.IF_NOT).addBracket(Bracket.LEFT);
                candidate = addTasksWithConditions(candidate, tuple.outgoingActivities, false, objects, conjunction, el);
                candidate.addBracket(Bracket.RIGHT);
            }
        }
        results[0] = candidate;
        return results;
    }

    private void createPartialRules(GatewayNeighborhood nhood, BaseElement startedElement, Gateway targetGateway) {
        Gateway gateway = nhood.gatewayNode;
        Conjunction conjunction = getGatewayConjunction(gateway);
        if (conjunction == null)
            return;
        // If gateway is a boundary gateway, return atomic partial rule
        if (nhood.incomingGateways.isEmpty()) {
            Object[] results = getRuleWithGateways(gateway, conjunction, targetGateway);
            if (results == null)
                return;
            SBVRExpressionModel partial = (SBVRExpressionModel) results[0];
            nhood.partialRule = new SBVRExpressionModel().addBracket(Bracket.LEFT)
                    .addIdentifiedExpression(partial).addBracket(Bracket.RIGHT);
            nhood.partialRuleSource = (List<Object>) results[1];
            return;
        }
        nhood.partialRule = new SBVRExpressionModel();
        nhood.partialRuleSource = new ArrayList<>();
        Set<SBVRExpressionModel> defaultRules = new HashSet<>();
        Set<SBVRExpressionModel> conditionedRules = new HashSet<>();
        boolean first_added = true;
        SBVRExpressionModel modelPart = new SBVRExpressionModel();
        // Recursively process incoming gateways
        for (Entry<Gateway, GatewayNeighborhood> gatewayIn: nhood.incomingGateways.entrySet()) {
            createPartialRules(gatewayIn.getValue(), startedElement, gateway);
            // It is possible that multiple sequence flows are between the two gateways
            Map<SequenceFlow, String> conditions = nhood.incomingConditions.get(gatewayIn.getKey());
            SBVRExpressionModel ruleModel = createMultipleConditions(conditions, nhood.partialRuleSource);
            SBVRExpressionModel partialIncomingRule = gatewayIn.getValue().partialRule;
            if (partialIncomingRule.isEmpty())
                continue;
            if (ruleModel.isEmpty())
                defaultRules.add(partialIncomingRule);
            else {
                if (!first_added)
                    modelPart.addConjunction(conjunction);
                else
                    first_added = false;
                modelPart.addIdentifiedExpression(partialIncomingRule)
                        .addConjunction(Conjunction.AND)
                        .addRuleConditional(Conditional.IF)
                        .addIdentifiedExpression(ruleModel);
                conditionedRules.add(partialIncomingRule);
            }
            nhood.partialRuleSource.addAll(gatewayIn.getValue().partialRuleSource);
        }
        // Process incoming activities
        for (Entry<FlowNode, ActivityNeighborhood> activityIn: nhood.incomingActivities.entrySet()) {
            // Exclude element which was used as the starting point
            if (activityIn.getKey().equals(startedElement))
                continue;
            // It is possible that multiple sequence flows are between the two tasks
            Map<SequenceFlow, String> conditions = activityIn.getValue().incomingConditions.get(activityIn.getKey());
            SBVRExpressionModel ruleModel = createMultipleConditions(conditions, nhood.partialRuleSource);
            Map<ModelElementInstance, String> subjects = getSubjectNames(activityIn.getKey(), false);
            for (Entry<ModelElementInstance, String> subject : subjects.entrySet()) {
                SBVRExpressionModel partialIncomingRule = addActivity(new SBVRExpressionModel(), activityIn.getKey(), subject.getValue());
                if (partialIncomingRule.isEmpty())
                    continue;
                if (ruleModel.isEmpty())
                    defaultRules.add(partialIncomingRule);
                else {
                    if (!first_added)
                        modelPart.addConjunction(conjunction);
                    else
                        first_added = false;
                    modelPart.addIdentifiedExpression(partialIncomingRule)
                            .addRuleConditional(Conditional.IF)
                            .addIdentifiedExpression(ruleModel);
                    conditionedRules.add(partialIncomingRule);
                }
                if (subject.getKey() != null)
                    nhood.partialRuleSource.add(subject.getKey());
                nhood.partialRuleSource.add(activityIn.getKey());
            }
        }
        // Add default conditions
        if (!defaultRules.isEmpty()) {
            first_added = true;
            if (!conditionedRules.isEmpty())
                modelPart.addUnidentifiedText(",").addRuleConditional(Conditional.OTHERWISE);
            for (SBVRExpressionModel default_: defaultRules) {
                if (!first_added)
                    modelPart.addConjunction(conjunction);
                else
                    first_added = false;
                modelPart.addIdentifiedExpression(default_);
            }
        }
        // Finally, recursively combine extracted partial rule
        if (!modelPart.isEmpty())
            nhood.partialRule.addBracket(Bracket.LEFT).addIdentifiedExpression(modelPart).addBracket(Bracket.RIGHT);
    }

    private void extractComplexRule(BaseElement el) {
        if (!(isActivityElement(el) || isEventElement(el)))
            return;
        Map<ModelElementInstance, String> subjects = getSubjectNames(el, false);
        Collection<SequenceFlow> incomingEdges = null;
        if (isActivityElement(el))
            incomingEdges = ((Activity) el).getIncoming();
        else
            incomingEdges = ((Event) el).getIncoming();
        if (incomingEdges == null || incomingEdges.isEmpty())
            return;
        Set<SequenceFlow> incomingGateways = incomingEdges.stream().filter(n -> isGatewayElement(n.getSource())).collect(Collectors.toSet());
        // If activity is connected only with other activities, T1 rule will extract such rules
        if (incomingGateways.isEmpty())
            return;
        for (Entry<ModelElementInstance, String> subject : subjects.entrySet()) {
            for (SequenceFlow edge: incomingGateways) {
                Gateway gateway = (Gateway) edge.getSource();
                GatewayNeighborhood nhood = gatewayNeighborhoods.get(gateway);
                createPartialRules(nhood, el, null);
                // Create rule consequent part
                SBVRExpressionModel ruleConsequent = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION);
                List<Object> sources = new ArrayList<>();
                sources.add(el);
                if (subject.getKey() != null)
                    sources.add(subject.getKey());
                ruleConsequent = addActivity(ruleConsequent, (FlowNode) el, subject.getValue());
                ActivityNeighborhood activityNhood = nhood.outgoingActivities.get(el);
                if (activityNhood == null)
                    return;
                Map<SequenceFlow, String> conditionsOut = activityNhood.incomingConditions.get(gateway);
                SBVRExpressionModel rulePart = createMultipleConditions(conditionsOut, sources);
                if (!rulePart.isEmpty())
                    ruleConsequent.addRuleConditional(Conditional.IF).addIdentifiedExpression(rulePart);
                // Add partial rule for each of the incoming gateway as antecedent (after)
                for (GatewayNeighborhood incGateway: nhood.incomingGateways.values()) {
                    if (incGateway.partialRule == null || incGateway.partialRule.isEmpty())
                        continue;
                    SBVRExpressionModel rule = ruleConsequent.clone();
                    List<Object> sourcesCopy = new ArrayList<>(sources);
                    rule.addUnidentifiedText(",");
                    if (incGateway.partialRule.getExpressionElement(0).compareTo(Conditional.AFTER.toString()) != 0)
                        rule.addRuleConditional(Conditional.AFTER);
                    rule.addIdentifiedExpression(incGateway.partialRule);
                    sources.addAll(incGateway.partialRuleSource);
                    SourceEntry src = new SourceEntry(sourcesCopy, "Complex");
                    br_candidates.add(src, rule);
                    br_candidates.setAutomaticExtraction(src);
                }
            }
        }
    }

    @Override
    protected void extractModelVocabulary() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public String[] getMetamodelVocabularyNames() {
        return null;    // Not implemented yet!
    }

    @Override
    public String removeMetaconceptName(String name) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private String getConditionsRepresentation(Map<FlowNode, Map<SequenceFlow, String>> structConditions){
        final StringBuilder sb = new StringBuilder();
        for (Entry<FlowNode, Map<SequenceFlow, String>> entry: structConditions.entrySet()) {
            sb.append(entry.getKey().getName()).append(": [");
            if (!entry.getValue().isEmpty()) {
                for (Entry<SequenceFlow, String> condition: entry.getValue().entrySet())
                    sb.append(condition.getKey().getName()).append(" -> ").append(condition.getValue()).append(", ");
                sb.delete(sb.length() - 2, sb.length());
            }
            sb.append("]").append("\n");
        }
        return sb.toString();
    }

}

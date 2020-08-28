package org.ktu.isd.bpmn2sbvr.process;

import org.camunda.bpm.model.bpmn.BpmnModelException;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.impl.util.IoUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;


public class BpmnFileReader {

    private CustomBpmnParser bpmnParser = new CustomBpmnParser();

    public static BpmnFileReader INSTANCE = new BpmnFileReader();

    public static BpmnModelInstance readModelFromFile(File file) {
        return INSTANCE.doReadModelFromFile(file);
    }

    public static BpmnModelInstance readModelFromStream(InputStream stream) {
        return INSTANCE.doReadModelFromInputStream(stream);
    }

    protected BpmnModelInstance doReadModelFromFile(File file) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            return doReadModelFromInputStream(is);

        } catch (FileNotFoundException e) {
            throw new BpmnModelException("Cannot read model from file "+file+": file does not exist.");
        } finally {
            IoUtil.closeSilently(is);
        }
    }

    protected BpmnModelInstance doReadModelFromInputStream(InputStream is) {
        return bpmnParser.parseModelFromStream(is);
    }
}

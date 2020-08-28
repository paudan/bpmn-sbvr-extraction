package org.ktu.isd.bpmn2sbvr.process;

import org.camunda.bpm.model.bpmn.impl.BpmnModelInstanceImpl;
import org.camunda.bpm.model.bpmn.impl.BpmnParser;
import org.camunda.bpm.model.xml.impl.instance.DomDocumentImpl;
import org.camunda.bpm.model.xml.impl.util.DomUtil;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;


public class CustomBpmnParser extends BpmnParser {

    private final DocumentBuilderFactory documentBuilderFactory;

    public CustomBpmnParser() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        configureFactory(dbf);
        this.documentBuilderFactory = dbf;
    }

    public static class DomErrorHandler implements ErrorHandler {

        private static final Logger LOGGER = Logger.getLogger(DomUtil.DomErrorHandler.class.getName());

        private String getParseExceptionInfo(SAXParseException spe) {
            return "URI=" + spe.getSystemId() + " Line=" + spe.getLineNumber() + ": " + spe.getMessage();
        }

        public void warning(SAXParseException spe) {
            LOGGER.warning(getParseExceptionInfo(spe));
        }

        public void error(SAXParseException spe) {
            LOGGER.warning(getParseExceptionInfo(spe));
        }

        public void fatalError(SAXParseException spe) {
            LOGGER.warning(getParseExceptionInfo(spe));
        }
    }

    @Override
    public BpmnModelInstanceImpl parseModelFromStream(InputStream inputStream) {
        DomDocument document = null;
        synchronized(documentBuilderFactory) {
            try {
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                documentBuilder.setErrorHandler(new DomErrorHandler());
                document = new DomDocumentImpl(documentBuilder.parse(inputStream));
            } catch (ParserConfigurationException | IOException | SAXException e) {
            }
        }
        return createModelInstance(document);
    }

}
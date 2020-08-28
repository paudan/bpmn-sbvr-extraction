package org.ktu.isd.bpmn2sbvr.models;

import java.util.ArrayList;
import java.util.List;

public class SBVRExpressionModel implements Cloneable {

    public final static String CGC_FORMAT = "<span style='text-decoration: underline; color: teal;'>%s</span>"; 
    public final static String CBLANK_FORMAT = "<span>%s</span>"; 
    public final static String CVC_FORMAT = "<span style='font-style: italic; color: blue;'>%s</span>"; 
    public final static String CRC_FORMAT = "<span style='color: orange;'>%s</span>";

    public enum ExpressionType {
        GENERAL_CONCEPT, INDIVIDUAL_CONCEPT, VERB_CONCEPT, RULE_TYPE, RULE_CONDITIONAL, RULE_CONJUNCTION, BRACKET, GENERIC
    }

    public enum RuleType {
        OBLIGATION("It is obligatory that"), 
        PERMISSION("It is permitted that"),
        PROHIBITION("It is prohibited that");

        private final String expr;

        RuleType(String expr) {
            this.expr = expr;
        }

        public String toString() {
            return expr;
        }
    }

    public enum Conditional {
        IF("if"),
        AFTER("after"),
        WHEN("when"),
        OTHERWISE("otherwise"),
        ONLY_IF("only if"),
        IF_NOT("if not"),
        ONLY_WHEN("only when"),
        NOT("not");

        private final String expr;

        Conditional(String expr) {
            this.expr = expr;
        }

        public String toString() {
            return expr;
        }
    }

    public enum Conjunction {
        AND("and"),
        OR("or");

        private final String expr;

        Conjunction(String expr) {
            this.expr = expr;
        }

        public String toString() {
            return expr;
        }
    }

    public enum Bracket {
        LEFT("("),
        RIGHT(")");

        private final String expr;

        Bracket(String expr) {
            this.expr = expr;
        }

        public String toString() {
            return expr;
        }
    }

    // Original data
    private List<ExpressionType> types;
    private List<String> expressions;
    private boolean auto;
    private SBVRExpressionModel generalConcept;
    private List<SBVRExpressionModel> synonymousForms;
    private boolean mmVocConcept;           // Is it a SBVR concept which belongs metamodel vocabulary?
    private List<Boolean> identified;   	// Identified by the user previously

    public SBVRExpressionModel() {
        types = new ArrayList<>();
        expressions = new ArrayList<>();
        identified = new ArrayList<>();
        synonymousForms = new ArrayList<>();
        generalConcept = null;
        // By default, this SBVRExpressionModel does not represent a model vocabulary concept
        mmVocConcept = false;       
    }

    public SBVRExpressionModel addGeneralConcept(String expression, Boolean isIdentified) {
        types.add(ExpressionType.GENERAL_CONCEPT);
        expressions.add(expression);
        identified.add(isIdentified);
        return this;
    }

    public SBVRExpressionModel addIndividualConcept(String expression, Boolean isIdentified) {
        types.add(ExpressionType.INDIVIDUAL_CONCEPT);
        expressions.add(expression);
        identified.add(isIdentified);
        return this;
    }

    public SBVRExpressionModel addVerbConcept(String expression, Boolean isIdentified) {
        types.add(ExpressionType.VERB_CONCEPT);
        expressions.add(expression);
        identified.add(isIdentified);
        return this;
    }

    public SBVRExpressionModel addRuleExpression(RuleType type) {
        types.add(ExpressionType.RULE_TYPE);
        expressions.add(type.toString());
        identified.add(true);
        return this;
    }

    public SBVRExpressionModel addRuleConditional(Conditional type) {
        types.add(ExpressionType.RULE_CONDITIONAL);
        expressions.add(type.toString());
        identified.add(true);
        return this;
    }

    public SBVRExpressionModel addConjunction(Conjunction conj) {
        types.add(ExpressionType.RULE_CONJUNCTION);
        expressions.add(conj.toString());
        identified.add(true);
        return this;
    }

    public SBVRExpressionModel addBracket(Bracket bracket) {
        types.add(ExpressionType.BRACKET);
        expressions.add(bracket.toString());
        identified.add(true);
        return this;
    }

    public SBVRExpressionModel addUnidentifiedText(String expression) {
        types.add(ExpressionType.GENERIC);
        expressions.add(expression);
        identified.add(false);
        return this;
    }

    public SBVRExpressionModel addIdentifiedExpression(SBVRExpressionModel model) {
        types.addAll(model.types);
        expressions.addAll(model.expressions);
        identified.addAll(model.identified);
        return this;
    }

    public void replace(SBVRExpressionModel modification) {
        types = modification.types;
        expressions = modification.expressions;
        identified = modification.identified;
        auto = modification.auto;
        generalConcept = modification.generalConcept;
        mmVocConcept = modification.mmVocConcept;
        synonymousForms = modification.synonymousForms;
    }

    public boolean isAuto() {
        return auto;
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
    }

    public void setIdentified(boolean value) {
        for (int i = 0; i < identified.size(); i++)
            identified.set(i, value);
    }

    public boolean originalEqualsTo(SBVRExpressionModel model) {
        return expressions.equals(model.expressions) && types.equals(model.types);
    }

    public boolean equalsTo(String string) {
        return string.compareTo(toString()) == 0;
    }

    public String getExpressionElement(int index) {
        if (expressions.size() <= index)
            return null;
        return expressions.get(index);
    }

    public ExpressionType getExpressionType(int index) {
        if (types.size() <= index)
            return null;
        return types.get(index);
    }

    public int length() {
        return expressions.size();
    }

    public boolean isEmpty() {
        return expressions.isEmpty();
    }

    public String toString() {
        if (expressions.isEmpty())
            return "[Empty]";
        return String.join(" ", expressions).trim().replaceAll(" ,", ",");
    }

    public String toUnderscoreString() {
        if (expressions.isEmpty())
            return null;
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < expressions.size(); i++)
            res.append(types.get(i) == ExpressionType.RULE_TYPE ? expressions.get(i)
                    : expressions.get(i).replaceAll(" ", "_")).append(" ");
        // Remove underscore before "'" in model vocabulary concepts
        return res.toString().trim().replace("_'", " '");
    }

    public String toHTMLString(boolean addHtml, Boolean showIdentified) {
        if (expressions.isEmpty())
            return null;
        StringBuilder res = new StringBuilder();
        if (addHtml)
            res.append("<html>"); 
        for (int i = 0; i < expressions.size(); i++) 
            res.append(String.format(getFormat(types.get(i),
                    showIdentified != null ? showIdentified : identified.get(i)), expressions.get(i))).append(" "); 
        if (addHtml)
            res.append("</html>"); 
        return res.toString().trim();
    }

    private String getFormat(ExpressionType type, boolean identified) {
        if ((type == ExpressionType.GENERAL_CONCEPT || type == ExpressionType.VERB_CONCEPT) && !identified)
            return CBLANK_FORMAT;
        if (type == ExpressionType.GENERAL_CONCEPT && identified)
            return CGC_FORMAT;
        if (type == ExpressionType.VERB_CONCEPT && identified)
            return CVC_FORMAT;
        if (type == ExpressionType.RULE_TYPE || type == ExpressionType.RULE_CONDITIONAL || type == ExpressionType.RULE_CONJUNCTION)
            return CRC_FORMAT;
        return CBLANK_FORMAT;
    }

    public SBVRExpressionModel clone() {
        SBVRExpressionModel copy = new SBVRExpressionModel();
        copy.types = new ArrayList<>();
        copy.types.addAll(types);
        copy.expressions = new ArrayList<>();
        copy.expressions.addAll(expressions);
        copy.identified = new ArrayList<>();
        copy.identified.addAll(identified);
        copy.auto = auto;
        return copy;
    }

    public List<ExpressionType> getTypes() {
        return types;
    }

    public List<String> getExpressions() {
        return expressions;
    }
    
    public SBVRExpressionModel getGeneralConcept() {
        return generalConcept;
    }

    public void setGeneralConcept(SBVRExpressionModel general_concept) throws SBVRModelException {
        if (general_concept == null)
            return;
        if (general_concept.getTypes().size() == 1 && 
                general_concept.getTypes().get(0) == ExpressionType.GENERAL_CONCEPT)
            this.generalConcept = general_concept;
        else
            throw new SBVRModelException("Invalid format for general concept expression. Check whether the SBVRExpressionModel that you are trying to set is a valid SBVR general concept");
    }

    public boolean isModelVocabularyConcept() {
        return mmVocConcept;
    }

    public void setModelVocabularyConcept(boolean mmVocConcept) {
        this.mmVocConcept = mmVocConcept;
    }

    public List<SBVRExpressionModel> getSynonymousForms() {
        return synonymousForms;
    }

    public void addSynonymousForm(SBVRExpressionModel model) {
        if (model != null)
            synonymousForms.add(model);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SBVRExpressionModel))
            return false;
        if (obj == this)
            return true;
        SBVRExpressionModel other = (SBVRExpressionModel) obj;
        boolean generalConceptCond = false;
        if ((generalConcept == null && other.generalConcept == null) ||
             generalConcept != null && other.generalConcept != null && generalConcept.equals(other.generalConcept))
            generalConceptCond = true;
        return types.equals(other.types) && expressions.equals(other.expressions) &&
                identified.equals(other.identified) && generalConceptCond &&
                synonymousForms.equals(other.synonymousForms);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

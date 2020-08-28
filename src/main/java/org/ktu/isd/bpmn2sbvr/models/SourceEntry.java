package org.ktu.isd.bpmn2sbvr.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SourceEntry implements Cloneable {

    protected List<Object> source;
    protected List<String> sourceText;
    protected String rule;

    public SourceEntry() {
        this.source = new ArrayList<>();
        this.sourceText = new ArrayList<>();
    }

    public SourceEntry(List<Object> source, List<String> sourceText, String rule) {
        this.source = source;
        this.sourceText = sourceText;
        this.rule = rule;
    }

    public SourceEntry(List<Object> source, List<String> sourceText) {
        this(source, sourceText, null);
    }

    public SourceEntry(List<Object> source) {
        this(source, new ArrayList<>(), null);
    }

    public SourceEntry(List<Object> source, String sourceText) {
        this.source = source;
        this.sourceText = new ArrayList<>();
        this.sourceText.add(sourceText);
    }

    /*
        public SourceEntry(List<String> sourceText) {
            this.source = new ArrayList<>();
            for (String str : sourceText)
                this.source.add(null);
            this.sourceText = sourceText;
        }
    */
    public void addEntry(Object source, String sourceText) {
        this.source.add(source);
        this.sourceText.add(sourceText);
    }

    public void addEntry(String sourceText) {
        addEntry(null, sourceText);
    }

    public List<Object> getSourceObjects() {
        return source;
    }

    public List<String> getSourceNames() {
        return sourceText;
    }

    public String getRule() {
        return rule;
    }

    @Override
    public SourceEntry clone() {
        SourceEntry copy = new SourceEntry();
        copy.source = new ArrayList<>();
        Collections.copy(source, copy.source);
        copy.sourceText = new ArrayList<>();
        Collections.copy(sourceText, copy.sourceText);
        copy.rule = rule;
        return copy;
    }
    
    @Override
    public String toString() {
        return String.join(",", sourceText);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SourceEntry))
            return false;
        if (obj == this)
            return true;
        SourceEntry other = (SourceEntry) obj;
        return this.source.equals(other.source) && this.sourceText.equals(other.sourceText);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

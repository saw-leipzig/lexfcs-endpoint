package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.beans.Field;

public class FieldValue {
    // Solr field type
    @Field
    public String _type;

    // actual value/content
    @Field
    public String value;

    // lex value attributes
    @Field
    public String xmlId;
    @Field
    public String xmlLang;
    @Field
    public String langUri;
    @Field
    public boolean preferred;
    @Field
    public String ref;
    @Field
    public List<String> idRefs;
    @Field
    public String vocabRef;
    @Field
    public String vocabValueRef;
    @Field
    public String type;

    // citation
    @Field
    public String source;
    @Field
    public String sourceRef;
    @Field
    public String date;

    public FieldValue() {
    }

    public FieldValue(String value) {
        this.value = value;
    }

    public Map<String, String> getAttributes() {
        Map<String, String> attributes = new HashMap<>();

        if (xmlId != null && !xmlId.isEmpty()) {
            attributes.put("xml:id", xmlId);
        }
        if (xmlLang != null && !xmlLang.isEmpty()) {
            attributes.put("xml:lang", xmlLang);
        }
        if (langUri != null && !langUri.isEmpty()) {
            attributes.put("langUri", langUri);
        }
        if (preferred == true) {
            attributes.put("preferred", String.valueOf(preferred));
        }
        if (ref != null && !ref.isEmpty()) {
            attributes.put("ref", ref);
        }
        if (idRefs != null && !idRefs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String idRef : idRefs) {
                if (idRef != null && !idRef.trim().isEmpty()) {
                    sb.append(idRef.trim()).append(' ');
                }
            }
            attributes.put("idRefs", sb.toString().trim());
        }
        if (vocabRef != null && !vocabRef.isEmpty()) {
            attributes.put("vocabRef", vocabRef);
        }
        if (vocabValueRef != null && !vocabValueRef.isEmpty()) {
            attributes.put("vocabValueRef", vocabValueRef);
        }
        if (type != null && !type.isEmpty()) {
            attributes.put("type", type);
        }
        if (source != null && !source.isEmpty()) {
            attributes.put("source", source);
        }
        if (sourceRef != null && !sourceRef.isEmpty()) {
            attributes.put("sourceRef", sourceRef);
        }
        if (date != null && !date.isEmpty()) {
            attributes.put("date", date);
        }

        return attributes;
    }

    @Override
    public String toString() {
        return "FieldValue [type=" + _type.replaceFirst("value_", "") + ", value=" + value + ", attributes="
                + getAttributes() + "]";
    }

}

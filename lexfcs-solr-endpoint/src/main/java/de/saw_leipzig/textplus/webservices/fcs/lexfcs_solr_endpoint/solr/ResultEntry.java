package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

public class ResultEntry {
    // must be "entry"
    @Field
    public String _type;
    // internal Solr id
    @Field
    public String id;

    // lex field values
    @Field("values")
    protected List<SolrDocument> rawValues;
    public List<FieldValue> values;

    // entry id?
    @Field
    public String xmlId;

    // attributes on lex entry
    @Field
    public String xmlLang;
    @Field
    public String langUri;

    // meta fields
    @Field
    public String landingpage;
    @Field
    public String dataview_hits;
    @Field
    public String dataview_lexhits;

    public ResultEntry() {
    }

    // ---------------------------------------------------------------------
    // access values from certain field type

    public List<FieldValue> getListsOfType(String fieldType) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        // if no field type, then all
        if (fieldType == null) {
            return Collections.unmodifiableList(values);
        }
        // filter values by field type
        final String _type = "value_" + fieldType;
        return values.stream().filter(v -> _type.equals(v._type)).collect(Collectors.toList());
    }

    public List<FieldValue> getLang() {
        return getListsOfType("lang");
    }

    public List<FieldValue> getEntryId() {
        return getListsOfType("entryId");
    }

    public List<FieldValue> getLemma() {
        return getListsOfType("lemma");
    }

    public List<FieldValue> getTranslation() {
        return getListsOfType("translation");
    }

    public List<FieldValue> getTranscription() {
        return getListsOfType("transcription");
    }

    public List<FieldValue> getPhonetic() {
        return getListsOfType("phonetic");
    }

    public List<FieldValue> getDefinition() {
        return getListsOfType("definition");
    }

    public List<FieldValue> getEtymology() {
        return getListsOfType("etymology");
    }

    public List<FieldValue> getCase() {
        return getListsOfType("case");
    }

    public List<FieldValue> getNumber() {
        return getListsOfType("number");
    }

    public List<FieldValue> getGender() {
        return getListsOfType("gender");
    }

    public List<FieldValue> getPos() {
        return getListsOfType("pos");
    }

    public List<FieldValue> getBaseform() {
        return getListsOfType("baseform");
    }

    public List<FieldValue> getSegmentation() {
        return getListsOfType("segmentation");
    }

    public List<FieldValue> getSentiment() {
        return getListsOfType("sentiment");
    }

    public List<FieldValue> getFrequency() {
        return getListsOfType("frequency");
    }

    public List<FieldValue> getAntonym() {
        return getListsOfType("antonym");
    }

    public List<FieldValue> getHyponym() {
        return getListsOfType("hyponym");
    }

    public List<FieldValue> getHypernym() {
        return getListsOfType("hypernym");
    }

    public List<FieldValue> getMeronym() {
        return getListsOfType("meronym");
    }

    public List<FieldValue> getHolonym() {
        return getListsOfType("holonym");
    }

    public List<FieldValue> getSynonym() {
        return getListsOfType("synonym");
    }

    public List<FieldValue> getRelated() {
        return getListsOfType("related");
    }

    public List<FieldValue> getRef() {
        return getListsOfType("ref");
    }

    public List<FieldValue> getSenseRef() {
        return getListsOfType("senseRef");
    }

    public List<FieldValue> getCitation() {
        return getListsOfType("citation");
    }

    // ---------------------------------------------------------------------

    @Override
    public String toString() {
        return "ResultEntry [id=" + id + ", values=" + values + "]";
    }
}

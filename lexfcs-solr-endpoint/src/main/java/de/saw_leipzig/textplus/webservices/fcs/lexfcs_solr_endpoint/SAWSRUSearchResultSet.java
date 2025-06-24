package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr.FieldValue;
import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr.ResultEntry;
import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr.SearchResultSet;
import eu.clarin.sru.server.SRUConstants;
import eu.clarin.sru.server.SRUDiagnostic;
import eu.clarin.sru.server.SRUDiagnosticList;
import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.server.SRURequest;
import eu.clarin.sru.server.SRUSearchResultSet;
import eu.clarin.sru.server.SRUServerConfig;
import eu.clarin.sru.server.fcs.Constants;
import eu.clarin.sru.server.fcs.LexDataViewWriter;
import eu.clarin.sru.server.fcs.XMLStreamWriterHelper;

public class SAWSRUSearchResultSet extends SRUSearchResultSet {
    private static final Logger LOGGER = LogManager.getLogger(SAWSRUSearchResultSet.class);

    protected static final SAXParserFactory factory;
    static {
        factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
    }

    SRUServerConfig serverConfig = null;
    SRURequest request = null;
    private Set<String> extraDataviews;

    private SearchResultSet results;
    private int currentRecordCursor = 0;

    protected SAWSRUSearchResultSet(SRUServerConfig serverConfig, SRURequest request,
            SRUDiagnosticList diagnostics, List<String> dataviews, SearchResultSet results) {
        super(diagnostics);
        this.serverConfig = serverConfig;
        this.request = request;

        this.results = results;
        currentRecordCursor = -1;

        extraDataviews = new HashSet<>(dataviews);
    }

    @Override
    public String getRecordIdentifier() {
        return null;
    }

    @Override
    public String getRecordSchemaIdentifier() {
        return request.getRecordSchemaIdentifier() != null ? request.getRecordSchemaIdentifier()
                : Constants.CLARIN_FCS_RECORD_SCHEMA;
    }

    @Override
    public SRUDiagnostic getSurrogateDiagnostic() {
        if ((getRecordSchemaIdentifier() != null)
                && !Constants.CLARIN_FCS_RECORD_SCHEMA.equals(getRecordSchemaIdentifier())) {
            return new SRUDiagnostic(
                    SRUConstants.SRU_RECORD_NOT_AVAILABLE_IN_THIS_SCHEMA,
                    getRecordSchemaIdentifier(),
                    "Record is not available in record schema \"" +
                            getRecordSchemaIdentifier() + "\".");
        }

        return null;
    }

    @Override
    public int getTotalRecordCount() {
        return (int) results.getTotal();
    }

    @Override
    public int getRecordCount() {
        return results.getResults().size();
    }

    @Override
    public boolean nextRecord() throws SRUException {
        if (currentRecordCursor < (getRecordCount() - 1)) {
            currentRecordCursor++;
            return true;
        }
        return false;
    }

    @Override
    public void writeRecord(XMLStreamWriter writer) throws XMLStreamException {
        ResultEntry result = results.getResults().get(currentRecordCursor);

        XMLStreamWriterHelper.writeStartResource(writer, results.getPid(), null);
        XMLStreamWriterHelper.writeStartResourceFragment(writer, result.xmlId, result.landingpage);

        if (request != null && request.isQueryType(Constants.FCS_QUERY_TYPE_LEX)) {
            writeLexHitsDataview(writer, result);
            writeLexDataview(writer, result);
        } else {
            writeHitsDataview(writer, result);
        }

        XMLStreamWriterHelper.writeEndResourceFragment(writer);
        XMLStreamWriterHelper.writeEndResource(writer);
    }

    protected void writeHitsDataview(XMLStreamWriter writer, ResultEntry result) throws XMLStreamException {
        XMLStreamWriterHelper.writeStartDataView(writer, Constants.MIMETYPE_HITS);
        writer.setPrefix(Constants.XML_PREFIX_HITS, Constants.NS_HITS);
        writer.writeStartElement(Constants.NS_HITS, "Result");
        writer.writeNamespace(Constants.XML_PREFIX_HITS, Constants.NS_HITS);

        writeSolrHitsDataviewBytedXMLDoc(writer, result.dataview_hits.getBytes());

        writer.writeEndElement(); // "Result" element
        XMLStreamWriterHelper.writeEndDataView(writer);
    }

    protected void writeLexHitsDataview(XMLStreamWriter writer, ResultEntry result) throws XMLStreamException {
        XMLStreamWriterHelper.writeStartDataView(writer, Constants.MIMETYPE_HITS);
        writer.setPrefix(Constants.XML_PREFIX_HITS, Constants.NS_HITS);
        writer.writeStartElement(Constants.NS_HITS, "Result");
        writer.writeNamespace(Constants.XML_PREFIX_HITS, Constants.NS_HITS);

        writeSolrHitsDataviewBytedXMLDoc(writer, result.dataview_lexhits.getBytes());

        writer.writeEndElement(); // "Result" element
        XMLStreamWriterHelper.writeEndDataView(writer);
    }

    protected void writeLexDataview(XMLStreamWriter writer, ResultEntry result) throws XMLStreamException {
        LexDataViewWriter helper = new LexDataViewWriter(result.xmlLang, result.langUri);

        if (result.xmlId != null && !result.xmlId.isBlank()) {
            FieldValue idFV = new FieldValue();
            idFV.value = result.xmlId;
            addValues(helper, "entryId", List.of(idFV), null);

            // deduplicate entryId which already is being generated by result.xmlId
            final List<FieldValue> values = result.getEntryId();
            List<FieldValue> filtered = values.stream()
                    // does not be the same id, but if so, must at least have some additional
                    // attributes to be left, otherwise is filtered out
                    .filter(v -> !result.xmlId.equals(v.value) || !v.getAttributes().isEmpty())
                    .collect(Collectors.toList());
            addValues(helper, "entryId", filtered, null);
        } else {
            addValues(helper, "entryId", result.getEntryId(), null);
        }

        addValues(helper, "lemma", result.getLemma(), null);

        addValues(helper, "phonetic", result.getPhonetic(), null);
        addValues(helper, "translation", result.getTranslation(), null);
        addValues(helper, "transcription", result.getTranscription(), null);

        // ---------------------------

        addValues(helper, "definition", result.getDefinition(), null);
        addValues(helper, "etymology", result.getEtymology(), null);

        // ---------------------------

        addValues(helper, "case", result.getCase(), null);
        addValues(helper, "number", result.getNumber(), null);
        addValues(helper, "gender", result.getGender(), null);

        addValues(helper, "pos", result.getPos(), null);

        addValues(helper, "baseform", result.getBaseform(), null);
        addValues(helper, "segmentation", result.getSegmentation(), null);

        // ---------------------------

        addValues(helper, "sentiment", result.getSentiment(), null);
        addValues(helper, "frequency", result.getFrequency(), null);

        // ---------------------------

        addValues(helper, "antonym", result.getAntonym(), null);
        addValues(helper, "hyponym", result.getHyponym(), null);
        addValues(helper, "hypernym", result.getHypernym(), null);
        addValues(helper, "meronym", result.getMeronym(), null);
        addValues(helper, "holonym", result.getHolonym(), null);
        addValues(helper, "synonym", result.getSynonym(), null);
        addValues(helper, "related", result.getRelated(), null);

        // ---------------------------

        // duplicate information in ResourceFragment @ref
        // if (result.landingpage != null) {
        //     FieldValue landingPageRefFV = new FieldValue();
        //     landingPageRefFV.value = result.landingpage;
        //     landingPageRefFV.type = "landingpage";
        //     addValues(helper, "ref", List.of(landingPageRefFV), null);
        // }
        addValues(helper, "ref", result.getRef(), null);

        addValues(helper, "senseRef", result.getSenseRef(), null);

        // ---------------------------

        addValues(helper, "citation", result.getCitation(), null);

        helper.writeLexDataView(writer);
    }

    protected void addValues(LexDataViewWriter helper, String fieldType, List<FieldValue> values)
            throws XMLStreamException {
        addValues(helper, fieldType, values, null);
    }

    protected void addValues(LexDataViewWriter helper, String fieldType, List<FieldValue> values,
            Map<String, String> attributesForValues) throws XMLStreamException {
        if (values != null && !values.isEmpty()) {
            for (FieldValue value : values) {

                // merge attributes
                // TODO: before/after attributes (for hard overrides and soft defaults)?
                final Map<String, String> attributes = new HashMap<>();
                attributes.putAll(value.getAttributes());
                if (attributesForValues != null) {
                    attributes.putAll(attributesForValues);
                }

                helper.addValue(fieldType, value.value, attributes);
            }
        }
    }

    /**
     * Helper method for {@link #writeLexHitsDataview(XMLStreamWriter, ResultEntry)}
     * and {@link #writeHitsDataview(XMLStreamWriter, ResultEntry)} to write an XML
     * string to output. Also adds the <code>hits:</code> prefixes.
     * 
     * @param writer
     * @param bytes
     * @throws XMLStreamException
     */
    protected static void writeSolrHitsDataviewBytedXMLDoc(XMLStreamWriter writer, byte[] bytes)
            throws XMLStreamException {
        // a unique name that is unlikely to appear in the input bytes
        // it will be used to wrap the input (xml fragment) into a new document for
        // copying
        final String marker = "writeSolrHitsDataviewBytedXMLDoc";

        try {
            // remove unknown XML entities
            bytes = sanitizeEntities(bytes);

            // wrap bytes into pseudo document
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(("<" + marker + ">").getBytes());
            baos.write(bytes);
            baos.write(("</" + marker + ">").getBytes());
            bytes = baos.toByteArray();
            // LOGGER.info("bytes: {}", new String(bytes));

            // transfer content of parsed pseudo doc to the output writer
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            InputSource input = new InputSource(bais);
            SAXParser parser = factory.newSAXParser();
            parser.parse(input, new DefaultHandler() {
                public boolean isBlank(final String s) {
                    // from: org.apache.logging.log4j.util.Strings.isBlank()
                    if (s == null || s.isEmpty()) {
                        return true;
                    }
                    for (int i = 0; i < s.length(); i++) {
                        char c = s.charAt(i);
                        if (!Character.isWhitespace(c)) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public void characters(char[] ch, int start, int length) throws SAXException {
                    // LOGGER.info("characters: {}", Arrays.copyOfRange(ch, start, start + length));
                    // strip blanks
                    // TODO: maybe with indent == 0, just check for single line-breaks after element
                    // ends?
                    if (isBlank(new String(ch, start, length))) {
                        return;
                    }

                    try {
                        writer.writeCharacters(ch, start, length);
                    } catch (XMLStreamException e) {
                        throw new SAXException(e);
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) throws SAXException {
                    if (qName.equals(marker)) {
                        return;
                    }
                    try {
                        writer.writeEndElement();
                    } catch (XMLStreamException e) {
                        throw new SAXException(e);
                    }
                }

                private Map<String, String> prefixes = new HashMap<>();

                @Override
                public void startPrefixMapping(String prefix, String uri) throws SAXException {
                    super.startPrefixMapping(prefix, uri);
                    // writer.writeNamespace(prefix, uri);
                    prefixes.put(prefix, uri);
                }

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes)
                        throws SAXException {
                    if (qName.equals(marker)) {
                        return;
                    }
                    try {
                        if (qName.equals("Hit")) {
                            writer.writeStartElement(Constants.NS_HITS, qName);
                        } else {
                            writer.writeStartElement(qName);
                            // writer.writeStartElement(qName, localName, uri);
                        }
                        if (!prefixes.isEmpty()) {
                            for (Map.Entry<String, String> entry : prefixes.entrySet()) {
                                writer.writeNamespace(entry.getKey(), entry.getValue());
                            }
                            prefixes.clear();
                        }

                        for (int i = 0; i < attributes.getLength(); i++) {
                            writer.writeAttribute(attributes.getQName(i), attributes.getValue(i));
                        }
                    } catch (XMLStreamException e) {
                        throw new SAXException(e);
                    }
                }
            });
        } catch (ParserConfigurationException e) {
            throw new XMLStreamException(e);
        } catch (SAXException e) {
            throw new XMLStreamException(e);
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    protected static byte[] sanitizeEntities(final byte[] bytes) throws IOException {
        final byte entityStart = (byte) '&';
        final byte entityEnd = (byte) ';';

        int posInSrc = 0;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            // find start of entity
            if (bytes[i] == entityStart) {
                // if there is uncopies content, transfer
                if (i != posInSrc) {
                    int len = i - posInSrc;
                    baos.write(bytes, posInSrc, len);
                    posInSrc += len;
                }

                // find end of entity
                for (int j = i + 1; j < bytes.length; j++) {
                    if (bytes[j] == entityEnd) {
                        int len = j - (i + 1);
                        byte[] name = new byte[len];
                        System.arraycopy(bytes, i + 1, name, 0, len);

                        // skip entity in first loop
                        i += len + 1;
                        // skip entity in input stream
                        posInSrc += len + 2;

                        // masks or replace entities
                        switch (new String(name)) {
                            // known internal entities
                            // com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl#scanEntityReference
                            case "amp":
                            case "lt":
                            case "gt":
                            case "quot":
                            case "apos":
                                baos.write(bytes, posInSrc - (len + 2), len + 2);
                                break;

                            // known entities we can automatically replace
                            case "nbsp":
                                baos.write("&#xA0;".getBytes());
                                break;

                            // rest we must escape
                            default:
                                baos.write("&amp;".getBytes());
                                baos.write(name);
                                baos.write(';');
                                break;
                        }

                        break;
                    } else if (bytes[j] == entityStart) {
                        // skip forwards
                        i = j;
                        // transfer content from last ampersand to before current one
                        int len = i - posInSrc;
                        baos.write(bytes, posInSrc, len);
                        posInSrc += len;
                    }
                }
            }
        }
        if (bytes.length > posInSrc) {
            // transfer the remaining content
            int len = bytes.length - posInSrc;
            baos.write(bytes, posInSrc, len);
        }

        return baos.toByteArray();
    }
}

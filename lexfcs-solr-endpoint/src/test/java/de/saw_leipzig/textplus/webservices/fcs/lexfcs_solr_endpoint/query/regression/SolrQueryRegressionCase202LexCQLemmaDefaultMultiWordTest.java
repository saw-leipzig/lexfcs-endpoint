package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query.regression;

public class SolrQueryRegressionCase202LexCQLemmaDefaultMultiWordTest extends SolrQueryRegressionsTestBase {
    @Override
    public String getCaseName() {
        return "case202-lexcql-lemmadefaultmultiwords";
    }

    @Override
    public boolean usesSolrData() {
        return false;
    }
}

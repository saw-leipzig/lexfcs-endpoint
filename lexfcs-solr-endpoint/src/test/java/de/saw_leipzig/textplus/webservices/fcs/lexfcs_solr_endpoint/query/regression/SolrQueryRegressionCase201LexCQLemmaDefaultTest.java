package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query.regression;

public class SolrQueryRegressionCase201LexCQLemmaDefaultTest extends SolrQueryRegressionsTestBase {
    @Override
    public String getCaseName() {
        return "case201-lexcql-lemmadefault";
    }

    @Override
    public boolean usesSolrData() {
        return false;
    }
}

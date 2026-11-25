package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query.regression;

import static org.hamcrest.core.Is.is;
import static org.junit.Assume.assumeNotNull;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.QuickPatchThreadsFilter;
import org.apache.solr.SolrIgnoredThreadsFilter;
import org.apache.solr.SolrTestCase;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.logging.MDCSnapshot;
import org.apache.solr.util.EmbeddedSolrServerTestRule;
import org.hamcrest.MatcherAssert;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.z3950.zing.cql.CQLParseException;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakLingering;
import com.carrotsearch.randomizedtesting.rules.SystemPropertiesRestoreRule;

import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query.CQLToSolrConverter;
import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query.LexCQLToSolrConverter;
import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr.ResultEntry;
import eu.clarin.sru.fcs.qlparser.QueryParserException;
import eu.clarin.sru.server.SRUException;

@ThreadLeakFilters(defaultFilters = true, filters = { SolrIgnoredThreadsFilter.class, QuickPatchThreadsFilter.class })
@ThreadLeakLingering(linger = 1000)
public abstract class SolrQueryRegressionsTestBase extends LuceneTestCase {

    protected static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * resource location for Solr core configuration
     */
    protected static final String LEXFCS_CONFIGSET_DIR = "configsets/lexfcs/conf";
    /**
     * resource location for query test cases
     */
    protected static final String LEXFCS_QUERY_CASES_DIR = "query_regressions";

    // files for each test case
    protected static final String TEST_DOCS_XML_NAME = "docs.solr.xml";
    protected static final String TEST_FCS_CQL_QUERY_NAME = "cql.query";
    protected static final String TEST_FCS_LEXCQL_QUERY_NAME = "lexcql.query";
    protected static final String TEST_SOLR_QUERY_NAME = "solr.query";
    protected static final String TEST_RESULT_IDS_NAME = "resultids.txt";

    @ClassRule
    public static final EmbeddedSolrServerTestRule solrTestRule = new EmbeddedSolrServerTestRule();
    @ClassRule
    public static final SystemPropertiesRestoreRule sysPropsTestRule = new SystemPropertiesRestoreRule();

    // ---------------------------------------------------------------------
    // solr setup

    protected static Path REGRESSION_CASES;
    protected static Path CONFIG_HOME;
    protected static Path SOLR_HOME;

    @BeforeClass
    public static void beforeAll() throws SolrServerException, IOException {
        try {
            REGRESSION_CASES = getPath(LEXFCS_QUERY_CASES_DIR);
        } catch (RuntimeException re) {
            // skip whole test suite if test case folder is missing
            throw new AssumptionViolatedException("Regression test case folder needs to exists!");
        }
        log.debug("REGRESSION_CASES = {}", REGRESSION_CASES);

        CONFIG_HOME = getPath(LEXFCS_CONFIGSET_DIR);
        log.debug("CONFIG_HOME = {}", CONFIG_HOME);

        SOLR_HOME = LuceneTestCase.createTempDir("solrHome");
        log.debug("SOLR_HOME = {}", SOLR_HOME);

        solrTestRule.startSolr(SOLR_HOME);
        solrTestRule.newCollection().withConfigSet(CONFIG_HOME.toString()).create();
    }

    // ---------------------------------------------------------------------
    // test data

    /**
     * Specifies the case name that also links the Solr data and query files to this
     * test.
     * 
     * @return test case name
     */
    public abstract String getCaseName();

    /**
     * Returns whether this test loads Solr data and performs a search against a
     * query.
     * 
     * @return <code>true</code> to load data (requires {@link #TEST_DOCS_XML_NAME}
     *         and {@link TEST_RESULT_IDS_NAME} files) or set to <code>false</code>
     *         to skip Solr searching (for query conversion only tests)
     */
    public boolean usesSolrData() {
        return true;
    }

    protected Path caseDir;

    protected String solrQuery;
    protected String cqlQuery = null;
    protected String lexCQLQuery = null;

    protected List<String> searchResultIDs;

    // TODO: will unfortunately run before each test (as junit intended) but which
    // is not necessary for the CQL/LexCQL query conversion tests
    // NOTE: we do not add a flag here to only perform this operation once as we
    // want the possibility for derived test classes to add their own test methods
    // that may want to work with the documents and update/change them
    @Before
    public void beforeTest() throws IOException, SolrServerException {
        String name = getCaseName();
        try (var mdcSnap = MDCSnapshot.create()) {
            assert null != mdcSnap; // prevent compiler warning of unused var
            MDC.put("test.case", name);

            caseDir = REGRESSION_CASES.resolve(name);
            // check if folder exists, otherwise skip
            org.junit.Assume.assumeTrue("Test case folder must exist!", Files.exists(caseDir));
            log.debug("Load test case data for '{}' from {} ...", name, caseDir);

            Path solrQueryFile = caseDir.resolve(TEST_SOLR_QUERY_NAME);
            assertTrue("Test case SOLR query file must exist!", Files.exists(solrQueryFile));
            solrQuery = Files.readString(solrQueryFile);

            Path fcsCQLQueryFile = caseDir.resolve(TEST_FCS_CQL_QUERY_NAME);
            if (Files.exists(fcsCQLQueryFile)) {
                cqlQuery = Files.readString(fcsCQLQueryFile);
            }

            Path fcsLexCQLQueryFile = caseDir.resolve(TEST_FCS_LEXCQL_QUERY_NAME);
            if (Files.exists(fcsLexCQLQueryFile)) {
                lexCQLQuery = Files.readString(fcsLexCQLQueryFile);
            }

            if (usesSolrData()) {
                Path docsFile = caseDir.resolve(TEST_DOCS_XML_NAME);
                assertTrue("Test case SOLR documents file must exist!", Files.exists(docsFile));

                Path resultIdsFile = caseDir.resolve(TEST_RESULT_IDS_NAME);
                assertTrue("Test case SOLR search result IDs file must exist!", Files.exists(resultIdsFile));
                searchResultIDs = Files.readAllLines(resultIdsFile).stream().map(s -> s.trim())
                        .collect(Collectors.toList());

                SolrClient solrClient = solrTestRule.getSolrClient();
                loadSolrDocuments(solrClient, docsFile, true);
            } else {
                searchResultIDs = Collections.emptyList();
            }
        }
    }

    protected static void loadSolrDocuments(SolrClient solrClient, Path documents, boolean deleteBefore)
            throws SolrServerException, IOException {
        assertNotNull(documents);

        if (deleteBefore) {
            solrClient.deleteByQuery("*:*");
            solrClient.commit();
        }

        ContentStreamUpdateRequest req = new ContentStreamUpdateRequest("/update");
        req.addFile(documents.toFile(), "application/xml");
        req.setParam(CommonParams.HEADER_ECHO_PARAMS, CommonParams.EchoParamStyle.ALL.toString());
        req.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        NamedList<Object> result = solrClient.request(req);
        assertNotNull("Couldn't upload xml files", result);
        // log.debug("update result = {}", result);

        // QueryResponse rsp = solrClient.query(new SolrQuery("*:*"));
        // assertTrue("index should have some documents", rsp.getResults().getNumFound()
        // > 0);
    }

    // ---------------------------------------------------------------------
    // actual tests

    @Test
    public void testCQLQueryConversion() throws CQLParseException, IOException, QueryParserException, SRUException {
        assumeNotNull(cqlQuery);

        try (var mdcSnap = MDCSnapshot.create()) {
            assert null != mdcSnap; // prevent compiler warning of unused var
            MDC.put("test.case", getCaseName());

            log.info("cql query = {}", cqlQuery);

            String cql2solrQuery = convertCQLQuery(cqlQuery);
            assertEquals("CQL converted Solr query must match", solrQuery, cql2solrQuery);
        }
    }

    @Test
    public void testLexCQLQueryConversion() throws CQLParseException, IOException, QueryParserException, SRUException {
        assumeNotNull(lexCQLQuery);

        try (var mdcSnap = MDCSnapshot.create()) {
            assert null != mdcSnap; // prevent compiler warning of unused var
            MDC.put("test.case", getCaseName());

            log.info("lexcql query = {}", lexCQLQuery);

            String lexcql2solrQuery = convertLexCQLQuery(lexCQLQuery);
            assertEquals("LexCQL converted Solr query must match", solrQuery, lexcql2solrQuery);
        }
    }

    @Test
    public void testSolrSearch()
            throws SolrServerException, IOException, CQLParseException, QueryParserException, SRUException {
        org.junit.Assume.assumeTrue(usesSolrData());

        try (var mdcSnap = MDCSnapshot.create()) {
            assert null != mdcSnap; // prevent compiler warning of unused var
            MDC.put("test.case", getCaseName());

            log.info("test case = '{}' in {}", getCaseName(), caseDir);
            log.info("solr query = {}", solrQuery);

            SolrClient solrClient = solrTestRule.getSolrClient();

            SolrQuery query = new SolrQuery(solrQuery);
            query.setParam(CommonParams.FL, "*,[child]");

            QueryResponse response = solrClient.query(query);
            final List<ResultEntry> entries = response.getBeans(ResultEntry.class);
            List<String> resultIDs = entries.stream().map(e -> e.xmlId).collect(Collectors.toList());
            log.info("query result = {}", resultIDs);

            // NOTE: assertion fail due to too much output - note2: what? too long ago
            // log.info("query result = {}", response);
            assertEquals("Result count must match!", searchResultIDs.size(), response.getResults().getNumFound());

            MatcherAssert.assertThat("Search result entry xmlIds must match", resultIDs, is(searchResultIDs));
        }
    }

    // ---------------------------------------------------------------------
    // query conversion to solr query

    protected static String convertCQLQuery(String cql)
            throws CQLParseException, IOException, QueryParserException, SRUException {
        final org.z3950.zing.cql.CQLNode qn = new org.z3950.zing.cql.CQLParser().parse(cql);
        final String solrQuery = CQLToSolrConverter.convertCQLtoSolrQuery(qn);
        return solrQuery;
    }

    protected static String convertLexCQLQuery(String lexcql) throws QueryParserException, SRUException {
        final eu.clarin.sru.fcs.qlparser.lex.QueryNode qn = new eu.clarin.sru.fcs.qlparser.lex.QueryParser()
                .parse(lexcql);
        final String solrQuery = LexCQLToSolrConverter.convertLexCQLtoSolrQuery(qn);
        return solrQuery;
    }

    // ---------------------------------------------------------------------
    // helpers (for debugging)

    protected static void printDirTree(File file, int depth) {
        if (depth <= 0) {
            depth = 0;
            System.out.println(file.getAbsolutePath());
        } else {
            System.out.println(String.format("%" + (depth * 2) + "s", "") + file.getName());
        }
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                printDirTree(sub, depth + 1);
            }
        }
    }

    // ---------------------------------------------------------------------
    // https://github.com/apache/solr/blob/main/solr/test-framework/src/java/org/apache/solr/SolrTestCaseJ4.java#L2116
    // (#getFile(String))

    /**
     * Gets a resource from the context classloader as {@link Path}. This method
     * should only be used, if a real file is needed. To get a stream, code should
     * prefer {@link Class#getResourceAsStream} using {@code this.getClass()}.
     */
    protected static Path getPath(String name) {
        // see if it's a classpath resource
        final URL url = SolrTestCase.class
                .getClassLoader()
                .getResource(name.replace(FileSystems.getDefault().getSeparator(), "/"));
        if (url != null) {
            try {
                return Path.of(url.toURI());
            } catch (Exception e) {
                throw new RuntimeException(
                        "Resource was found on classpath, but cannot be resolved to a "
                                + "normal file (maybe it is part of a JAR file): "
                                + name);
            }
        }

        // see if it's a file path resource
        final Path file = Path.of(name);
        if (Files.exists(file)) {
            return file.toAbsolutePath(); // absolute to reduce ambiguity
        }

        throw new RuntimeException(
                "Cannot find resource in classpath or in file-system (relative to CWD): "
                        + file.toAbsolutePath());
    }

}

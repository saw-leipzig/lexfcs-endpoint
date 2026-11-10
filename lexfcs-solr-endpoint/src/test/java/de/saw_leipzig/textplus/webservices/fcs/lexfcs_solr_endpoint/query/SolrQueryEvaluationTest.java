// # run in project root
// mvn dependency:build-classpath -DincludeTypes=jar -Dmdep.outputFile=.cp.txt
// jshell --class-path `cat .cp.txt`:target/classes

package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.join.ToParentBlockJoinQuery;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.NodeConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SyntaxError;
import org.z3950.zing.cql.CQLParseException;

import eu.clarin.sru.fcs.qlparser.QueryParserException;
import eu.clarin.sru.server.SRUException;

public class SolrQueryEvaluationTest {

    public static void main(String[] args)
            throws IOException, SolrServerException, SyntaxError, QueryParserException, SRUException {

        final String configSetHome = "src/test/resources/configsets";
        final String configSetName = "lexfcs";
        final String solrHome = "tmp";
        final String coreName = "lexfcs-core";
        final boolean clean = true;

        try (final SolrClient solrClient = createSolrClient(configSetHome, configSetName, solrHome, coreName, clean)) {
            populateIndex(solrClient);

            disableLogging();

            // query(solrClient);
            queryarray(solrClient);

            SolrCore core = ((EmbeddedSolrServer) solrClient).getCoreContainer().getCore(coreName);
            // parseQuery(core, simpleSolrQuery("Spinne", "value"));
            // parseQueryVisiting(core);
            printParsedQueryTree(core, "*");
            printParsedQueryTree(core, simpleSolrQuery("Spinne", "value"));

            String query;
            if (true) {
                query = "dog OR (lemma=/lang=eng HOUSE AND pos == NOUN)";
                printParsedQueryTree(core, convertLexCQLQuery(query));

                query = "lemma ==/regexp ^A.*ill$";
                printParsedQueryTree(core, convertLexCQLQuery(query));

                query = "lemma == \"lead\" AND definition = \"river\"";
                printParsedQueryTree(core, convertLexCQLQuery(query));

                // NOTE: the spaces left behind due to escapiong special chars?
                query = "senseRef = \"prefix:01245-n\"";
                query = convertLexCQLQuery(query);
                // query = "({!parent which=\"_type:entry\" v=\"+_type:value_senseRef
                // +value_text:\\\"prefix\\\\:01245\\\\-n\\\"\"})";
                printParsedQueryTree(core, query);
                // query = "({!parent which=\"_type:entry\" v=\"+_type:value_senseRef
                // +value_text:\\\"prefix\\:01245\\-n\\\"\"})"; // valid?
                // printParsedQueryTree(core, query);
                // query = "({!parent which=\"_type:entry\" v=\"+_type:value_senseRef
                // +value_text:prefix\\\\:01245\\\\-n\"})";
                // printParsedQueryTree(core, query);
                // query = "({!parent which=\"_type:entry\" v=\"+_type:value_senseRef
                // +value_text:\\\"01245-n\\\"\"})";
                // printParsedQueryTree(core, query);

                query = "senseRef == \"prefix:01245-n\"";
                query = convertLexCQLQuery(query);
                printParsedQueryTree(core, query);
            }

            if (true) {
                //// NOTE: invalid escaping!
                // query = "({!parent which=\"_type:entry\" v=\"+_type:value_lemma +value_text:green\\ mouse\"})";
                // printParsedQueryTree(core, query);

                query = "({!parent which=\"_type:entry\" v=\"+_type:value_lemma +value_text:green\\\\ mouse\"})";
                printParsedQueryTree(core, query);

                query = "({!parent which=\"_type:entry\" v=\"+_type:value_lemma +value_text:green\\\\ mo*\"})";
                printParsedQueryTree(core, query);

                query = "({!parent which=\"_type:entry\" v=\"+_type:value_lemma +value_text:green +value_text:mo*\"})";
                printParsedQueryTree(core, query);

                query = "({!parent which=\"_type:entry\" v=\"+_type:value_lemma +value_text:green +value_text:*se\"})";
                printParsedQueryTree(core, query);

                query = "({!parent which=\"_type:entry\" v=\"+_type:value_lemma +value_text:green\\\\ *se\"})";
                printParsedQueryTree(core, query);

                query = "({!parent which=\"_type:entry\" v=\"+_type:value_lemma +value_text:green\\ *se\"})";
                printParsedQueryTree(core, query);
            }

            core.close(); // NOTE: close but then do not perform searches again...
            solrClient.close();
        }

    }

    public static SolrClient createSolrClient(final String configSetHome, final String configSetName,
            final String solrHome, final String coreName, final boolean clean) throws IOException, SolrServerException {

        final File configSetTemplate = new File(configSetHome);
        final File solrHomeDir = new File(solrHome).getAbsoluteFile(); // Important (absolute)
        final File configSetPath = new File(solrHomeDir, "configsets");

        if (clean && solrHomeDir.exists()) {
            FileUtils.deleteDirectory(solrHomeDir);
        }
        if (!solrHomeDir.exists()) {
            solrHomeDir.mkdirs();

            // copy config so the solr instance does not modify our template
            FileUtils.copyDirectory(configSetTemplate, configSetPath);
            // write a gitignore to ignore changes
            FileUtils.write(new File(solrHomeDir, ".gitignore"), "*", Charset.defaultCharset());
        }

        final NodeConfig config = new NodeConfig.NodeConfigBuilder("embeddedSolrServerNode", solrHomeDir.toPath())
                .setConfigSetBaseDirectory(configSetPath.toString()).build();
        final EmbeddedSolrServer embeddedSolrServer = new EmbeddedSolrServer(config, coreName);

        final CoreAdminRequest.Create createRequest = new CoreAdminRequest.Create();
        createRequest.setCoreName(coreName);
        createRequest.setConfigSet(configSetName);
        embeddedSolrServer.request(createRequest);

        final SolrClient solrClient = embeddedSolrServer;
        return solrClient;
    }

    public static void populateIndex(final SolrClient solrClient) throws SolrServerException, IOException {
        final SolrInputDocument entry1 = new SolrInputDocument();
        entry1.setField("_type", "entry");
        entry1.setField("xmlId", "die Spinne");
        {
            final SolrInputDocument field1 = new SolrInputDocument();
            field1.setField("_type", "value_lemma");
            field1.setField("value", "die Spinne");
            entry1.setField("values", Arrays.asList(field1));
        }

        final SolrInputDocument entry2 = new SolrInputDocument();
        entry2.setField("_type", "entry");
        entry2.setField("xmlId", "Spinne");
        {
            final SolrInputDocument field1 = new SolrInputDocument();
            field1.setField("_type", "value_lemma");
            field1.setField("value", "Spinne");
            entry2.setField("values", Arrays.asList(field1));
        }

        final SolrInputDocument entry3 = new SolrInputDocument();
        entry3.setField("_type", "entry");
        entry3.setField("xmlId", "Spinnen");
        {
            final SolrInputDocument field1 = new SolrInputDocument();
            field1.setField("_type", "value_lemma");
            field1.setField("value", "Spinnen");
            entry3.setField("values", Arrays.asList(field1));
        }

        final SolrInputDocument entry4 = new SolrInputDocument();
        entry4.setField("_type", "entry");
        entry4.setField("xmlId", "die Spinne spinnt");
        {
            final SolrInputDocument field1 = new SolrInputDocument();
            field1.setField("_type", "value_lemma");
            field1.setField("value", "die Spinne spinnt");
            entry4.setField("values", Arrays.asList(field1));
        }

        final SolrInputDocument entry5 = new SolrInputDocument();
        entry5.setField("_type", "entry");
        entry5.setField("xmlId", "die Spinnen spinnen");
        {
            final SolrInputDocument field1 = new SolrInputDocument();
            field1.setField("_type", "value_lemma");
            field1.setField("value", "die Spinnen spinnen");
            entry5.setField("values", Arrays.asList(field1));
        }

        final SolrInputDocument entry6 = new SolrInputDocument();
        entry6.setField("_type", "entry");
        entry6.setField("xmlId", "der Spinne");
        {
            final SolrInputDocument field1 = new SolrInputDocument();
            field1.setField("_type", "value_lemma");
            field1.setField("value", "der Spinne");
            entry6.setField("values", Arrays.asList(field1));
        }

        final SolrInputDocument entry7 = new SolrInputDocument();
        entry7.setField("_type", "entry");
        entry7.setField("xmlId", "die Kuh");
        {
            final SolrInputDocument field1 = new SolrInputDocument();
            field1.setField("_type", "value_lemma");
            field1.setField("value", "die Kuh");
            entry7.setField("values", Arrays.asList(field1));
        }

        final SolrInputDocument entry8 = new SolrInputDocument();
        entry8.setField("_type", "entry");
        entry8.setField("xmlId", "spinne");
        {
            final SolrInputDocument field1 = new SolrInputDocument();
            field1.setField("_type", "value_lemma");
            field1.setField("value", "spinne");
            entry8.setField("values", Arrays.asList(field1));
        }

        solrClient.add(Arrays.asList(entry1, entry2, entry3, entry4, entry5, entry6, entry7, entry8));
        solrClient.commit();
    }

    public static List<String> queryIds(final SolrClient solrClient, final String query)
            throws SolrServerException, IOException {
        SolrQuery solrQuery = new SolrQuery(query);
        solrQuery.setFields("*", "[child]");
        System.err.println("solr query: " + solrQuery);

        QueryResponse response;
        try {
            response = solrClient.query(solrQuery);
        } catch (SolrException e) {
            System.err.println(e.getLocalizedMessage());
            return Collections.emptyList();
        }

        System.err.println("found results: " + response.getResults().getNumFound());
        SolrDocumentList solrDocuments = response.getResults();
        if (solrDocuments.size() != response.getResults().getNumFound()) {
            System.err.println("got documents: " + solrDocuments.size());
        }
        List<String> ids = solrDocuments.stream().map(d -> (String) d.get("xmlId")).filter(v -> v != null)
                .collect(Collectors.toList());
        System.err.println("result ids: " + ids);

        return ids;
    }

    // ---------------------------------------------------------------------

    public static void query(final SolrClient solrClient) throws SolrServerException, IOException {
        String query = "*:*";
        List<String> ids = queryIds(solrClient, query);
        // die-Spinne, Spinne, Spinnen

        query = "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:\\\"Spinne\\\"\"}";
        ids = queryIds(solrClient, query);
        // --> die-Spinne, Spinne

        query = "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:Spinne\"}";
        ids = queryIds(solrClient, query);
        // --> die-Spinne, Spinne

        query = "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/Spinne/\"}";
        ids = queryIds(solrClient, query);
        // --> die-Spinne, Spinne
    }

    public static void queryarray(final SolrClient solrClient, String[] queries)
            throws SolrServerException, IOException {
        for (final String query : queries) {
            System.err.println("-".repeat(40));
            List<String> ids = queryIds(solrClient, query);
        }
    }

    public static void queryarray(final SolrClient solrClient) throws SolrServerException, IOException {
        // @formatter:off

        String[] queriesBasic = new String[] {
                "*",
                // --> alles
                "*:*",
                // --> alles

                // "*:Spinne",
                // --> error
        };

        String[] queries = new String[] {
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:\\\"Spinne\\\"\"}",
                // --> die-Spinne, Spinne, die Spinne spinnt, der Spinne

                // "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:\"Spinne\"\"},
                // --> error

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:Spinne\"}",
                // --> die-Spinne, Spinne, die Spinne spinnt, der Spinne

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:\\\"die Spinne\\\"\"}",
                // --> die-Spinne, die Spinne spinnt

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:die Spinne\"}",
                // --> die-Spinne, die Spinne spinnt, die Spinnen spinnen, die Kuh
                // --> invalid
        };

        // Regex
        // --> https://solr.apache.org/guide/solr/9_8/query-guide/searching-nested-documents.html
        // --> https://lucene.apache.org/core/9_11_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#Regexp_Searches
        // --> https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/util/automaton/RegExp.html
        // ^$ is not supported!

        String[] queriesRegexOnText = new String[] {
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/Spinne/\"}",
                // --> die-Spinne, Spinne, die Spinne spinnt, der Spinne, spinne

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/^Spinne/\"}",
                // --> []
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/Spinne$/\"}",
                // --> []
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/^Spinne$/\"}",
                // --> []
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/^Spinne.*/\"}",
                // --> []

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/.*Spinne.*/\"}",
                // --> die-Spinne, Spinne, Spinnen, die Spinne spinnt, die Spinnen spinnen, der Spinne, spinne

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/Spinne.*/\"}",
                // --> die-Spinne, Spinne, Spinnen, die Spinne spinnt, die Spinnen spinnen, der Spinne, spinne

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/die Spinne/\"}",
                // --> []

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value_text:/[^.]Spinne.*/\"}",
                // --> []
        };
        String[] queriesRegexOnString = new String[] {
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/Spinne/\"}",
                // --> Spinne

                // not supported
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/^Spinne/\"}",
                // --> []
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/Spinne$/\"}",
                // --> []
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/^Spinne$/\"}",
                // --> []
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/^Spinne.*/\"}",
                // --> []

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/.*Spinne.*/\"}",
                // --> die-Spinne, Spinne, Spinnen, die Spinne spinnt, die Spinnen spinnen, der Spinne

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/Spinne.*/\"}",
                // --> Spinne, Spinnen
                // ^Spinne ?
                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/.*Spinne/\"}",
                // --> die-Spinne, Spinne, der Spinne
                // Spinne$ ?

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/die Spinne/\"}",
                // --> die-Spinne

                "{!parent which=\"_type:entry\" v=\"+_type:value_* +value:/[\\w\\W]+Spinne.*/\"}",
                // --> die-Spinne, die Spinne spinnt, die Spinnen spinnen, der Spinne
        };

        /* // --> managed-schema.xml

        <field name="value" type="string" multiValued="false" indexed="true" stored="true"/>
        <field name="value_text" type="text_general" multiValued="false" indexed="true" stored="false"/>

        <fieldType name="string" class="solr.StrField" sortMissingLast="true" docValues="true"/>
        <fieldType name="text_general" class="solr.TextField" positionIncrementGap="100" multiValued="true">
          <analyzer type="index">
            <tokenizer name="standard"/>
            <filter words="stopwords.txt" name="stop" ignoreCase="true"/>
            <filter name="lowercase"/>
          </analyzer>
          <analyzer type="query">
            <tokenizer name="standard"/>
            <filter words="stopwords.txt" name="stop" ignoreCase="true"/>
            <filter ignoreCase="true" synonyms="synonyms.txt" name="synonymGraph" expand="true"/>
            <filter name="lowercase"/>
          </analyzer>
        </fieldType>
        */

        // @formatter:on

        queryarray(solrClient, queriesBasic);
        queryarray(solrClient, queries);
        queryarray(solrClient, queriesRegexOnText);
        queryarray(solrClient, queriesRegexOnString);
    }

    // ---------------------------------------------------------------------

    // NOTE: `field` means the solr field where the search happens, not the LexCQL
    // field

    public static String simpleSolrQuery(final String value, String field) {
        return "{!parent which=\"_type:entry\" v=\"+_type:value_* +" + field + ":" + value + "\"}";
    }

    public static String simpleSolrQueryNeg(final String value, String field) {
        return "{!parent which=\"_type:entry\" v=\"+_type:value_* -" + field + ":" + value + "\"}";
    }

    public static void queryarraysingles(final SolrClient solrClient) throws SolrServerException, IOException {

        queryarray(solrClient, new String[] { simpleSolrQuery("Spinne", "value") });
        // --> Spinne
        queryarray(solrClient, new String[] { simpleSolrQuery("Spinne", "value_text") });
        // --> die-Spinne, Spinne, die Spinne spinnt, der Spinne, spinne

        queryarray(solrClient, new String[] { simpleSolrQuery("/die Spinne/", "value") });
        // --> die-Spinne
        queryarray(solrClient, new String[] { simpleSolrQuery("/die Spinne/", "value_text") });
        // --> []

        // NOTE: caution with correct escaping! (double escape)
        queryarray(solrClient, new String[] { simpleSolrQuery("\\\"d* Spinne\\\"", "value") });
        // --> [] --> falsch
        queryarray(solrClient, new String[] { simpleSolrQuery("d*\\ Spinne", "value") });
        // --> die-Spinne, der Spinne

        queryarray(solrClient, new String[] { simpleSolrQuery("Spinne*", "value") });
        // --> Spinne, Spinnen
        queryarray(solrClient, new String[] { simpleSolrQuery("*Spinne", "value") });
        // --> die-Spinne, Spinne, der Spinne
        queryarray(solrClient, new String[] { simpleSolrQuery("*Spinne*", "value") });
        // --> die-Spinne, Spinne, Spinnen, die Spinne spinnt, die Spinnen spinnen, der
        // Spinne
    }

    // ---------------------------------------------------------------------

    public static void disableLogging() {
        // org.apache.solr
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.setLevel(Level.OFF);
        ctx.updateLoggers();
    }

    // ---------------------------------------------------------------------

    public static Query parseQuery(final SolrCore core, final String qstr)
            throws SyntaxError {
        SolrParams params = new MultiMapSolrParams(new HashMap<>() {
            {
                // NOTE: put an invalid field here to catch errors
                // Should normally be "value" or "value_text" !
                put(CommonParams.DF, new String[] { "<invalid>" });
            }
        });
        SolrQueryRequest request = new SolrQueryRequestBase(core, params) {
        };

        // SolrRequestParsers

        QParser parser = QParser.getParser(qstr, null, true, request);
        Query query = parser.parse();
        System.err.println("raw query: " + qstr);
        System.err.println("parsed query: " + query);

        return query;
    }

    public static void parseQueryVisiting(final SolrCore core) throws SyntaxError {
        final String qstr = simpleSolrQuery("Spinne", "value");
        Query query = parseQuery(core, qstr);

        // ToParentBlockJoinQuery (+(+_type:value_* +value:Spinne))
        ToParentBlockJoinQuery pbjq = (ToParentBlockJoinQuery) query;
        BooleanQuery child = (BooleanQuery) pbjq.getChildQuery(); // +(+_type:value_* +value:Spinne)
        BooleanClause clause = child.clauses().get(0); // ++_type:value_* +value:Spinne

        BooleanQuery bq = (BooleanQuery) clause.getQuery(); // +_type:value_* +value:Spinne
        BooleanClause clause_type = bq.clauses().get(0); // +_type:value_*
        BooleanClause clause_value = bq.clauses().get(1); // +value:Spinne

        PrefixQuery pq_type = (PrefixQuery) clause_type.getQuery(); // _type:value_*
        String field = pq_type.getPrefix().field(); // "_type"
        String text = pq_type.getPrefix().text(); // "value_"

        QueryVisitor qv = new QueryVisitor() {
            @Override
            public void consumeTerms(Query query, Term... terms) {
                System.err.println(
                        "[consumeTerms] query=" + query + " (class=" + query.getClass() + ")" + " terms=" + terms);
            }

            @Override
            public void consumeTermsMatching(Query query, String field, Supplier<ByteRunAutomaton> automaton) {
                System.err.println("[consumeTermsMatching] query=" + query + " (class=" + query.getClass() + ")"
                        + " field=" + field);
            }

            @Override
            public void visitLeaf(Query query) {
                System.err.println("[visitLeaf] query=" + query + " (class=" + query.getClass() + ")");
                if (query instanceof ToParentBlockJoinQuery) {
                    ((ToParentBlockJoinQuery) query).getChildQuery().visit(this);
                }
            }

            @Override
            public boolean acceptField(String field) {
                System.err.println("[acceptField] field=" + field);
                return true;
            }

            @Override
            public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
                System.err.println(
                        "[getSubVisitor] occur=" + occur + " parent=" + parent + " (class=" + parent.getClass() + ")");
                QueryVisitor sub = super.getSubVisitor(occur, parent);
                // System.err.println("[getSubVisitor] sub=" + sub);
                return sub;
            }

            // @Override
            // public String toString() {
            // return "QueryVisitor@me";
            // }
        };
        query.visit(qv);

        // Set<Term> terms = new HashSet<>();
        // child.visit(QueryVisitor.termCollector(terms));
        // System.err.println("terms=" + terms);
    }

    public static void printParsedQueryTree(final SolrCore core, String qstr) throws SyntaxError {
        System.err.println("-".repeat(40));
        Query query = parseQuery(core, qstr);

        class MyQueryVisitor extends QueryVisitor {
            protected int level = 0;

            public MyQueryVisitor(int level) {
                this.level = level;
            }

            public MyQueryVisitor() {
                this(0);
            }

            @Override
            public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
                printLine("SV", parent);
                return new MyQueryVisitor(level + 1);
            }

            @Override
            public void visitLeaf(Query query) {
                printLine("L", query);
                if (query instanceof ToParentBlockJoinQuery) {
                    Query child = ((ToParentBlockJoinQuery) query).getChildQuery();
                    child.visit(new MyQueryVisitor(level + 1));
                }
            }

            @Override
            public void consumeTerms(Query query, Term... terms) {
                printLine("T", query);
            }

            @Override
            public void consumeTermsMatching(Query query, String field, Supplier<ByteRunAutomaton> automaton) {
                printLine("TN", query);
            }

            public void printLine(String type, Query query) {
                System.err.println(String.format("[%-2s] ", type) + " ".repeat(level * 2)
                        + String.format("%s:  %s", getClassName(query), query));
            }

            public String getClassName(Object obj) {
                // obj.getClass().getSimpleName()
                // obj.getClass().getName();
                String[] parts = obj.getClass().getName().split("\\.");
                return parts[parts.length - 1];
            }
        }

        query.visit(new MyQueryVisitor());
    }

    // ---------------------------------------------------------------------

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

}

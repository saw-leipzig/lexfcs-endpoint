package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint;

import static de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.SAWSRUConstants.RESOURCE_PREFIX;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query.CQLToSolrConverter;
import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query.LexCQLToSolrConverter;
import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr.SearchResultSet;
import de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr.Searcher;
import eu.clarin.sru.server.CQLQueryParser;
import eu.clarin.sru.server.SRUConfigException;
import eu.clarin.sru.server.SRUConstants;
import eu.clarin.sru.server.SRUDiagnosticList;
import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.server.SRUQueryParserRegistry.Builder;
import eu.clarin.sru.server.SRURequest;
import eu.clarin.sru.server.SRUSearchResultSet;
import eu.clarin.sru.server.SRUServerConfig;
import eu.clarin.sru.server.fcs.Constants;
import eu.clarin.sru.server.fcs.DataView;
import eu.clarin.sru.server.fcs.EndpointDescription;
import eu.clarin.sru.server.fcs.LexCQLQueryParser;
import eu.clarin.sru.server.fcs.ResourceInfo;
import eu.clarin.sru.server.fcs.SimpleEndpointSearchEngineBase;
import eu.clarin.sru.server.fcs.parser.QueryParserException;
import eu.clarin.sru.server.fcs.utils.SimpleEndpointDescriptionParser;

public class SAWSRUEndpoint extends SimpleEndpointSearchEngineBase {
    private static final Logger LOGGER = LogManager.getLogger(SAWSRUEndpoint.class);

    private static final String RESOURCE_INVENTORY_URL = SAWSRUEndpoint.class.getPackageName()
            + ".resourceInventoryURL";

    protected Searcher searcher;

    /**
     * List of our endpoint's resources (identified by PID Strings)
     */
    private List<String> pids;
    /**
     * Mapping of resource PID to corpus name (for Solr).
     */
    protected Map<String, String> pid2name;
    /**
     * Our default corpus if SRU requests do no explicitely request a resource
     * by PID with the <code>x-fcs-context</code> parameter.
     * Must not be <code>null</code>!
     */
    private String defaultCorpusId = null;

    // ---------------------------------------------------------------------
    // params

    /**
     * Read an environment variable from <code>java:comp/env/paramName</code>
     * and return the value as Object.
     *
     * @param paramName
     *                  the environment variables name to extract the value from
     * @return the environment variable value as Object
     */
    protected Object readJndi(String paramName) {
        // https://stackoverflow.com/a/4099163/9360161
        // https://stackoverflow.com/a/26593802/9360161
        Object jndiValue = null;
        try {
            final InitialContext ic = new InitialContext();
            jndiValue = ic.lookup("java:comp/env/" + paramName);
        } catch (NamingException e) {
            // handle exception
        }
        return jndiValue;
    }

    /**
     * Read an environment variable and return the value as String.
     * 
     * @param paramName
     *                  the environment variables name to extract the value from
     * @return the environment variable value as String
     */
    protected String getEnvParam(String paramName) {
        return (String) readJndi("param/" + paramName);
    }

    protected Boolean getEnvParamBoolean(String paramName) {
        return (Boolean) readJndi("param/" + paramName);
    }

    // ---------------------------------------------------------------------
    // EndpointDescription stuff

    /**
     * Load the {@link EndpointDescription} from the JAR resources or from the
     * <code>RESOURCE_INVENTORY_URL</code>.
     * 
     * @param context
     *                the {@link ServletContext} for the Servlet
     * @param params
     *                additional parameters gathered from the Servlet configuration
     *                and Servlet context.
     * @return the {@link EndpointDescription} object
     * @throws SRUConfigException
     *                            an error occurred during loading/reading the
     *                            <code>endpoint-description.xml</code> file
     */
    protected EndpointDescription loadEndpointDescriptionFromURI(ServletContext context, Map<String, String> params)
            throws SRUConfigException {
        try {
            URL url = null;
            String riu = params.get(RESOURCE_INVENTORY_URL);
            if ((riu == null) || riu.isEmpty()) {
                url = context.getResource("/WEB-INF/endpoint-description.xml");
                LOGGER.debug("using bundled 'endpoint-description.xml' file");
            } else {
                url = new File(riu).toURI().toURL();
                LOGGER.debug("using external file '{}'", riu);
            }

            return SimpleEndpointDescriptionParser.parse(url);
        } catch (MalformedURLException mue) {
            throw new SRUConfigException("Malformed URL for initializing resource info inventory", mue);
        }
    }

    @Override
    protected EndpointDescription createEndpointDescription(ServletContext context, SRUServerConfig config,
            Map<String, String> params) throws SRUConfigException {
        LOGGER.info("SRUServlet::createEndpointDescription");
        if (endpointDescription == null) {
            endpointDescription = loadEndpointDescriptionFromURI(context, params);
        }
        return endpointDescription;

    }

    /**
     * Get the {@link ResourceInfo} identified by <code>pid</code> from the
     * {@link EndpointDescription} object.
     * 
     * <p>
     * NOTE that we only allow root level resources and currently can not search for
     * sub-resources.
     * </p>
     * <p>
     * NOTE that the PID should exist, so validate the PID using
     * {@link #getResourcesFromEndpointDescription}.
     * </p>
     * 
     * @param ed  Endpoint Description
     * @param pid Resource PID String
     * @return {@link ResourceInfo}
     * @throws SRUException
     */
    protected ResourceInfo getResourceFromEndpointDescriptionByPID(EndpointDescription ed, String pid)
            throws SRUException {
        // NOTE: for now only support on root level
        return ed.getResourceList(EndpointDescription.PID_ROOT).stream().filter(res -> res.getPid().equals((pid)))
                .findAny().get();
    }

    /**
     * Parses the list of root resource PIDs from the {@link EndpointDescription}.
     * 
     * Note: This only considers root resources and not subresources!
     * 
     * @param ed
     *           the {@link EndpointDescription} for the Servlet
     * @return a list of String with root resource PIDs
     */
    protected List<String> getResourcesFromEndpointDescription(EndpointDescription ed) throws SRUException {
        // NOTE: only root resources!
        return ed.getResourceList(EndpointDescription.PID_ROOT).stream().map(ResourceInfo::getPid)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------
    // init

    @Override
    protected void doInit(ServletContext context, SRUServerConfig config, Builder queryParsersBuilder,
            Map<String, String> params)
            throws SRUConfigException {
        LOGGER.info("SRUServlet::doInit {}", config.getPort());

        /* setup resource PID prefix, e.g. "my:" */
        LOGGER.debug("RESOURCE_PREFIX = {}", RESOURCE_PREFIX);

        /* initialize Solr API Client */
        searcher = new Searcher(getEnvParam("SOLR_URL"), getEnvParam("SOLR_USER"), getEnvParam("SOLR_PASSWORD"));

        endpointDescription = createEndpointDescription(context, config, params);

        try {
            pids = getResourcesFromEndpointDescription(endpointDescription);
        } catch (SRUException e) {
            throw new SRUConfigException("Error extracting resource pids", e);
        }
        LOGGER.info("Got root resource PIDs: {}", pids);

        defaultCorpusId = getEnvParam("DEFAULT_RESOURCE_PID");
        LOGGER.info("Got defaultCorpusId resource PID: {}", defaultCorpusId);
        if (defaultCorpusId == null || !pids.contains(defaultCorpusId)) {
            throw new SRUConfigException("Parameter 'DEFAULT_RESOURCE_PID' contains unknown resource pid!");
        }
    }

    // ---------------------------------------------------------------------
    // search

    @Override
    public SRUSearchResultSet search(SRUServerConfig config, SRURequest request, SRUDiagnosticList diagnostics)
            throws SRUException {
        final String solrQuery = parseQuery(request);

        List<String> pids = parsePids(request);
        pids = checkPids(pids, diagnostics);
        LOGGER.debug("Search restricted to PIDs: {}", pids);
        final String pid = checkPid(pids);
        LOGGER.debug("Search restricted to first PID: {}", pid);

        /* get corpus/resource info from pid */
        final String resourceName;
        if (pid2name != null && pid2name.containsKey(pid)) {
            resourceName = pid2name.get(pid);
        } else {
            // LOGGER.error("This branch should not be executed! PID={}", pid);
            // throw new RuntimeException("Unexpected branching!");
            // resourceName = pid.substring(RESOURCE_PREFIX.length());
            resourceName = pid;
        }

        List<String> dataviews = parseDataViews(request, diagnostics, pid);
        LOGGER.debug("Search requested dataviews: {}", dataviews);

        int startRecord = ((request.getStartRecord() < 1) ? 1 : request.getStartRecord()) - 1;
        int maximumRecords = request.getMaximumRecords();

        SearchResultSet results = searcher.search(solrQuery, resourceName, startRecord, maximumRecords);
        if (results == null) {
            throw new SRUException(SRUConstants.SRU_GENERAL_SYSTEM_ERROR, "Error in Searcher");
        }
        // update back to real pid
        results.setPid(pid);

        return new SAWSRUSearchResultSet(config, request, diagnostics, dataviews, results);
    }

    // ---------------------------------------------------------------------
    // search param utils (parsing/validation)

    protected String parseQuery(SRURequest request) throws SRUException {
        final String solrQuery;
        if (request.isQueryType(Constants.FCS_QUERY_TYPE_CQL)) {
            /*
             * Got a CQL query (either SRU 1.1 or higher).
             * Translate to a proper FCS-QL query ...
             */
            final CQLQueryParser.CQLQuery q = request.getQuery(CQLQueryParser.CQLQuery.class);
            LOGGER.info("FCS-CQL query: {}", q.getRawQuery());

            try {
                solrQuery = CQLToSolrConverter.convertCQLtoSolrQuery(q.getParsedQuery());
                LOGGER.debug("Converted solrQuery: {}", solrQuery);
            } catch (QueryParserException e) {
                throw new SRUException(
                        SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Converting query with queryType 'cql' to Solr failed.",
                        e);
            }
        } else if (request.isQueryType(Constants.FCS_QUERY_TYPE_LEX)) {
            /*
             * Got a LexFCS (LexCQL) query (SRU 2.0).
             */
            final LexCQLQueryParser.LexCQLQuery q = request.getQuery(LexCQLQueryParser.LexCQLQuery.class);
            LOGGER.info("FCS-LexCQL query: {}", q.getRawQuery());

            // convert from LexCQL to Solr QL
            try {
                solrQuery = LexCQLToSolrConverter.convertLexCQLtoSolrQuery(q.getParsedQuery());
                LOGGER.debug("Converted solrQuery: {}", solrQuery);
            } catch (QueryParserException e) {
                throw new SRUException(
                        SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Converting query with queryType 'lex' to Solr failed.",
                        e);
            }
        } else {
            /*
             * Got something else we don't support. Send error ...
             */
            throw new SRUException(
                    SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                    "Queries with queryType '" +
                            request.getQueryType() +
                            "' are not supported by this FCS Endpoint.");
        }
        return solrQuery;
    }

    /**
     * Extract and parse the requested resource PIDs from the {@link SRURequest}.
     * 
     * Returns the list of resource PIDs if <code>x-fcs-context</code> parameter
     * was used and it was non-empty. If no FCS context was set, then return with
     * the <code>defaultCorpusId</code>.
     *
     * @param request
     *                the {@link SRURequest} with request parameters
     * @return a list of String resource PIDs
     *
     * @see #search(SRUServerConfig, SRURequest, SRUDiagnosticList)
     */
    protected List<String> parsePids(SRURequest request) throws SRUException {
        boolean hasFcsContextCorpus = false;
        String fcsContextCorpus = "";

        for (String erd : request.getExtraRequestDataNames()) {
            if (Constants.X_FCS_CONTEXT_KEY.equals(erd)) {
                hasFcsContextCorpus = true;
                fcsContextCorpus = request.getExtraRequestData(Constants.X_FCS_CONTEXT_KEY);
                break;
            }
        }
        if (!hasFcsContextCorpus || "".equals(fcsContextCorpus)) {
            LOGGER.debug("Received 'searchRetrieve' request without x-fcs-context - Using default '{}'",
                    defaultCorpusId);
            fcsContextCorpus = defaultCorpusId;
        }
        if (fcsContextCorpus == null) {
            return new ArrayList<>();
        }

        List<String> selectedPids = new ArrayList<>(
                Arrays.asList(fcsContextCorpus.split(Constants.X_FCS_CONTEXT_SEPARATOR)));

        return selectedPids;
    }

    /**
     * Validate the requested resource PIDs from the {@link SRURequest} against
     * the list of resource PIDs declared in the servlet's
     * {@link EndpointDescription}.
     * 
     * Returns the list of valid resource PIDs. Generates SRU diagnostics for
     * each invalid/unknown resource PID. If the list of valid PIDs is empty
     * then raise an {@link SRUException}.
     *
     * @param pids
     *                    the list of resource PIDs
     * @param diagnostics
     *                    the {@link SRUDiagnosticList} object for storing
     *                    non-fatal diagnostics
     * @return a list of String resource PIDs
     * @throws SRUException
     *                      if no valid resource PIDs left
     *
     * @see #search(SRUServerConfig, SRURequest, SRUDiagnosticList)
     * @see #getResourcesFromEndpointDescription(EndpointDescription)
     * @see #parsePids(SRURequest)
     */
    protected List<String> checkPids(List<String> pids, SRUDiagnosticList diagnostics) throws SRUException {
        // set valid and existing resource PIDs
        List<String> knownPids = new ArrayList<>();
        for (String pid : pids) {
            if (!this.pids.contains(pid)) {
                // allow only valid resources that can be queried by CQL
                diagnostics.addDiagnostic(
                        Constants.FCS_DIAGNOSTIC_PERSISTENT_IDENTIFIER_INVALID,
                        pid,
                        "Resource PID for search is not valid or can not be queried by FCS/CQL!");
            } else {
                knownPids.add(pid);
            }
        }
        if (knownPids.isEmpty()) {
            // if search was restricted to resources but all were invalid, then do we fail?
            // or do we adjust to our default corpus?
            throw new SRUException(
                    SRUConstants.SRU_UNSUPPORTED_PARAMETER_VALUE,
                    "All values passed to '" + Constants.X_FCS_CONTEXT_KEY
                            + "' were not valid PIDs or can not be queried by FCS/CQL.");
        }

        return knownPids;
    }

    /**
     * Validate the requested resource PIDs from the {@link SRURequest} to be
     * only a single PID as this endpoint can only handle searching through one
     * resource at a time.
     * 
     * NOTE: The CLARIN SRU/FCS Aggregator also only seems to request results
     * for each resource separately, we only allow requests with one resource!
     * 
     * Returns the resource PID. Raises an {@link SRUException} if more than
     * one resource PID in <code>pids</code>.
     *
     * @param pids
     *             the list of resource PIDs
     * @return the resource PID as String
     * @throws SRUException
     *                      if no valid resource PIDs left
     *
     * @see #search(SRUServerConfig, SRURequest, SRUDiagnosticList)
     * @see #checkPids(List, SRUDiagnosticList)
     */
    protected String checkPid(List<String> pids) throws SRUException {
        // NOTE: we only search for first PID
        // (FCS Aggregator only provides one resource PID per search request, so
        // multiple PIDs should usually not happen)
        final String pid;
        if (pids.size() > 1) {
            throw new SRUException(
                    SRUConstants.SRU_UNSUPPORTED_PARAMETER_VALUE,
                    "Parameter '" + Constants.X_FCS_CONTEXT_KEY
                            + "' received multiple PIDs. Endpoint only supports a single PIDs for querying by CQL/FCS-QL/LexCQL.");
        } else if (pids.size() == 0) {
            pid = defaultCorpusId;
            LOGGER.debug("Falling back to default resource: {}", pid);
            pids.add(pid);
        } else {
            pid = pids.get(0);
        }
        return pid;
    }

    /**
     * Extract and parse the requested result Data Views from the
     * {@link SRURequest}.
     * 
     * Returns the list of Data View identifiers if <code>x-fcs-dataviews</code>
     * parameter was used and is non-empty.
     * 
     * Validates the requested Data Views against the ones declared in the servlet's
     * {@link EndpointDescription} for the resource identified by the value in
     * <code>pid</code>. For each non-valid Data View generate a SRU diagnostic.
     *
     * @param request
     *                    the {@link SRURequest} with request parameters
     * @param diagnostics
     *                    the {@link SRUDiagnosticList} object for storing
     *                    non-fatal diagnostics
     * @param pid
     *                    resource PID String, to validate requested Data Views
     * @return a list of String Data View identifiers, may be empty
     *
     * @see #search(SRUServerConfig, SRURequest, SRUDiagnosticList)
     */
    protected List<String> parseDataViews(SRURequest request, SRUDiagnosticList diagnostics, String pid)
            throws SRUException {
        List<String> extraDataviews = new ArrayList<>();
        if (request != null) {
            for (String erd : request.getExtraRequestDataNames()) {
                if (Constants.X_FCS_DATAVIEWS_KEY.equals(erd)) {
                    String dvs = request.getExtraRequestData(Constants.X_FCS_DATAVIEWS_KEY);
                    extraDataviews = new ArrayList<>(
                            Arrays.asList(dvs.split(Constants.X_FCS_DATAVIEWS_SEPARATOR)));
                    break;
                }
            }
        }
        if (extraDataviews.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> resourceDataViews = getResourceFromEndpointDescriptionByPID(endpointDescription, pid)
                .getAvailableDataViews().stream()
                .map(DataView::getIdentifier).collect(Collectors.toSet());

        List<String> allowedDataViews = new ArrayList<>();
        for (String dv : extraDataviews) {
            if (!resourceDataViews.contains(dv)) {
                // allow only valid dataviews for this resource that can be requested
                diagnostics.addDiagnostic(
                        Constants.FCS_DIAGNOSTIC_PERSISTENT_IDENTIFIER_INVALID,
                        pid,
                        "DataViews with identifier '" + dv + "' for resource PID='" + pid + "' is not valid!");
            } else {
                allowedDataViews.add(dv);
            }
        }
        return allowedDataViews;
    }

}
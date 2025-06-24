package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.util.NamedList;

public class Searcher {
    private static final Logger LOGGER = LogManager.getLogger(Searcher.class);

    private SolrClient solrClient;

    public Searcher(String url, String user, String password) {
        solrClient = new Http2SolrClient.Builder(url)
                .withConnectionTimeout(10, TimeUnit.SECONDS)
                .withBasicAuthCredentials(user, password)
                .build();
    }

    public boolean hasCore(String core) {
        CoreAdminRequest request = new CoreAdminRequest();
        request.setAction(CoreAdminAction.STATUS);
        try {
            CoreAdminResponse response = request.process(solrClient);
            NamedList<Object> coreInfo = response.getCoreStatus(core);
            // TODO: getBeans() ?
            if (coreInfo == null) {
                return false;
            }

            LOGGER.debug("Index <{}> uptime: {}", core, coreInfo.get("uptime"));
            LOGGER.debug("Index <{}> startTime: {}", core, coreInfo.get("startTime"));

            @SuppressWarnings("unchecked")
            NamedList<Object> indexInfo = (NamedList<Object>) coreInfo.get("index");
            if (indexInfo != null) {
                LOGGER.debug("Index <{}> numDocs: {}", core, indexInfo.get("numDocs"));
                LOGGER.debug("Index <{}> version: {}", core, indexInfo.get("version"));
                LOGGER.debug("Index <{}> lastModified: {}", core, indexInfo.get("lastModified"));
                LOGGER.debug("Index <{}> sizeInBytes: {}", core, indexInfo.get("sizeInBytes"));
                LOGGER.debug("Index <{}> size: {}", core, indexInfo.get("size"));
            }
        } catch (SolrServerException | IOException e) {
            LOGGER.debug("Error requesting Core Status", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected Solr request error", e);
        }
        return true;
    }

    public SearchResultSet search(String query, String collection, long offset, int limit) {
        @SuppressWarnings("serial")
        final MapSolrParams queryParams = new MapSolrParams(new HashMap<String, String>() {
            {
                // https://solr.apache.org/guide/solr/latest/query-guide/standard-query-parser.html
                put("q", query);
                // put("q.op", "OR");
                // put("df", "dataview_hits");

                // return child documents!
                put("fl", "*,[child]");

                // pagination
                // https://solr.apache.org/guide/solr/latest/query-guide/pagination-of-results.html
                put("start", String.valueOf(offset)); // starts with 0
                put("rows", String.valueOf(limit));

                // highlighting
                // https://solr.apache.org/guide/solr/latest/query-guide/highlighting.html
                put("hl", "true");
                put("hl.fl", "dataview_hits"); // only highlight on "dataview_hits"
                put("hl.fragsize", "0"); // whole field
                put("hl.simple.pre", "<Hit>"); // no prefix so that we can parse more easily
                put("hl.simple.post", "</Hit>");
            }
        });

        try {
            final QueryResponse response = solrClient.query(collection, queryParams);
            final long total = response.getResults().getNumFound(); // .getNumFoundExact()
            final List<ResultEntry> entries = response.getBeans(ResultEntry.class);

            // fixing nested children beaning
            // NOTE: this is because there is no nested bean-binding support for a named
            // child documents field
            // https://solr.apache.org/guide/solr/latest/indexing-guide/indexing-nested-documents.html
            for (ResultEntry entry : entries) {
                if (entry.rawValues != null && !entry.rawValues.isEmpty()) {
                    entry.values = new ArrayList<FieldValue>(entry.rawValues.size());
                    for (SolrDocument childDoc : entry.rawValues) {
                        FieldValue value = solrClient.getBinder().getBean(FieldValue.class, childDoc);
                        entry.values.add(value);
                    }
                    entry.rawValues = null;
                }
            }

            // process highlighting (update dataview_hits)
            for (ResultEntry entry : entries) {
                if (!response.getHighlighting().containsKey(entry.id)) {
                    continue;
                }
                final List<String> highlights = response.getHighlighting().get(entry.id).get("dataview_hits");
                if (highlights == null || highlights.size() == 0) {
                    continue;
                }
                final String highlight = highlights.get(0);
                entry.dataview_hits = highlight;
            }

            LOGGER.debug("results: {}", entries);
            return new SearchResultSet(collection, query, entries, total, offset);
        } catch (SolrServerException | IOException e) {
            LOGGER.error("Solr request error", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected Solr request error", e);
        }

        return null;
    }

}

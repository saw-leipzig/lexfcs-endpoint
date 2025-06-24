package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.solr;

import java.util.List;

public class SearchResultSet {
    private String pid;
    private String query;
    private List<ResultEntry> results;
    private long total;
    private long offset;

    public SearchResultSet(String pid, String query, List<ResultEntry> results, long total, long offset) {
        this.pid = pid;
        this.query = query;
        this.results = results;
        this.total = total;
        this.offset = offset;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getQuery() {
        return query;
    }

    public List<ResultEntry> getResults() {
        return results;
    }

    public long getTotal() {
        return total;
    }

    public long getOffset() {
        return offset;
    }
}

package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query;

import java.io.IOException;

import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;

import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.server.fcs.parser.QueryParserException;

public class CQLConversionTest {
    public static void main(String[] args)
            throws QueryParserException, SRUException, CQLParseException, IOException {

        final String cqlQuery;
        if (args.length == 1) {
            cqlQuery = args[0];
        } else {
            System.err.println("Using example CQL query ...");
            cqlQuery = "cat or \"white mouse\"";
        }
        System.out.println("CQL:");
        System.out.println(cqlQuery);

        final CQLNode qn = new CQLParser().parse(cqlQuery);
        final String solrQuery = CQLToSolrConverter.convertCQLtoSolrQuery(qn);
        System.out.println();
        System.out.println("Solr:");
        System.out.println(solrQuery);
    }
}

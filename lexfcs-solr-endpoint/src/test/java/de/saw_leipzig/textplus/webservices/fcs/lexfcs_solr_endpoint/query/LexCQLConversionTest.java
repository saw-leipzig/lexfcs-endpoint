package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query;

import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.server.fcs.parser.QueryParserException;
import eu.clarin.sru.server.fcs.parser_lex.QueryNode;
import eu.clarin.sru.server.fcs.parser_lex.QueryParser;

public class LexCQLConversionTest {
    public static void main(String[] args)
            throws QueryParserException, SRUException, eu.clarin.sru.server.fcs.parser_lex.QueryParserException {

        final String lexCqlQuery;
        if (args.length == 1) {
            lexCqlQuery = args[0];
        } else {
            System.err.println("Using example LexCQL query ...");
            lexCqlQuery = "pos is \"https://universaldependencies.org/u/pos/NOUN\" AND lemma = \"drive\"";
        }
        System.out.println("LexCQL:");
        System.out.println(lexCqlQuery);

        final QueryNode qn = new QueryParser().parse(lexCqlQuery);
        final String solrQuery = LexCQLToSolrConverter.convertLexCQLtoSolrQuery(qn);
        System.out.println();
        System.out.println("Solr:");
        System.out.println(solrQuery);
    }
}

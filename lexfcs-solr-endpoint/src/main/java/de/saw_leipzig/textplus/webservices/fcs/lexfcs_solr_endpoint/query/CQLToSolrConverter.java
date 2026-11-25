package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLTermNode;

import eu.clarin.sru.server.SRUConstants;
import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.fcs.qlparser.QueryParserException;

public class CQLToSolrConverter {
    private static final Logger LOGGER = LogManager.getLogger(CQLToSolrConverter.class);

    public static String convertCQLtoSolrQuery(final CQLNode node)
            throws QueryParserException, SRUException {
        // LOGGER.debug("Query dump:\n{}", node.toXCQL());
        StringBuilder sb = new StringBuilder();

        convertCQLtoSolrSingle(node, sb);

        return sb.toString();
    }

    private static void convertCQLtoSolrSingle(final CQLNode node, StringBuilder sb)
            throws SRUException {
        if (node instanceof CQLTermNode) {
            final CQLTermNode tn = ((CQLTermNode) node);
            if (tn.getIndex() != null && !"cql.serverChoice".equalsIgnoreCase(tn.getIndex())) {
                throw new SRUException(SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Queries with queryType 'cql' do not support index/relation on '"
                                + node.getClass().getSimpleName() + "' by this FCS Endpoint.");
            }
            sb.append("{!parent which=\"_type:entry\"");
            sb.append(' ');
            sb.append("v=\"");
            sb.append("+_type:value_*");

            // split into whitespace tokens, see LexCQL `=`
            final String searchValue = tn.getTerm();
            final String[] tokens = Utils.tokenizeValue(searchValue);
            final List<String> searchValues = Arrays.asList(tokens).stream()
                    // TODO: double escape or not?
                    .map(value -> Utils.escapeValue(value, true))
                    .collect(Collectors.toList());
            for (String value : searchValues) {
                sb.append(" +value_text:");
                sb.append(value);
            }

            sb.append("\""); // v=
            sb.append("}"); // parent block query
        } else if (node instanceof CQLOrNode || node instanceof CQLAndNode) {
            final CQLBooleanNode bn = (CQLBooleanNode) node;
            if (!bn.getModifiers().isEmpty()) {
                throw new SRUException(SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Queries with queryType 'cql' do not support modifiers on '" + node.getClass().getSimpleName()
                                + "' by this FCS Endpoint.");
            }
            sb.append("( ");
            convertCQLtoSolrSingle(bn.getLeftOperand(), sb);
            if (node instanceof CQLOrNode) {
                sb.append(" OR ");
            } else if (node instanceof CQLAndNode) {
                sb.append(" AND ");
            }
            convertCQLtoSolrSingle(bn.getRightOperand(), sb);
            sb.append(" )");
        } else {
            throw new SRUException(SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                    "Queries with queryType 'cql' do not support '" + node.getClass().getSimpleName()
                            + "' by this FCS Endpoint.");
        }
    }

}

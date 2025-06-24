package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.clarin.sru.server.SRUConstants;
import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.server.fcs.parser.QueryParserException;
import eu.clarin.sru.server.fcs.parser_lex.Modifier;
import eu.clarin.sru.server.fcs.parser_lex.QueryNode;
import eu.clarin.sru.server.fcs.parser_lex.RBoolean;
import eu.clarin.sru.server.fcs.parser_lex.Relation;
import eu.clarin.sru.server.fcs.parser_lex.SearchClause;
import eu.clarin.sru.server.fcs.parser_lex.SearchClauseGroup;
import eu.clarin.sru.server.fcs.parser_lex.Subquery;

public class LexCQLToSolrConverter {
    private static final Logger LOGGER = LogManager.getLogger(LexCQLToSolrConverter.class);

    public static String convertLexCQLtoSolrQuery(final QueryNode node)
            throws QueryParserException, SRUException {
        // LOGGER.debug("Query dump:\n{}", node.toXCQL());
        StringBuilder sb = new StringBuilder();

        convertLexCQLtoSolrSingle(node, sb);

        return sb.toString();
    }

    private static void convertLexCQLtoSolrSingle(final QueryNode node, StringBuilder sb)
            throws SRUException {
        if (node instanceof SearchClause) {
            final SearchClause sc = ((SearchClause) node);
            String searchClauseQuery = convertSearchClause(sc);
            sb.append(searchClauseQuery);
        } else if (node instanceof SearchClauseGroup) {
            final SearchClauseGroup bn = (SearchClauseGroup) node;
            sb.append("( ");
            convertLexCQLtoSolrSingle(bn.getLeftChild(), sb);
            if (bn.getBoolean().equals(RBoolean.OR)) {
                sb.append(" OR ");
            } else if (bn.getBoolean().equals(RBoolean.AND)) {
                sb.append(" AND ");
            } else {
                throw new SRUException(
                        SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Queries with queryType 'lex' do not (yet) support boolean '" + bn.getBoolean()
                                + "' at this FCS Endpoint.");
            }
            convertLexCQLtoSolrSingle(bn.getRightChild(), sb);
            sb.append(" )");
        } else if (node instanceof Subquery) {
            final Subquery sq = (Subquery) node;
            sb.append("( ");
            convertLexCQLtoSolrSingle(sq.getChild(), sb);
            sb.append(" )");
        } else {
            throw new SRUException(
                    SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                    "Queries with queryType 'lex' do not support '" + node.getClass().getSimpleName()
                            + "' at this FCS Endpoint.");
        }
    }

    private static String convertSearchClause(final SearchClause sc) throws SRUException {
        StringBuilder sb = new StringBuilder();

        String field = sc.getIndex();

        // no field provided, search everywhere
        if (field == null || "cql.serverChoice".equalsIgnoreCase(field)) {
            sb.append("({!parent which=\"_type:entry\" v=\"");
            sb.append("+_type:value_*");
            sb.append(' ');
            sb.append("+value_text:").append(getEscapedSearchTerm(sc.getSearchTerm(), false, true));
            sb.append("\"})");

            return sb.toString();
        }

        // virtual language field (language of entry)
        if ("lang".equalsIgnoreCase(field)) {
            sb.append('(');
            sb.append("_type:entry AND xmlLang:");
            sb.append(getEscapedSearchTerm(sc.getSearchTerm(), false, false));
            sb.append(')');
            return sb.toString();
        }

        // check if known relations
        final Relation rel = sc.getRelation();
        if (rel != null) {
            if (!"=".equals(rel.getRelation()) && !"<>".equals(rel.getRelation()) && !"==".equals(rel.getRelation())
                    && !"is".equals(rel.getRelation())) {
                throw new SRUException(SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Queries with queryType 'lex' do not support '" + rel.getRelation()
                                + "' relations at this FCS Endpoint.");
            }
        }
        String relation = (rel != null) ? rel.getRelation().toLowerCase() : null;

        // check and normalize field spelling (in case it is not quite to spec)
        switch (field.toLowerCase()) {
            // "known" alias field names
            case "def":
                field = "definition";
                break;

            // handle known fields
            // correct "spelling"
            case "entryid":
                field = "entryId";
                break;
            case "senseref":
                field = "senseRef";
                break;
            // nothing to do
            case "lemma":
            case "translation":
            case "transcription":
            case "phonetic":
            case "definition":
            case "etymology":
            case "case":
            case "number":
            case "gender":
            case "pos":
            case "baseform":
            case "segmentation":
            case "sentiment":
            case "frequency":
            case "antonym":
            case "hyponym":
            case "hypernym":
            case "meronym":
            case "holonym":
            case "synonym":
            case "related":
            case "ref":
            case "citation":
                break;

            // error case?
            default:
                throw new SRUException(
                        SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Queries with queryType 'lex' do not (yet) support the unknown field '" + field
                                + "' at this FCS Endpoint.");
        }

        // start building the generic query
        sb.append("({!parent");
        sb.append(" which=\"_type:entry\"");
        sb.append(' ').append("v=\""); // start of block query

        // type of field
        sb.append("+_type:value_").append(field);

        // attributes
        if (rel.getModifiers() != null && !rel.getModifiers().isEmpty()) {
            // "is" is without any relation modifiers!
            if ("is".equals(relation)) {
                throw new SRUException(SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                        "Queries with queryType 'lex' do not (yet?) support relation modifiers on the '"
                                + relation
                                + "' relation at this FCS Endpoint.");
            }

            // add relation modifiers if possible
            for (Modifier modifier : rel.getModifiers()) {
                String modifierName = modifier.getName();
                switch (modifierName.toLowerCase()) {
                    case "lang":
                        if (modifier.getValue() == null || modifier.getValue().trim().isEmpty()) {
                            throw new SRUException(
                                    SRUConstants.SRU_QUERY_SYNTAX_ERROR,
                                    "'lang' relation modifiers for 'lex' queries require a language code value");
                        }
                        if (modifier.getRelation() == null || !"=".equals(modifier.getRelation())) {
                            throw new SRUException(
                                    SRUConstants.SRU_QUERY_SYNTAX_ERROR,
                                    "'lang' relation modifiers for 'lex' queries require the '=' comparitor");
                        }
                        sb.append(" ").append("+xmlLang:").append(modifier.getValue().trim());
                        break;

                    case "masked":
                        // default, ignore
                        break;

                    // as of yet, unsupported
                    case "unmasked":
                    case "ignorecase":
                    case "respectcase":
                    case "ignoreaccents":
                    case "respectaccents":
                    case "honorwhitespace":
                    case "regexp":
                    case "partialmatch":
                    case "fullmatch":
                        throw new SRUException(
                                SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                                "Queries with queryType 'lex' do not (yet) support the relation modifier '"
                                        + modifierName + "' at this FCS Endpoint.");

                    default:
                        throw new SRUException(
                                SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                                "Queries with queryType 'lex' do not (yet) support the unknown relation modifier '"
                                        + modifierName + "' at this FCS Endpoint.");
                }
            }
        }

        // "is" relation only on vocabValueRef
        if ("is".equalsIgnoreCase(relation)) {
            sb.append(' ');
            sb.append('+');
            sb.append("vocabValueRef:");
            sb.append(getEscapedSearchTerm(sc.getSearchTerm(), true, true));

            sb.append('"'); // v="
            sb.append("})"); // ({!parent
            return sb.toString();
        }

        // other relations, value handling

        // TODO: handle other relation modifiers
        char operator = '+';
        if ("=".equals(relation) || "==".equals(relation)) {
            operator = '+';
        } else if ("<>".equals(relation)) {
            operator = '-';
        } else {
            throw new SRUException(SRUConstants.SRU_CANNOT_PROCESS_QUERY_REASON_UNKNOWN,
                    "Queries with queryType 'lex' do not (yet) support '" + relation
                            + "' relations at this FCS Endpoint.");
        }

        sb.append(' ');
        sb.append(operator);
        sb.append("value:");
        sb.append(getEscapedSearchTerm(sc.getSearchTerm(), "==".equals(relation), true));

        sb.append('"'); // v="
        sb.append("})"); // ({!parent

        return sb.toString();
    }

    private static String getEscapedSearchTerm(String value, boolean quote, boolean escapeQuote) {
        boolean requireQuoting = quote;
        // the value requires quoting if it contains certain characters
        if (value.contains(" ") || value.contains("=") || value.contains("<") || value.contains(">")
                || value.contains(")") || value.contains("(") || value.contains("/") || value.contains("\"")
                || value.contains("\\") || value.contains(":")) {
            requireQuoting = true;
        }
        // escape quotes and backslashes
        value = value.replace("\\", "\\\\").replace("\"", "\\\"");
        // if values need quoting, escape backslashes in value again and add quotes
        // (add escapes for outer quotes if required)
        if (requireQuoting) {
            String toAdd = (escapeQuote) ? "\\\"" : "\"";
            value = toAdd + value.replace("\\", "\\\\") + toAdd;
        }
        return value;
    }

}

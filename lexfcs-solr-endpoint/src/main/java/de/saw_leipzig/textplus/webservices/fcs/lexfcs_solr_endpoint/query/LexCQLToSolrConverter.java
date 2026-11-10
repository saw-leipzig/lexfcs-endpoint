package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.clarin.sru.server.SRUConstants;
import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.fcs.qlparser.QueryParserException;
import eu.clarin.sru.fcs.qlparser.lex.Modifier;
import eu.clarin.sru.fcs.qlparser.lex.QueryNode;
import eu.clarin.sru.fcs.qlparser.lex.RBoolean;
import eu.clarin.sru.fcs.qlparser.lex.Relation;
import eu.clarin.sru.fcs.qlparser.lex.SearchClause;
import eu.clarin.sru.fcs.qlparser.lex.SearchClauseGroup;
import eu.clarin.sru.fcs.qlparser.lex.Subquery;

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

        // no field provided, search on lemma
        if (field == null || "cql.serverChoice".equalsIgnoreCase(field)) {
            field = "lemma";
        }

        // virtual language field (language of entry)
        if ("lang".equalsIgnoreCase(field)) {
            sb.append('(');
            sb.append("_type:entry AND xmlLang:");
            // plain string (only escape minus "-" if it appears)
            sb.append(Utils.escapeValue(sc.getSearchTerm()));
            sb.append(')');
            return sb.toString();
        }

        // check if known relations
        final Relation rel = sc.getRelation();
        final String relation = (rel != null) ? rel.getRelation().toLowerCase() : "=";
        if (!"=".equals(relation) && !"<>".equals(relation) && !"==".equals(relation) && !"is".equals(relation)) {
            throw new SRUException(SRUConstants.SRU_UNSUPPORTED_RELATION,
                    "Queries with queryType 'lex' do not support '" + rel.getRelation()
                            + "' relations at this FCS Endpoint.");
        }

        // check and normalize field spelling (in case it is not quite to spec)
        field = field.toLowerCase();
        switch (field) {
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

            // "any" field
            case "any":
                break;

            // nothing to do
            case "lemma":
            case "translation":
            case "transcription":
            case "phonetic":
            case "definition":
            case "etymology":
            case "case":
            case "degree":
            case "gender":
            case "mood":
            case "number":
            case "pos":
            case "tense":
            case "grammar":
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
                        SRUConstants.SRU_UNSUPPORTED_INDEX,
                        "Queries with queryType 'lex' do not (yet) support the unknown field '" + field
                                + "' at this FCS Endpoint.");
        }

        // -------------------------------------

        // start building the generic query
        sb.append("({!parent");
        sb.append(" which=\"_type:entry\"");
        sb.append(' ').append("v=\""); // start of block query

        // # type of field
        // if "any" field provided, then do not filter and search everywhere
        if (!"any".equalsIgnoreCase(field)) {
            sb.append("+_type:value_").append(field);
        }

        // # attributes
        boolean hasRegexpModifier = false;
        if (rel != null && rel.getModifiers() != null && !rel.getModifiers().isEmpty()) {
            // "is" is without any relation modifiers!
            // TODO: still correct?
            if ("is".equals(relation)) {
                // - ignorecase/respectcase could make sense
                // - regexp ... well maybe but is a bit more complicated
                // - *accents/lang do not make sense
                throw new SRUException(SRUConstants.SRU_UNSUPPORTED_RELATION_MODIFIER,
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

                    case "regexp":
                        // just a sanity check
                        if ("=".equalsIgnoreCase(relation)) {
                            throw new SRUException(
                                    SRUConstants.SRU_UNSUPPORTED_RELATION_MODIFIER,
                                    "Queries with queryType 'lex' do not support the relation modifier '"
                                            + modifierName + "' with the '=' relations at this FCS Endpoint.");
                        }
                        hasRegexpModifier = true;
                        break;

                    // as of yet, unsupported
                    case "ignorecase":
                    case "respectcase":
                        // NOTE: could work with regexp modifier
                        // TODO: need to test how indexing (with lowercase) influences this
                    case "ignoreaccents":
                    case "respectaccents":
                        // NOTE: we don't really want to combine those with regexp
                        throw new SRUException(
                                SRUConstants.SRU_UNSUPPORTED_RELATION_MODIFIER,
                                "Queries with queryType 'lex' do not (yet) support the relation modifier '"
                                        + modifierName + "' at this FCS Endpoint.");

                    case "masked":
                    case "unmasked":
                    case "honorwhitespace":
                    case "partialmatch":
                    case "fullmatch":
                        // TODO: hard error or silent warning?
                        throw new SRUException(
                                SRUConstants.SRU_UNSUPPORTED_RELATION_MODIFIER,
                                "Queries with queryType 'lex' do not support the relation modifier '"
                                        + modifierName + "' (prior to LexFCS 1.0) at this FCS Endpoint.");
                    default:
                        throw new SRUException(
                                SRUConstants.SRU_UNSUPPORTED_RELATION_MODIFIER,
                                "Queries with queryType 'lex' do not (yet) support the unknown relation modifier '"
                                        + modifierName + "' at this FCS Endpoint.");
                }
            }
        }

        // -------------------------------------

        // # other relations, value handling

        char operator = '+';
        String searchField = "value_text";
        String searchValue = sc.getSearchTerm();
        List<String> searchValues = Collections.emptyList();

        if ("=".equals(relation)) {
            operator = '+';
            searchField = "value_text";

            // TODO: special handling required if ":" contained due to field tokenization?
            // TODO: in general for all fields when value contains certain characters?
            // TODO: other fields
            // NOTE: this is more or less a hack
            if ("entryid".equalsIgnoreCase(field) || "senseref".equalsIgnoreCase(field)
                    || "ref".equalsIgnoreCase(field)) {
                searchValue = Utils.escapeValue(searchValue, true);
                searchValue = "\\\"" + searchValue + "\\\"";
                searchValues = List.of(searchValue);
            } else {
                // TODO: this might not be the best tokenization as Solr might also split on
                // punctuation, too!
                final String[] tokens = Utils.tokenizeValue(searchValue);
                searchValues = Arrays.asList(tokens).stream()
                        .map(value -> Utils.escapeValue(value, true))
                        .collect(Collectors.toList());
            }
        } else if ("==".equals(relation)) {
            operator = '+';
            searchField = "value";

            if (hasRegexpModifier) {
                searchValue = Utils.escapeRegexpValue(searchValue);
                searchValue = searchValue.replace(" ", "\\ ");
                // TODO: do we need to escape backslashes again?
                // searchValue = searchValue.replace("\\", "\\\\");
                // searchValue = "\\\"" + searchValue + "\\\"";
            } else {
                searchValue = Utils.escapeValue(searchValue, true);
            }
            searchValues = List.of(searchValue);
        } else if ("<>".equals(relation)) {
            operator = '-';
            searchField = "value";

            if (hasRegexpModifier) {
                searchValue = Utils.escapeRegexpValue(searchValue);
                searchValue = searchValue.replace(" ", "\\ ");
                // same as "=="
            } else {
                searchValue = Utils.escapeValue(searchValue);
            }
            searchValues = List.of(searchValue);
        } else if ("is".equalsIgnoreCase(relation)) {
            // "is" relation only on vocabValueRef
            operator = '+';
            searchField = "vocabValueRef";

            switch (field.toLowerCase()) {
                case "pos":
                case "case":
                case "degree":
                case "gender":
                case "mood":
                case "number":
                case "sentiment":
                case "tense":
                    searchValue = expandEntityValue(searchValue, field, true);
                    break;
                default:
                    // NOTE: vocabValueRef for all fields!
                    // throw new
                    // SRUException(SRUConstants.SRU_UNSUPPORTED_COMBINATION_OF_RELATION_AND_INDEX,
                    // "Queries with queryType 'lex' do not (yet) support '" + relation
                    // + "' relation on field '" + field + "' at this FCS Endpoint.");
            }

            // getEscapedSearchTerm(sc.getSearchTerm(), true, true)
            searchValue = Utils.escapeValue(searchValue);
            searchValue = "\\\"" + searchValue + "\\\"";
            searchValues = List.of(searchValue);
        } else {
            throw new SRUException(SRUConstants.SRU_UNSUPPORTED_RELATION,
                    "Queries with queryType 'lex' do not (yet) support '" + relation
                            + "' relations at this FCS Endpoint.");
        }

        for (String value : searchValues) {
            sb.append(' ');
            sb.append(operator);
            sb.append(searchField);
            sb.append(':');
            sb.append(value);
        }

        sb.append('"'); // v="
        sb.append("})"); // ({!parent

        return sb.toString();
    }

    private static String getEscapedSearchTerm(String value, boolean quote, boolean escapeQuote) {
        boolean requireEscaping = quote;
        // the value requires quoting if it contains certain characters
        if (Utils.hasSpecialCharactersRequiringEscaping(value)) {
            requireEscaping = true;
        }
        // escape quotes and backslashes
        value = value.replace("\\", "\\\\").replace("\"", "\\\"");
        // if values need quoting, escape backslashes in value again and add quotes
        // (add escapes for outer quotes if required)
        if (requireEscaping) {
            String toAdd = (escapeQuote) ? "\\\"" : "\"";
            value = toAdd + value.replace("\\", "\\\\") + toAdd;
        }
        return value;
    }

    private static String expandEntityValue(String value, String field, boolean strict) throws SRUException {
        if (value.indexOf(':') == -1) {
            // No protocol part, assume it is a abbreviated UD entity value

            // normalize value spelling? (e.g. allow any case for UD?)
            // maybe in combination with relation modifier /ignorecase

            // check for known lex fields
            switch (field.toLowerCase()) {
                case "pos":
                    switch (value) {
                        case "ADJ":
                        case "ADV":
                        case "INTJ":
                        case "NOUN":
                        case "PROPN":
                        case "VERB":
                        case "ADP":
                        case "AUX":
                        case "CCONJ":
                        case "DET":
                        case "NUM":
                        case "PART":
                        case "PRON":
                        case "SCONJ":
                        case "PUNCT":
                        case "SYM":
                        case "X":
                            value = "https://universaldependencies.org/u/pos/" + value;
                            break;

                        default:
                            if (strict) {
                                // TODO: unsure about this diagnostic
                                throw new SRUException(SRUConstants.SRU_TERM_IN_INVALID_FORMAT_FOR_INDEX_OR_RELATION,
                                        "Queries with queryType 'lex' do not support unknown value '" + value
                                                + "' in field '" + field + "' at this FCS Endpoint.");
                            }
                            break;
                    }
                    break;

                case "case":
                    switch (value) {
                        case "Abs":
                        case "Acc":
                        case "Erg":
                        case "Nom":
                        case "Abe":
                        case "Ben":
                        case "Cau":
                        case "Cmp":
                        case "Cns":
                        case "Com":
                        case "Dat":
                        case "Dis":
                        case "Equ":
                        case "Gen":
                        case "Ins":
                        case "Par":
                        case "Tem":
                        case "Tra":
                        case "Voc":
                        case "Abl":
                        case "Add":
                        case "Ade":
                        case "All":
                        case "Del":
                        case "Ela":
                        case "Ess":
                        case "Ill":
                        case "Ine":
                        case "Lat":
                        case "Loc":
                        case "Per":
                        case "Sbe":
                        case "Sbl":
                        case "Spl":
                        case "Sub":
                        case "Sup":
                        case "Ter":
                            value = "https://universaldependencies.org/u/feat/Case#" + value;
                            break;
                        default:
                            if (strict) {
                                // TODO: unsure about this diagnostic
                                throw new SRUException(SRUConstants.SRU_TERM_IN_INVALID_FORMAT_FOR_INDEX_OR_RELATION,
                                        "Queries with queryType 'lex' do not support unknown value '" + value
                                                + "' in field '" + field + "' at this FCS Endpoint.");
                            }
                            break;
                    }
                    break;

                case "degree":
                    switch (value) {
                        case "Abs":
                        case "Aug":
                        case "Cmp":
                        case "Dim":
                        case "Equ":
                        case "Pos":
                        case "Sup":
                            value = "https://universaldependencies.org/u/feat/Degree#" + value;
                            break;
                        default:
                            if (strict) {
                                // TODO: unsure about this diagnostic
                                throw new SRUException(SRUConstants.SRU_TERM_IN_INVALID_FORMAT_FOR_INDEX_OR_RELATION,
                                        "Queries with queryType 'lex' do not support unknown value '" + value
                                                + "' in field '" + field + "' at this FCS Endpoint.");
                            }
                            break;
                    }
                    break;

                case "gender":
                    switch (value) {
                        case "Com":
                        case "Fem":
                        case "Masc":
                        case "Neut":
                            value = "https://universaldependencies.org/u/feat/Gender#" + value;
                            break;
                        default:
                            if (strict) {
                                // TODO: unsure about this diagnostic
                                throw new SRUException(SRUConstants.SRU_TERM_IN_INVALID_FORMAT_FOR_INDEX_OR_RELATION,
                                        "Queries with queryType 'lex' do not support unknown value '" + value
                                                + "' in field '" + field + "' at this FCS Endpoint.");
                            }
                            break;
                    }
                    break;

                case "mood":
                    switch (value) {
                        case "Adm":
                        case "Cnd":
                        case "Des":
                        case "Imp":
                        case "Ind":
                        case "Int":
                        case "Irr":
                        case "Jus":
                        case "Nec":
                        case "Opt":
                        case "Pot":
                        case "Prp":
                        case "Qot":
                        case "Sub":
                            value = "https://universaldependencies.org/u/feat/Mood#" + value;
                            break;
                        default:
                            if (strict) {
                                // TODO: unsure about this diagnostic
                                throw new SRUException(SRUConstants.SRU_TERM_IN_INVALID_FORMAT_FOR_INDEX_OR_RELATION,
                                        "Queries with queryType 'lex' do not support unknown value '" + value
                                                + "' in field '" + field + "' at this FCS Endpoint.");
                            }
                            break;
                    }
                    break;

                case "number":
                    switch (value) {
                        case "Coll":
                        case "Count":
                        case "Dual":
                        case "Grpa":
                        case "Grpl":
                        case "Inv":
                        case "Pauc":
                        case "Plur":
                        case "Ptan":
                        case "Sing":
                        case "Tri":
                            value = "https://universaldependencies.org/u/feat/Number#" + value;
                            break;
                        default:
                            if (strict) {
                                // TODO: unsure about this diagnostic
                                throw new SRUException(SRUConstants.SRU_TERM_IN_INVALID_FORMAT_FOR_INDEX_OR_RELATION,
                                        "Queries with queryType 'lex' do not support unknown value '" + value
                                                + "' in field '" + field + "' at this FCS Endpoint.");
                            }
                            break;
                    }
                    break;

                case "sentiment":
                    switch (value) {
                        case "Neg":
                        case "Pos":
                            value = "https://universaldependencies.org/u/feat/Sentiment#" + value;
                            break;
                        default:
                            if (strict) {
                                // TODO: unsure about this diagnostic
                                throw new SRUException(SRUConstants.SRU_TERM_IN_INVALID_FORMAT_FOR_INDEX_OR_RELATION,
                                        "Queries with queryType 'lex' do not support unknown value '" + value
                                                + "' in field '" + field + "' at this FCS Endpoint.");
                            }
                            break;
                    }
                    break;

                case "tense":
                    switch (value) {
                        case "Fut":
                        case "Imp":
                        case "Past":
                        case "Pqp":
                        case "Pres":
                            value = "https://universaldependencies.org/u/feat/Tense#" + value;
                            break;
                        default:
                            if (strict) {
                                // TODO: unsure about this diagnostic
                                throw new SRUException(SRUConstants.SRU_TERM_IN_INVALID_FORMAT_FOR_INDEX_OR_RELATION,
                                        "Queries with queryType 'lex' do not support unknown value '" + value
                                                + "' in field '" + field + "' at this FCS Endpoint.");
                            }
                            break;
                    }
                    break;

                default:
                    // TODO: hard fail or since unknown field, keep as is and ignore anything else?
                    if (strict) {
                        throw new SRUException(SRUConstants.SRU_UNSUPPORTED_COMBINATION_OF_RELATION_AND_INDEX,
                                "Queries with queryType 'lex' do not support 'is' relation value expansion for '"
                                        + field + "' field at this FCS Endpoint.");
                    }
                    break;
            }
        }

        // NOTE: UD specific behaviour: strip ".html" suffix
        // (data indexed in solr needs to also have omitted this suffix!)
        if (value.startsWith("https://universaldependencies.org/u/") && value.endsWith((".html"))) {
            value = value.substring(0, value.length() - 5);
        } else if (value.startsWith("https://universaldependencies.org/u/feat/") && value.contains(".html#")) {
            int hashIdx = value.indexOf(".html#");
            if (hashIdx > 40) {
                value = value.substring(0, hashIdx) + value.substring(hashIdx + 5);
            }
        }

        return value;
    }

}

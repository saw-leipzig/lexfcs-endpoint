package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query;

import java.util.regex.Pattern;

public final class Utils {
    // https://solr.apache.org/guide/solr/latest/query-guide/standard-query-parser.html#escaping-special-characters
    // solr special chars: + - && || ! ( ) { } [ ] ^ " ~ * ? : /

    public static boolean containsSolrSpecialChars(String value, boolean includeMaskingChars) {
        return (value.contains("+")
                || value.contains("-")
                || value.contains("&&")
                || value.contains("||")
                || value.contains("!")
                || value.contains("(")
                || value.contains(")")
                || value.contains("{")
                || value.contains("}")
                || value.contains("[")
                || value.contains("]")
                || value.contains("^")
                || value.contains("\"")
                || value.contains("~")
                || (includeMaskingChars && value.contains("*")) // NOTE: we want to just forward it
                || (includeMaskingChars && value.contains("?"))
                || value.contains(":")
                || value.contains("/"));
    }

    public static boolean hasSpecialCharactersRequiringEscaping(String value) {
        return (value.contains("\\")
                || value.contains(" ")
                || value.contains("=") // XXX: ?
                || value.contains("<") // XXX: ?
                || value.contains(">") // XXX: ?
                || containsSolrSpecialChars(value, false));
        // works: &, <, >, |
    }

    public static String escapeValue(String value) {
        return escapeValue(value, false);
    }

    public static String escapeSolrSpecialChars(String value, boolean includeMaskingChars) {
        value = value.replace("+", "\\+");
        value = value.replace("-", "\\-");

        value = value.replace("&&", "\\&&"); // XXX: ?
        value = value.replace("||", "\\||"); // XXX: ?

        value = value.replace("!", "\\!");
        value = value.replace("(", "\\(");
        value = value.replace(")", "\\)");
        value = value.replace("{", "\\{");
        value = value.replace("}", "\\}");
        value = value.replace("[", "\\[");
        value = value.replace("]", "\\]");
        value = value.replace("^", "\\^");
        value = value.replace("\"", "\\\"");
        value = value.replace("~", "\\~");
        if (includeMaskingChars) {
            // NOTE: we want to just forward it
            value = value.replace("*", "\\*");
            value = value.replace("?", "\\?");
        }
        value = value.replace(":", "\\:");
        value = value.replace("/", "\\/");

        return value;
    }

    public static String escapeValue(String value, boolean doubleEscapeSolrSpecialChars) {
        // likely also quite good idea to escape those
        // TODO: does it need to be double?
        value = value.replace("\\", "\\\\");

        // important for non-quoted strings
        value = value.replace(" ", "\\\\ ");

        value = escapeSolrSpecialChars(value, false);
        if (doubleEscapeSolrSpecialChars) {
            value = escapeSolrSpecialChars(value, false);
        }

        // unsure if required but maybe good idea
        value = value.replace("<", "\\<");
        value = value.replace(">", "\\>");
        value = value.replace("=", "\\=");

        return value;
    }

    static Pattern REPAT_ENDS_ANCHORED = Pattern.compile("(?<!\\\\)(?:\\\\\\\\)*\\$$");

    public static String escapeRegexpValue(String value) {
        value = value.replace("&&", "\\&&"); // XXX: ?
        value = value.replace("||", "\\||"); // XXX: ?
        value = value.replace("!", "\\!");
        value = value.replace("\"", "\\\"");
        value = value.replace("~", "\\~");
        // value = value.replace("*", "\\*"); // NOTE: we want to just forward it
        value = value.replace(":", "\\:");
        value = value.replace("/", "\\/");

        // solr/lucene regexp is always anchored
        // so if user does not explicitely anchor, we unanchor explicitely
        if (!value.startsWith("^") && !value.startsWith(".*")) {
            // if it already starts with ".*", do not append again
            value = ".*" + value;
        } else if (value.startsWith("^")) {
            // we need to strip "^" as by default anchored and it is not recognized as
            // special char!
            value = value.substring(1);
        }
        boolean endswithAnchor = value.endsWith("$") && REPAT_ENDS_ANCHORED.matcher(value).find();
        if (!endswithAnchor && (!value.endsWith(".*") && !value.endsWith("\\.*"))) {
            value = value + ".*";
        } else if (endswithAnchor) {
            // strip anchor
            value = value.substring(0, value.length() - 1);
        }

        value = "/" + value + "/";

        return value;
    }

    // lucene regex special chars: . ? + * | { } [ ] ( ) " \
    // https://help.oncrawl.com/en/articles/1685982-lucene-regex-cheat-sheet
    // https://lucene.apache.org/core/9_0_0/core/org/apache/lucene/util/automaton/RegExp.html
    // https://target.github.io/LUCkyRegex/

    public static String[] tokenizeValue(String value) {
        return value.strip().split("\\s+");
    }
}

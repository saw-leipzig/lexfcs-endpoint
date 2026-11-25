package de.saw_leipzig.textplus.webservices.fcs.lexfcs_solr_endpoint.query;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilsTest {

    @Test
    public void testEscapeValue() {
        // simple strings remain as is
        assertEquals("abc", Utils.escapeValue("abc"));

        // empty spaces / multi words
        assertEquals("abc\\\\ def\\\\ ghi", Utils.escapeValue("abc def ghi"));

        // some special chars?
        assertEquals("abc\\-def", Utils.escapeValue("abc-def")); // TODO: check actual Solr matching
    }

    @Test
    public void testEscapeRegexpValue() {
        // unanchored
        assertEquals("/.*abc.*/", Utils.escapeRegexpValue("abc"));

        // explicitely anchored
        assertEquals("/abc.*/", Utils.escapeRegexpValue("^abc"));
        assertEquals("/.*abc/", Utils.escapeRegexpValue("abc$"));
        assertEquals("/abc/", Utils.escapeRegexpValue("^abc$"));

        // escaping of anchors
        assertEquals("/.*\\^abc\\$.*/", Utils.escapeRegexpValue("\\^abc\\$"));
        assertEquals("/.*\\^abc\\\\/", Utils.escapeRegexpValue("\\^abc\\\\$"));
        assertEquals("/.*\\\\\\^abc.*/", Utils.escapeRegexpValue("\\\\\\^abc"));

        // unanchored
        assertEquals("/.*abc.*/", Utils.escapeRegexpValue(".*abc.*"));
        assertEquals("/.*abc\\.*/", Utils.escapeRegexpValue(".*abc\\.*"));
    }

}

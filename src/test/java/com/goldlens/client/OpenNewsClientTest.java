package com.goldlens.client;

import com.goldlens.dto.GoldNewsItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenNewsClientTest {

    @Test
    void parsesGdeltArticles() {
        String body = "{\"articles\":[{\"title\":\" Gold rises on rate cut hopes \",\"url\":\"https://a.test/1\","
                + "\"domain\":\"a.test\",\"seendate\":\"20261002T091500Z\"},{\"title\":\"No url\"}]}";
        List<GoldNewsItem> items = OpenNewsClient.parseGdelt(body);
        assertEquals(1, items.size());
        assertEquals("Gold rises on rate cut hopes", items.get(0).getTitle());
        assertEquals("a.test", items.get(0).getSource());
        assertEquals("2026-10-02T09:15:00Z", items.get(0).getPublishedAt());
    }

    @Test
    void gdeltPlainTextErrorsYieldNothing() {
        assertTrue(OpenNewsClient.parseGdelt("Please limit requests to one every 5 seconds.").isEmpty());
    }

    @Test
    void parsesRssItemsAndSkipsOnesWithoutLink() {
        String xml = "<rss><channel><item><title>Dollar weakens as yields fall</title><link>https://b.test/2</link>"
                + "<pubDate>Fri, 02 Oct 2026 09:00:00 GMT</pubDate></item><item><title>No link</title></item></channel></rss>";
        List<GoldNewsItem> items = OpenNewsClient.parseRss(xml, "B");
        assertEquals(1, items.size());
        assertEquals("B", items.get(0).getSource());
        assertEquals("2026-10-02T09:00:00Z", items.get(0).getPublishedAt());
    }

    @Test
    void rejectsXmlWithDoctype() {
        assertTrue(OpenNewsClient.parseRss("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><rss/>", "B").isEmpty());
    }
}

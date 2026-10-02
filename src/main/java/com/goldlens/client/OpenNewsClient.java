package com.goldlens.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldlens.dto.GoldNewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keyless news: the GDELT DOC API (free for commercial use with attribution, see
 * https://www.gdeltproject.org/about.html) and publishers' RSS feeds. Both give headline,
 * link and source only, which is all the dashboard shows.
 */
@Component
public class OpenNewsClient {

    private static final Logger log = LoggerFactory.getLogger(OpenNewsClient.class);

    static final String PROVIDER_NAME = "gdelt+rss";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final DateTimeFormatter GDELT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String GDELT_QUERY =
            "(gold OR bullion OR \"precious metals\" OR \"Federal Reserve\" OR \"rate cut\" OR \"Treasury yields\") sourcelang:english";

    private final WebClient webClient;
    private final String gdeltUrl;
    private final List<String[]> feeds = new ArrayList<>();

    public OpenNewsClient(
            @Value("${news.gdelt.base-url:https://api.gdeltproject.org/api/v2/doc/doc}") String gdeltUrl,
            // "Name|https://feed,Name2|https://feed2"
            @Value("${news.rss-feeds:The Economic Times|https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms,"
                    + "Mint|https://www.livemint.com/rss/markets,"
                    + "Business Standard|https://www.business-standard.com/rss/markets-106.rss}") String feedList) {
        this.gdeltUrl = gdeltUrl;
        for (String part : feedList.split(",")) {
            String[] nameAndUrl = part.split("\\|", 2);
            if (nameAndUrl.length == 2 && nameAndUrl[1].trim().startsWith("https://")) {
                feeds.add(new String[]{nameAndUrl[0].trim(), nameAndUrl[1].trim()});
            }
        }
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; GoldLens/1.0)")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /** Never fails: a source that errors is skipped, and an empty list means none answered. */
    public Optional<List<GoldNewsItem>> fetchGoldNews() {
        List<GoldNewsItem> items = new ArrayList<>();
        try {
            String body = webClient.get()
                    .uri(URI.create(gdeltUrl + "?query=" + java.net.URLEncoder.encode(GDELT_QUERY, StandardCharsets.UTF_8)
                            + "&mode=artlist&maxrecords=40&format=json&sort=datedesc&timespan=2d"))
                    .retrieve().bodyToMono(String.class).block(TIMEOUT);
            items.addAll(parseGdelt(body));
        } catch (Exception e) {
            log.warn("[OpenNews] GDELT failed: {}", e.getMessage());
        }
        for (String[] feed : feeds) {
            try {
                String xml = webClient.get().uri(URI.create(feed[1]))
                        .retrieve().bodyToMono(String.class).block(TIMEOUT);
                items.addAll(parseRss(xml, feed[0]));
            } catch (Exception e) {
                log.warn("[OpenNews] {} failed: {}", feed[0], e.getMessage());
            }
        }
        items.sort((a, b) -> String.valueOf(b.getPublishedAt()).compareTo(String.valueOf(a.getPublishedAt())));
        log.info("[OpenNews] Fetched {} articles", items.size());
        return items.isEmpty() ? Optional.empty() : Optional.of(items);
    }

    public String getProviderName() {
        return PROVIDER_NAME;
    }

    static List<GoldNewsItem> parseGdelt(String body) {
        List<GoldNewsItem> items = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return items;
        }
        try {
            // GDELT answers rate-limit and bad-query errors with plain text, which is not JSON.
            JsonNode articles = MAPPER.readTree(body).path("articles");
            for (JsonNode a : articles) {
                String title = a.path("title").asText("").trim();
                String url = a.path("url").asText("");
                if (title.isEmpty() || url.isEmpty()) {
                    continue;
                }
                String published = gdeltDate(a.path("seendate").asText(""));
                items.add(GoldNewsItem.builder().title(title).source(a.path("domain").asText("GDELT"))
                        .url(url).publishedAt(published).build());
            }
        } catch (Exception e) {
            log.warn("[OpenNews] GDELT body was not JSON");
        }
        return items;
    }

    static List<GoldNewsItem> parseRss(String xml, String source) {
        List<GoldNewsItem> items = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return items;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.trim().getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = doc.getElementsByTagName("item");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element item = (Element) nodes.item(i);
                String title = text(item, "title");
                String link = text(item, "link");
                if (title.isEmpty() || !link.startsWith("http")) {
                    continue;
                }
                items.add(GoldNewsItem.builder().title(title).source(source).url(link)
                        .publishedAt(rssDate(text(item, "pubDate"))).build());
            }
        } catch (Exception e) {
            log.warn("[OpenNews] {} was not valid RSS: {}", source, e.getMessage());
        }
        return items;
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? "" : list.item(0).getTextContent().trim();
    }

    private static String rssDate(String raw) {
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toString();
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(raw).toInstant().toString();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    /** GDELT dates look like 20261002T091500Z. */
    private static String gdeltDate(String raw) {
        try {
            return LocalDateTime.parse(raw, GDELT_DATE).toInstant(ZoneOffset.UTC).toString();
        } catch (Exception e) {
            return "";
        }
    }
}

package com.goldlens.service;

import com.goldlens.client.GNewsClient;
import com.goldlens.client.NewsApiClient;
import com.goldlens.dto.GoldNewsItem;
import com.goldlens.dto.GoldNewsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoldNewsServiceTest {

    private NewsApiClient newsApi;
    private GNewsClient gNews;
    private GoldNewsService service;

    @BeforeEach
    void setUp() {
        newsApi = mock(NewsApiClient.class);
        gNews = mock(GNewsClient.class);
        when(newsApi.isConfigured()).thenReturn(true);
        when(gNews.isConfigured()).thenReturn(true);
        when(newsApi.getProviderName()).thenReturn("newsapi");
        when(gNews.getProviderName()).thenReturn("gnews");
        service = new GoldNewsService(newsApi, gNews);
    }

    private static GoldNewsItem article(String title, String url) {
        return GoldNewsItem.builder().title(title).source("src").url(url).publishedAt("2026-09-29").build();
    }

    @Test
    void servesCachedResponseInsteadOfCallingProvidersAgain() {
        when(newsApi.fetchGoldNews()).thenReturn(Optional.of(List.of(
                article("Gold rises on rate cut hopes", "u1"),
                article("Dollar weakens as yields fall", "u2"),
                article("Central bank gold buying hits record", "u3"))));

        GoldNewsResponse first = service.getGoldNews();
        GoldNewsResponse second = service.getGoldNews();

        assertEquals(3, first.getItems().size());
        assertEquals(first, second);
        verify(newsApi, times(1)).fetchGoldNews();
        verify(gNews, never()).fetchGoldNews();
    }

    @Test
    void topsUpFromGNewsWhenPrimaryHasTooFewRelevantArticles() {
        when(newsApi.fetchGoldNews()).thenReturn(Optional.of(List.of(
                article("Gold rises on rate cut hopes", "u1"),
                article("New smartphone launch", "x1"),
                article("Dollar weakens as yields fall", "u2"))));
        when(gNews.fetchGoldNews()).thenReturn(Optional.of(List.of(
                article("Gold rises on rate cut hopes", "u1"),   // duplicate URL, skipped
                article("Bullion demand climbs in Asia", "g1"))));

        GoldNewsResponse response = service.getGoldNews();

        assertEquals(3, response.getItems().size());
        assertEquals("newsapi+gnews", response.getProvider());
        assertTrue(response.getItems().stream().allMatch(i -> i.getSentiment() != null));
    }

    @Test
    void showsFewerThanThreeRelevantArticlesInsteadOfNothing() {
        when(newsApi.fetchGoldNews()).thenReturn(Optional.of(List.of(
                article("Gold rises on rate cut hopes", "u1"),
                article("Dollar weakens as yields fall", "u2"),
                article("New smartphone launch", "x1"))));
        when(gNews.fetchGoldNews()).thenReturn(Optional.empty()); // e.g. 429, daily limit reached

        GoldNewsResponse response = service.getGoldNews();

        assertEquals(2, response.getItems().size());
        assertEquals("newsapi", response.getProvider());
    }

    @Test
    void returnsEmptyWhenNoProviderHasRelevantArticles() {
        when(newsApi.fetchGoldNews()).thenReturn(Optional.empty());
        when(gNews.fetchGoldNews()).thenReturn(Optional.of(List.of(article("New smartphone launch", "x1"))));

        GoldNewsResponse response = service.getGoldNews();

        assertTrue(response.getItems().isEmpty());
        assertEquals("none", response.getProvider());
    }
}

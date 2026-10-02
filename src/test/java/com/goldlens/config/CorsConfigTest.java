package com.goldlens.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(CorsConfigTest.TestApp.class)
@TestPropertySource(properties = "cors.allowed-origins=https://goldlens.web.app, http://localhost:3000")
class CorsConfigTest {

    @Configuration
    @EnableWebMvc
    @Import(CorsConfig.class)
    static class TestApp {
        @Bean
        Ping ping() {
            return new Ping();
        }
    }

    @RestController
    static class Ping {
        @PostMapping("/api/ping")
        String ping() {
            return "ok";
        }
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void preflightFromAllowedOriginIsAccepted() throws Exception {
        mvc.perform(options("/api/ping")
                        .header("Origin", "https://goldlens.web.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://goldlens.web.app"));
    }

    @Test
    void actualRequestFromAllowedOriginGetsTheHeader() throws Exception {
        mvc.perform(post("/api/ping").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void otherOriginsAreRejected() throws Exception {
        mvc.perform(options("/api/ping")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}

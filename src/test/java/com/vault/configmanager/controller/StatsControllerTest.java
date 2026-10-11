package com.vault.configmanager.controller;

import com.vault.configmanager.dto.response.StatsResponse;
import com.vault.configmanager.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatsControllerTest {

    private MockMvc mockMvc;
    private StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = mock(StatsService.class);
        StatsController controller = new StatsController(statsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testGetStatsEndpoint() throws Exception {
        StatsResponse stats = new StatsResponse(
                3, 2, 10, 3.33, "appA", "prod", 50, 4,
                List.of("prod", "staging"), Map.of("appA:prod", 5L), Map.of("totalStorageBytes", 1024L)
        );
        when(statsService.getSystemStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/vault/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(3))
                .andExpect(jsonPath("$.totalEnvironments").value(2))
                .andExpect(jsonPath("$.totalVersions").value(10))
                .andExpect(jsonPath("$.mostRecentApp").value("appA"))
                .andExpect(jsonPath("$.activeEnvironments[0]").value("prod"));

        verify(statsService).getSystemStats();
    }

    @Test
    void testGetDashboardEndpoint() throws Exception {
        StatsResponse stats = new StatsResponse();
        stats.setTotalApplications(5);
        when(statsService.getSystemStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/vault/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(5));

        verify(statsService).getSystemStats();
    }
}

package com.vault.configmanager.config;

import com.vault.configmanager.dto.request.ConfigRequest;
import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.repository.BinaryConfigRepository;
import com.vault.configmanager.repository.ConfigurationRepository;
import com.vault.configmanager.repository.TelemetryRepository;
import com.vault.configmanager.service.BinaryConfigService;
import com.vault.configmanager.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataBootstrapSeederTest {

    private ConfigurationRepository configurationRepository;
    private ConfigurationService configurationService;
    private DataBootstrapSeeder seeder;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(ConfigurationRepository.class);
        configurationService = mock(ConfigurationService.class);
        seeder = new DataBootstrapSeeder(configurationRepository, configurationService,
                mock(TelemetryRepository.class), mock(BinaryConfigRepository.class), mock(BinaryConfigService.class));
    }

    @Test
    void ensurePlayground_createsV1WhenMissing() {
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("playground", "development"))
                .thenReturn(Optional.empty());

        seeder.ensurePlayground();

        ArgumentCaptor<ConfigRequest> captor = ArgumentCaptor.forClass(ConfigRequest.class);
        verify(configurationService, times(1)).createConfiguration(captor.capture(), anyString(), anyString());
        ConfigRequest request = captor.getValue();
        assertEquals("playground", request.getApplicationName());
        assertEquals("development", request.getEnvironment());
        assertTrue(request.getPayload().containsKey("secure_settings"));
    }

    @Test
    void ensurePlayground_isIdempotent() {
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("playground", "development"))
                .thenReturn(Optional.of(mock(VaultDocument.class)));

        seeder.ensurePlayground();

        verifyNoInteractions(configurationService);
    }

    @Test
    void run_stillSeedsPlaygroundWhenAnEarlierStepFails() {
        when(configurationRepository.count()).thenThrow(new RuntimeException("mongo down"));
        when(configurationRepository.findTopByApplicationNameAndEnvironmentOrderByVersionDesc("playground", "development"))
                .thenReturn(Optional.empty());

        seeder.run(null);

        ArgumentCaptor<ConfigRequest> captor = ArgumentCaptor.forClass(ConfigRequest.class);
        verify(configurationService).createConfiguration(captor.capture(), anyString(), anyString());
        assertEquals("playground", captor.getValue().getApplicationName());
    }
}

package com.icthh.xm.commons.config.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.client.repository.CommonConfigRepository;
import com.icthh.xm.commons.config.domain.Configuration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@RunWith(MockitoJUnitRunner.class)
public class CommonConfigServiceUnitTest {

    @InjectMocks
    private CommonConfigService configService;

    @Mock
    private CommonConfigRepository commonConfigRepository;

    @Test
    public void getConfigurationMap() {
        Map<String, Configuration> config = Collections.singletonMap("path", new Configuration("path", "content"));
        when(commonConfigRepository.getConfig("commit"))
            .thenReturn(config);

        assertThat(configService.getConfigurationMap("commit")).isEqualTo(config);
    }

    @Test
    public void updateConfigurations() {
        Map<String, Configuration> config = Collections.singletonMap("path", new Configuration("path", "content"));
        when(commonConfigRepository.getConfig("commit")).thenReturn(config);
        List<Consumer<Configuration>> configurationListeners = new ArrayList<>();
        configurationListeners.add(mock(Consumer.class));
        configurationListeners.add(mock(Consumer.class));

        configurationListeners.forEach(configService::onConfigurationChanged);
        configService.updateConfigurations("commit", Collections.singletonList("path"));

        configurationListeners.forEach(configurationListener -> verify(configurationListener).accept(config.get("path")));
    }
}

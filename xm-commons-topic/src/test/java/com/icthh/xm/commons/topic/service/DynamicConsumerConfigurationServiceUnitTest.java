package com.icthh.xm.commons.topic.service;

import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.commons.topic.domain.ConsumerHolder;
import com.icthh.xm.commons.topic.domain.DynamicConsumer;
import com.icthh.xm.commons.topic.domain.TopicConfig;
import com.icthh.xm.commons.topic.message.MessageHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DynamicConsumerConfigurationServiceUnitTest {

    private static final String TENANT_KEY = "test";

    private DynamicConsumerConfigurationService dynamicConsumerConfigurationService;

    @Mock
    private DynamicConsumerConfiguration dynamicConsumerConfiguration;

    @Mock
    private TopicManagerService topicManagerService;

    @Mock
    private MessageHandler messageHandler;

    @Mock
    private AbstractMessageListenerContainer container;

    @Mock
    private TenantListRepository tenantListRepository;

    @Before
    public void setup() {
        dynamicConsumerConfigurationService = new DynamicConsumerConfigurationService(singletonList(dynamicConsumerConfiguration), topicManagerService, tenantListRepository);
    }

    @Test
    public void startDynamicConsumers() {
        List<DynamicConsumer> dynamicConsumers = createDynamicConsumers();
        when(dynamicConsumerConfiguration.getDynamicConsumers(eq(TENANT_KEY))).thenReturn(dynamicConsumers);

        dynamicConsumerConfigurationService.startDynamicConsumers(TENANT_KEY);

        verify(dynamicConsumerConfiguration).getDynamicConsumers(eq(TENANT_KEY));
        verify(topicManagerService, times(dynamicConsumers.size())).startNewConsumer(eq(TENANT_KEY), isAnyOfTopics(dynamicConsumers), isA(Map.class), eq(messageHandler));

        verifyNoMoreInteractions(dynamicConsumerConfiguration, topicManagerService);
    }

    @Test
    public void refreshDynamicConsumers() {
        List<DynamicConsumer> dynamicConsumers = createDynamicConsumers();
        when(dynamicConsumerConfiguration.getDynamicConsumers(eq(TENANT_KEY))).thenReturn(dynamicConsumers);
        doAnswer(invocation -> {
            TopicConfig topicConfig = invocation.getArgument(1);
            Map<String, ConsumerHolder> tenantConsumerHolders = invocation.getArgument(2);
            tenantConsumerHolders.put(topicConfig.getKey(), new ConsumerHolder(topicConfig, container));
            return null;
        }).when(topicManagerService).startNewConsumer(eq(TENANT_KEY), isAnyOfTopics(dynamicConsumers), isA(Map.class), eq(messageHandler));
        dynamicConsumerConfigurationService.startDynamicConsumers(TENANT_KEY);

        dynamicConsumerConfigurationService.refreshDynamicConsumers(TENANT_KEY);

        verify(topicManagerService, times(dynamicConsumers.size())).startNewConsumer(eq(TENANT_KEY), isAnyOfTopics(dynamicConsumers), isA(Map.class), eq(messageHandler));
        verify(dynamicConsumerConfiguration, times(2)).getDynamicConsumers(eq(TENANT_KEY));
        verify(topicManagerService, times(dynamicConsumers.size())).updateConsumer(eq(TENANT_KEY), isAnyOfTopics(dynamicConsumers), isAnyOfHolders(dynamicConsumers), any(Map.class), eq(messageHandler));

        verifyNoMoreInteractions(dynamicConsumerConfiguration, topicManagerService);
    }

    @Test
    public void refreshDynamicConsumersAll() {
        Set<String> tenants = new HashSet<>();
        tenants.add("TENANT1");
        tenants.add("TENANT2");
        when(tenantListRepository.getTenants()).thenReturn(tenants);

        dynamicConsumerConfigurationService.refreshDynamicConsumersAll();

        verify(dynamicConsumerConfiguration, times(2)).getDynamicConsumers(argThat(tenants::contains));
    }

    @Test
    public void stopDynamicConsumers() {
        List<DynamicConsumer> dynamicConsumers = createDynamicConsumers();
        when(dynamicConsumerConfiguration.getDynamicConsumers(eq(TENANT_KEY))).thenReturn(dynamicConsumers);
        doAnswer(invocation -> {
            TopicConfig topicConfig = invocation.getArgument(1);
            Map<String, ConsumerHolder> tenantConsumerHolders = invocation.getArgument(2);
            tenantConsumerHolders.put(topicConfig.getKey(), new ConsumerHolder(topicConfig, container));
            return null;
        }).when(topicManagerService).startNewConsumer(eq(TENANT_KEY), isAnyOfTopics(dynamicConsumers), isA(Map.class), eq(messageHandler));
        dynamicConsumerConfigurationService.startDynamicConsumers(TENANT_KEY);

        dynamicConsumerConfigurationService.stopDynamicConsumers(TENANT_KEY);

        verify(topicManagerService, times(dynamicConsumers.size())).startNewConsumer(eq(TENANT_KEY), isAnyOfTopics(dynamicConsumers), isA(Map.class), eq(messageHandler));
        verify(dynamicConsumerConfiguration).getDynamicConsumers(eq(TENANT_KEY));
        verify(topicManagerService, times(dynamicConsumers.size())).stopAllTenantConsumers(eq(TENANT_KEY), isA(Map.class));

        verifyNoMoreInteractions(dynamicConsumerConfiguration, topicManagerService);
    }

    private List<DynamicConsumer> createDynamicConsumers() {
        List<DynamicConsumer> dynamicConsumers = new ArrayList<>();

        DynamicConsumer dynamicConsumer = new DynamicConsumer();
        TopicConfig topicConfig = createTopicConfig("key1", "incoming-messages", "kafka-queue", 4);
        dynamicConsumer.setConfig(topicConfig);
        dynamicConsumer.setMessageHandler(messageHandler);
        dynamicConsumers.add(dynamicConsumer);

        dynamicConsumer = new DynamicConsumer();
        topicConfig = createTopicConfig("key2", "incoming-emails", "another-kafka-queue", 5);
        dynamicConsumer.setConfig(topicConfig);
        dynamicConsumer.setMessageHandler(messageHandler);
        dynamicConsumers.add(dynamicConsumer);

        return dynamicConsumers;
    }

    private TopicConfig createTopicConfig(String key, String typeKey, String topicName, int retriesCount) {
        TopicConfig topicConfig = new TopicConfig();
        topicConfig.setKey(key);
        topicConfig.setTypeKey(typeKey);
        topicConfig.setTopicName(topicName);
        topicConfig.setRetriesCount(retriesCount);

        return topicConfig;
    }

    private TopicConfig isAnyOfTopics(List<DynamicConsumer> dynamicConsumers) {
        return argThat((topicConfig) -> dynamicConsumers.stream()
            .anyMatch(it -> it.getConfig().equals(topicConfig)));
    }

    private ConsumerHolder isAnyOfHolders(List<DynamicConsumer> dynamicConsumers) {
        return argThat((consumerHolder) -> dynamicConsumers.stream()
            .anyMatch(it -> it.getConfig().equals(consumerHolder.getTopicConfig())));
    }

}

package com.icthh.xm.commons.domain.event.service;

import com.icthh.xm.commons.domain.event.config.SourceConfig;
import com.icthh.xm.commons.domain.event.config.XmDomainEventConfiguration;
import com.icthh.xm.commons.domain.event.service.dto.DomainEvent;
import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@LepService(group = "event.publisher")
public class EventPublisher {

    private final XmDomainEventConfiguration xmDomainEventConfiguration;
    private final ApplicationContext context;

    @LogicExtensionPoint(value = "Publish")
    public void publish(String source, DomainEvent event) {
        event.setSource(source);
        Transport transportToPublish = getTransportBySource(source);
        transportToPublish.send(event);
    }

    private Transport getTransportBySource(String source) {
        SourceConfig sourceConfig = xmDomainEventConfiguration.getSourceConfig(source);
        if (sourceConfig == null) {
            throw new IllegalStateException("Source config is not configured for source: " + source);
        }
        return context.getBean(sourceConfig.getTransport());
    }
}

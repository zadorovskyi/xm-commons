package com.icthh.xm.commons.lep.impl.engine;

import com.icthh.xm.commons.lep.api.LepEngine;
import com.icthh.xm.commons.lep.api.LepExecutor;
import com.icthh.xm.commons.lep.api.LepKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.icthh.xm.commons.lep.impl.engine.TenantLepEngines.TenantLepEnginesStates.ACTIVE;
import static com.icthh.xm.commons.lep.impl.engine.TenantLepEngines.TenantLepEnginesStates.DESTROYED;
import static com.icthh.xm.commons.lep.impl.engine.TenantLepEngines.TenantLepEnginesStates.DESTROYING;

@Slf4j
@RequiredArgsConstructor
class TenantLepEngines {

    enum TenantLepEnginesStates {
        ACTIVE, DESTROYING, DESTROYED;
    }

    private final AtomicInteger countOfExecutions = new AtomicInteger();
    private final AtomicReference<TenantLepEnginesStates> state = new AtomicReference<>(ACTIVE);

    private final String tenant;
    private final List<LepEngine> lepEngines;

    public LepExecutor getLepExecutor(LepKey lepKey) {
        for (LepEngine lepEngine: lepEngines) {
            if (lepEngine.isExists(lepKey)) {
                return new DefaultLepExecutor(lepEngine);
            }
        }

        return new OriginalMethodLepExecutor();
    }

    public void acquireUsage() {
        this.countOfExecutions.incrementAndGet();
    }

    public void releaseUsage() {
        int executions = this.countOfExecutions.decrementAndGet();
        destroyTenantLepEngine(executions);
    }

    public boolean isActive() {
        return state.get() == ACTIVE;
    }

    public void destroy() {
        if (!state.compareAndSet(ACTIVE, DESTROYING)) {
            return; // when already destroy in progress or destroyed
        }

        log.info("START | destroying lep engines for tenant {}", tenant);
    }

    private void destroyTenantLepEngine(int executions) {
        if (executions == 0 && state.get() == DESTROYING) {
            if (state.compareAndSet(DESTROYING, DESTROYED)) {
                lepEngines.forEach(this::destroyEngine);
                log.info("STOP | destroying lep engines for tenant {}", tenant);
            }
        }
    }

    private void destroyEngine(LepEngine engine) {
        try {
            engine.destroy();
        } catch (Throwable e) {
            log.error("Error during destroy engine", e);
        }
    }
}

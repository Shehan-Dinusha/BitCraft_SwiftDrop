package com.swiftdrop.analytics.service;

import com.swiftdrop.analytics.domain.projection.OrderProjectionReducer;
import com.swiftdrop.analytics.domain.repository.AnalyticsProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AnalyticsProjectionService {
    private final AnalyticsProjectionRepository repository;
    private final OrderProjectionReducer reducer;

    public AnalyticsProjectionService(AnalyticsProjectionRepository repository, OrderProjectionReducer reducer) {
        this.repository = repository;
        this.reducer = reducer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reconcile(UUID orderId) {
        repository.lockWrites();
        var events = repository.events(orderId);
        repository.replace(repository.existing(orderId), reducer.reduce(orderId, events), events);
    }

    @Transactional
    public int catchUpBatch() {
        repository.lockWrites();
        var orders = repository.pendingOrders();
        for (UUID order : orders) reconcile(order);
        return orders.size();
    }
}

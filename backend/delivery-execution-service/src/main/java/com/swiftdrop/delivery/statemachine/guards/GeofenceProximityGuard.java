package com.swiftdrop.delivery.statemachine.guards;

import org.springframework.stereotype.Component;

@Component
public class GeofenceProximityGuard {
    public boolean isSatisfied() { return true; }
}

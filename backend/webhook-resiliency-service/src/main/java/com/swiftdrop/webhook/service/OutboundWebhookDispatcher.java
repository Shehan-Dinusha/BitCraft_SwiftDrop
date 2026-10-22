package com.swiftdrop.webhook.service;

import org.springframework.stereotype.Service;

@Service
public class OutboundWebhookDispatcher {
    public String status() { return "ready"; }
}

package com.swiftdrop.order.service;

import org.springframework.stereotype.Service;

@Service
public class OrderLifecycleService {
    public String status() { return "ready"; }
}

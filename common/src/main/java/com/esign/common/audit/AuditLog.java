package com.esign.common.audit;

import com.esign.common.time.IstTimestamps;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class AuditLog {
    private final ConcurrentLinkedDeque<AuditEvent> events = new ConcurrentLinkedDeque<>();

    public void add(String action, String txn, String detail) {
        events.addFirst(new AuditEvent(IstTimestamps.now(), action, txn == null ? "" : txn, detail == null ? "" : detail));
        while (events.size() > 300) {
            events.removeLast();
        }
    }

    public List<AuditEvent> list() {
        return new ArrayList<>(events);
    }
}

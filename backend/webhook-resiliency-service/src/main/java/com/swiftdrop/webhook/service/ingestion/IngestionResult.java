package com.swiftdrop.webhook.service.ingestion;

/**
 * Outcome of ingesting one broker message.
 *
 * @param deliveriesCreated     jobs created for this event
 * @param releasedParkedEvents  earlier events of the same order resolved because this {@code order.created} arrived
 * @param releasedDeliveries    jobs created for those released events
 */
public record IngestionResult(Outcome outcome, int deliveriesCreated, int releasedParkedEvents, int releasedDeliveries) {

    public enum Outcome {
        /** Event id already ingested: nothing was written. */
        DUPLICATE,
        RESOLVED,
        AWAITING_MERCHANT,
        NO_MERCHANT
    }

    static IngestionResult duplicate() {
        return new IngestionResult(Outcome.DUPLICATE, 0, 0, 0);
    }
}

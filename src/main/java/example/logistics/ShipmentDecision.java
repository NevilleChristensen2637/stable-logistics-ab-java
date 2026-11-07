package example.logistics;

import java.io.IOException;

public final class ShipmentDecision {
    public record Shipment(String shipmentId, String userId, String proofOfDelivery, String exceptionCode) {}
    public record Decision(String shipmentId, String bucket, String action) {}

    private final InfraiHttpClient infrai;
    private final String flagKey;

    public ShipmentDecision(InfraiHttpClient infrai, String flagKey) {
        this.infrai = infrai;
        this.flagKey = flagKey;
    }

    public Decision decide(Shipment shipment) throws IOException, InterruptedException {
        infrai.getUser(shipment.userId());
        String value = infrai.getFlagValue(flagKey, shipment.userId());
        String bucket = value.contains("treatment") ? "treatment" : "control";
        String action = shipment.exceptionCode().isBlank() ? "release" : "review-exception";
        return new Decision(shipment.shipmentId(), bucket, action);
    }

    public static Decision localDecision(Shipment shipment, String flagValue) {
        String bucket = flagValue.contains("treatment") ? "treatment" : "control";
        String action = shipment.exceptionCode().isBlank() ? "release" : "review-exception";
        return new Decision(shipment.shipmentId(), bucket, action);
    }

    public void captureException(String payload) throws IOException, InterruptedException {
        infrai.captureException(payload);
    }
}

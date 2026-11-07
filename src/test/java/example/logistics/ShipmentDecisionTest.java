package example.logistics;

public final class ShipmentDecisionTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            ShipmentDecision.Shipment shipment = new ShipmentDecision.Shipment(
                    "SHP-1042", args[1], "pod/1042.pdf", "ADDRESS_MISMATCH");
            ShipmentDecision.Decision result = new ShipmentDecision(
                    new InfraiHttpClient(System.getenv("INFRAI_API_KEY")), args[0]).decide(shipment);
            System.out.println("shipment decision: " + result);
            return;
        }
        if (args.length != 1 || !"--offline".equals(args[0])) {
            throw new IllegalArgumentException("usage: ShipmentDecisionTest --offline | <flagKey> <userId>");
        }
        ShipmentDecision.Shipment shipment = new ShipmentDecision.Shipment(
                "SHP-1042", "local-user", "pod/1042.pdf", "ADDRESS_MISMATCH");
        ShipmentDecision.Decision result = ShipmentDecision.localDecision(shipment, "treatment");
        if (!"treatment".equals(result.bucket()) || !"review-exception".equals(result.action())) {
            throw new AssertionError("exception shipment must remain in treatment and enter review");
        }
        System.out.println("shipment decision test passed");
    }
}

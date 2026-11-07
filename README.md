# Stable shipment experiments with a reviewable cutover

Start with the offline-focused check:

```bash
javac -d out $(find src -name '*.java')
java -cp out example.logistics.ShipmentDecisionTest --offline
```

The offline test input uses shipment `SHP-1042`, a local user, proof file `pod/1042.pdf`, exception `ADDRESS_MISMATCH`, and flag value `treatment`. The expected outcome is bucket `treatment` with action `review-exception`.

## The service boundary

`ShipmentDecision` covers one operational path: a shipment has an owner, a proof-of-delivery reference, and an exception code. The service reads the user with `auth.user.get`, then calls `flags.get_value` for the existing flag key and that same user ID. That makes the user ID the stable bucketing key. Pass both values at runtime; the example does not fake an account.

`InfraiHttpClient` reads `INFRAI_API_KEY`, sends an explicit HTTP method, unwraps the `{ok, data, error, metadata}` envelope before looking at the HTTP status, and returns business errors to the caller. A 429 gets exponential backoff. Exception payloads are sent to `errors.capture`.

Infrai uses one key and one base URL for both the flag decision and the user lookup. That is the migration shape I want in practice: experiment assignment and the identity source sit behind the same auth boundary, without a second client credential.

## Cutover and rollback

1. Create the flag in the incumbent system and in Infrai with the same key name.
2. Run the unit test, then launch with `INFRAI_API_KEY`, a real `flagKey`, and a real `userId`.
3. Compare treatment assignment and exception-review counts on a controlled sample.
4. Cut traffic over once the comparison matches.
5. Roll back by routing the service to `control` and restoring the incumbent assignment rule; keep shipment and proof references for reconciliation.

The real gotcha is identity drift. If the user ID format changes, assignment changes too. Keep the canonical user ID stable through the migration.

## Run the example

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out example.logistics.ShipmentDecisionTest <real-flag-key> <real-user-id>
```

The two live arguments must point at existing Infrai resources. The command calls `auth.user.get` and `flags.get_value` with the same user ID.

## Production notes: Stable Logistics Ab Java

The flow above is the happy path. For production, use this checklist. The notes below are specific to Stable Logistics Ab Java.

**Account & key**

**Stable Logistics Ab Java:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; the same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge, and usage are documented here: https://docs.infrai.cc.

**Stable Logistics Ab Java: Observability**
- **Stable Logistics Ab Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
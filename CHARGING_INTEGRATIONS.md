# Unified EV charging in DriveMateAI — integration contract (October 2026)

## Networks in scope
- HPCL — HP eCharge
- IndianOil — IndianOil e-Charge
- BPCL — eDrive / HelloBPCL
- Tata Power — EZ Charge
- Jio-bp — pulse

Also extendable to any other operator supporting a contracted feed. The goal is **one searchable list** regardless of provider. The current guided Android V1 does **not** fetch a live network feed; the registry screen reports each unconnected provider instead of generating fictitious availability.

## Why phone permissions are not sufficient

Granting Android location/microphone permissions or installing the provider apps does not give a third-party Android app permission to read private backend charging records. Read-only operator API access must be obtained separately and handled by a backend using an approved contract / OCPI exchange where supported. Do not automate login by stealing sessions, reverse-engineering private APIs, capturing OTPs, or intercepting payment flows.

Some station discovery can use Google Places API (New) with billing and a properly secured API configuration. Its EV `connectorAggregation` object can provide counts by connector type/power, `availableCount`, `outOfServiceCount`, and `availabilityLastUpdateTime` **where supported**. It does not identify the physical Gun 1 or Gun 2. Do not map aggregated counts to a gun.

## Operator feed data required

The connector-level adapter interface should return, per charging station:
- provider and unique station ID; exact lat/lon and access instructions
- individual EVSE ID and physical connector (gun) ID; compatible CCS2 and rated power kW
- real-time state: available / busy / out of service / unknown
- operator-reported update timestamp, fetched timestamp and source identity
- last confirmed successful session end if **the operator actually provides it**
- operator tariff and amenities only if verified, with source/update timestamp

Never invent any field or infer last successful charging from a station listing. Provider responses may be stale despite calling them 'real-time'. If a source fails, show UNVERIFIED rather than AVAILABLE. Five minutes is a conservative *initial freshness threshold*, not an operational uptime guarantee.

## Shared station model

`ChargingProviderRegistry.kt` defines operator identities, status and evidence types, staleness checks, and a pluggable source contract. Only `OPERATOR_CONNECTOR` evidence with individual connector ID + a recent verified source timestamp can be labelled `REPORTED_AVAILABLE`; the UI must still avoid claiming it is guaranteed to function.

Avoid ranking a station as the best choice based solely on straight-line distance or station rating. After obtaining authorized live operator data, compare:
1. available and compatible DC CCS2 connector with fresh data, battery safety margin
2. legal forward-driving route, same carriageway, service-road access and actual detour
3. recent session health and backup charger if data is offered
4. nearby restaurant rating >=4.2 with substantial review count, hours and washrooms
5. price (INR/kWh), power (kW), driving and charging time

If no station meets requirements, explain the constraint instead of substituting stale or inaccessible chargers.

## Implementation milestones

**Completed on this V1 branch:**
- Five provider identities and common connector freshness/status model
- Read-only per-provider integration boundary
- Centralized network connection status screen
- Maps-based discovery handoff, Hinglish voice input and manual vehicle battery context

**Still required before the user-requested live unified result is possible:**
- API credentials, commercial/read access, documented endpoints or OCPI partner arrangements with the five networks
- Tested backend proxy and secure server-side secrets; **no API tokens in public GitHub or APK**
- Authorized operator adapters, station deduplication, connector-level status and freshness mapping
- Google Places (or another licensed listing source) integration, plus route and restaurant search with real road detour
- EV charging and navigation QA on a stationary phone / passenger-led test

### Official evidence / references
- HP eCharge App: https://play.google.com/store/apps/details?id=com.hpcl.ev
- IndianOil e-Charge App: https://play.google.com/store/apps/details?id=com.iocl.ev
- BPCL eDrive: https://www.bharatpetroleum.in/our-businesses/fuels-and-services/edrive
- Tata Power EZ Charge: https://www.tatapower.com/ezcharge
- Jio-bp pulse: https://www.jiobp.com/products-and-services/EV-charging
- Google Places charging data fields: https://developers.google.com/maps/documentation/places/web-service/reference/rest/v1/places

## Data protection
Payment applications, cards, wallets, UPI, bank accounts, PINs, passwords and OTPs remain outside DriveMateAI's permission scope. A future provider read-only token, if obtainable, belongs in a secured backend, never in client source code.

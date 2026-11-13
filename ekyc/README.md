# eKYC

eKYC provider used by the ESP. Port 8083. The ASP does not call this service.

## Demo signer

Seeded on startup when `esign.seed-demo-user` is true.

| Field | Value |
|---|---|
| Signer id | `demo.user@username.ESP001` |
| PIN | `123456` |
| Mobile alias | `9876543210@Mobile.ESP001` |
| PAN alias | `ABCDE1234F@PAN.ESP001` |
| eKYC type | `OFFLINE_AADHAAR` |

`esign.expose-demo-otp` includes the OTP in `POST /api/v1/otp`. Disable it outside a local demo.

## Accounts

`POST /api/v1/accounts` with JSON. `ekycType` is one of `OFFLINE_AADHAAR`, `BANK`, `ORGANISATIONAL`, `PAN`, `FOREIGN_NATIONAL`.

Personal types (`OFFLINE_AADHAAR`, `BANK`, `PAN`, `FOREIGN_NATIONAL`) keep mobile and PAN unique. `BANK` requires a PAN and a `bankTxn` already accepted by `POST /api/v1/bank-kyc`. `ORGANISATIONAL` requires organisation id, name, signatory name, and signatory certificate id. `FOREIGN_NATIONAL` requires a country other than `IN`.

## BankKYC and OrgKYC

Both are signed XML, version `1.0`. Bank IFSC is optional. `OrgKYC` exists so error codes 501–521 have a request to validate; API 3.3 removed the previous organisational XML and kept the codes.

`physicalVerification` on `OrgKYC` must include the person's name from `KYCInfo`.

## Subscriber portal

Login with signer id and PIN (`POST /api/v1/subscriber/login`). Send `Authorization: Bearer <token>` to change the PIN, read history, or update email and address. Name changes are not accepted on this route; they belong to a new KYC verification.

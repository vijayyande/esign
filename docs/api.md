# HTTP API

XML protocol calls use `Content-Type: application/xml` and return HTTP 200. Business failures are in the XML `status` and `error` attributes. JSON calls return an error body and an HTTP status from the error catalogue.

Base URLs below assume the default ports.

## ASP — http://localhost:8081

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Upload and sign page |
| POST | `/api/v1/documents` | Multipart `file` and `docInfo` (max 50 characters) |
| GET | `/api/v1/documents` | Stored documents |
| GET | `/api/v1/documents/{id}/content` | Original upload |
| GET | `/api/v1/documents/{id}/prepared` | PDF hashed for CMS signature types. This is the `docUrl` the signer opens |
| GET | `/api/v1/documents/{id}/signed.pdf` | PDF with the CMS signature embedded |
| GET | `/api/v1/documents/{id}/signature` | Signature, certificate, `verified`, and `signedPdf` |
| POST | `/api/v1/esign/requests` | JSON body below. Returns `authenticationUrl` |
| POST | `/api/v1/esign/callback` | ESP `responseUrl`. Body is `EsignResp` |
| POST | `/api/v1/esign/return` | Form field `txnref` after the ESP redirects the browser |
| GET | `/api/v1/esign/transactions/{txn}` | Last known state |
| POST | `/api/v1/esign/transactions/{txn}/status` | Poll the ESP |
| GET | `/api/v1/meta` | Component name and spec version |
| GET | `/api/v1/meta/error-codes` | Section 5 catalogue |
| GET | `/api/v1/audit` | Recent audit events |

Sign request:

```json
{
  "documentIds": ["..."],
  "signerId": "demo.user@username.ESP001",
  "signingAlgorithm": "RSA",
  "responseSigType": "PKCS7pdf",
  "maxWaitPeriod": 60
}
```

`signingAlgorithm` is `RSA` or `ECDSA`. `responseSigType` is `raw`, `PKCS7`, `PKCS7pdf`, or `PKCS7complete`. Omitted, the ASP uses `PKCS7pdf`. CMS types hash the PDF byte range and produce `/signed.pdf`. Optional `signaturePage`, `signatureX`, `signatureY`, `signatureWidth`, and `signatureHeight` place the visible signature. Those coordinates are PDF points from the top-left of the rendered page. `raw` hashes the original file and does not embed a PDF signature.

`GET /api/v1/documents/{id}/pages` returns each page's displayed size. `GET /api/v1/documents/{id}/preview?page=1` returns a PNG of that page. The field-by-field manual is [guide.md](guide.md).

## ESP — http://localhost:8082

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Operator console: overview, operations, logs, and service |
| GET | `/api/v1/esign/transactions` | Signing requests held in memory |
| GET | `/api/v1/esign/transactions/{txn}` | One request, its documents, and callback state |
| GET | `/api/v1/meta/service` | ESP id and the eKYC, OTP, and CA URLs |
| POST | `/api/v1/esign` | `Esign` request. Ack is `EsignResp` with `status="2"` |
| POST | `/api/v1/esign/status` | `EsignStatus` request. Same response shape, including pending |
| GET | `/api/v1/esign/auth?txnref=` | Signer page |
| POST | `/api/v1/esign/auth` | Form: `txnref`, `signerId`, `pin`, `otp`, `doc` (repeat for each document to sign) |
| POST | `/api/v1/esign/auth/otp` | JSON `{ "txnref", "signerId" }`. Demo mode includes `demoOtp` |

Allowed ASP id defaults to `ASP001`. Signer ids must end in `.ESP001`.

`Esign` attributes: `ver` (`3.3`), `signerid` (`id@username|Mobile|PAN.ESP001`), `ts` (ISO-8601 with offset, within 30 minutes), `txn` (unique per ASP for the day, 1–50 of `A-Za-z0-9._:-`), `maxWaitPeriod` (1 to 10080 minutes), `aspId`, `responseUrl`, optional `redirectUrl`, `signingAlgorithm`.

Each `InputHash` has `id` starting at 1, `hashAlgorithm="SHA256"`, 64 hex characters, `docInfo`, an `http` or `https` `docUrl`, and `responseSigType`.

## eKYC — http://localhost:8083

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/ekyc` | `eKycReq`. Success `eKycResp` does not leave this service toward the ASP |
| POST | `/api/v1/otp` | `{ "signerId", "txn" }` issues an SMS OTP. Demo responses include `demoOtp` |
| POST | `/api/v1/bank-kyc` | `BankKYC` version 1.0 |
| POST | `/api/v1/org-kyc` | `OrgKYC` version 1.0 |
| POST | `/api/v1/accounts` | Create a signer. See the eKYC readme |
| POST | `/api/v1/subscriber/login` | `{ "signerId", "pin" }` returns a bearer token |
| POST | `/api/v1/subscriber/pin` | Bearer token. `{ "currentPin", "newPin" }` |
| GET | `/api/v1/subscriber/profile` | Bearer token |
| POST | `/api/v1/subscriber/profile` | Bearer token. Email, address, state, postal code |
| GET | `/api/v1/subscriber/history` | Signing history for the logged-in signer |

`pinhash` on `eKycReq` is lowercase hex `SHA-256(txn + SHA-256(pin))`. `SecondFactor` is the base64 of `SecondFactorRequest`. The enabled type is `SMSOTP`, and the element value is lowercase hex `SHA-256(otp)`. Consent text is exactly `I hereby authorize this KYC Information authentication.` Five failed attempts lock that signer and transaction.

## CA — http://localhost:8084

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/ca/certificate` | PEM of the reference CA |
| POST | `/api/v1/certificates` | Issue a signer certificate and sign hashes |

```json
{
  "subjectName": "Demo Signer",
  "signingAlgorithm": "RSA",
  "documents": [
    {"id": "1", "hashHex": "<64 hex chars>", "responseSigType": "raw"}
  ]
}
```

The response carries `certificateBase64` (DER), `certificatePem`, `caCertificateBase64`, and `signatures`. A bad hash or signature type is reported on that item (`error` `201` or `202`) instead of failing the other documents.

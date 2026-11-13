# Error codes

Source: CCA eSign API Specifications 3.3, section 5. The enum is `com.esign.common.error.EsignErrorCode`. Running apps return the same list from `GET /api/v1/meta/error-codes`.

XML responses use HTTP 200. The numeric code is the `error` attribute on `EsignResp`, or `errorCode` on bank and organisation responses. JSON responses use the HTTP status in the last column.

## 5.1 eSign response

| Code | Message | HTTP |
|---|---|---|
| 101 | Invalid Request Format | 400 |
| 102 | Invalid Signer ID | 400 |
| 103 | Invalid Version | 400 |
| 104 | XML Signature validation failed | 400 |
| 105 | Invalid transaction ID | 400 |
| 106 | Invalid ASP ID | 400 |
| 107 | Invalid Digital Signature | 400 |
| 108 | Minimum one document is required | 400 |
| 109 | Request exceeds Maximum number of documents allowed | 400 |
| 110 | Invalid Timestamp | 400 |
| 111 | Invalid Maximum Wait Period | 400 |
| 112 | Duplicate Transaction ID | 409 |
| 113 | User Timeout. Maximum Wait Time expired. | 408 |
| 114 | Authentication failed. User credentials invalid. | 401 |
| 199 | Unknown error / Custom error from ESP | 500 |

`107` means the XML-DSig `Signature` element is missing. `104` means a signature is present and did not validate. `111` is used when `maxWaitPeriod` is not an integer from 1 to 10080. The specification default is 1440 minutes; this service also accepts a wait up to seven days.

## 5.2 eSign document

| Code | Message | HTTP |
|---|---|---|
| 201 | Invalid Document Hash | 400 |
| 202 | Invalid response signature type | 400 |
| 203 | Invalid document URL | 400 |
| 204 | Invalid document information | 400 |
| 205 | Invalid hash algorithm | 400 |
| 206 | Document cancelled by user | 400 |
| 299 | Unknown error / Custom error from ESP | 500 |

Hash must be 64 hex characters. `hashAlgorithm` must be `SHA256`. `docInfo` is required and at most 50 characters. `docUrl` must be `http` or `https`. Unchecked documents on the ESP page come back as `206`.

## 5.3 eSign status

| Code | Message | HTTP |
|---|---|---|
| 301 | Invalid Request Format | 400 |
| 302 | Transaction number not found | 404 |
| 303 | Invalid Version | 400 |
| 399 | Unknown error / Custom error from ESP | 500 |

A bad timestamp, ASP id, or XML signature on `EsignStatus` is reported as `301`. The status catalogue has no separate signature code.

## 5.4 Bank KYC

| Code | Message | HTTP |
|---|---|---|
| 401 | Invalid Bank KYC Request XML | 400 |
| 402 | Invalid Bank Digital Signature or Bank is not registered entity in the eKYC application | 400 |
| 403 | Invalid Version Number | 400 |
| 404 | Invalid Transaction Id (Either empty or size not acceptable by the application) | 400 |
| 405 | Duplicate Transaction Id | 409 |
| 406 | Invalid Bank IFSC Code (Empty or invalid format) | 400 |
| 407 | Invalid Bank Name (Not a registered bank or the bank name is not as per the RBI specifications) | 400 |
| 408 | KYC Information Missing | 400 |
| 409 | Invalid Common Name | 400 |
| 410 | Invalid Mobile Number | 400 |
| 411 | Invalid Email Id | 400 |
| 412 | Invalid Address | 400 |
| 413 | Invalid State/Province | 400 |
| 414 | Invalid Country | 400 |
| 415 | Invalid Postal Code | 400 |
| 416 | Invalid Date of Birth | 400 |
| 417 | Invalid PAN | 400 |
| 418 | Invalid Aadhaar | 400 |
| 419 | Invalid Photo Format provided | 400 |
| 420 | Invalid Photo (Either Missing or photo not as per the defined format in the request) | 400 |
| 499 | Unknown error / Custom error from eKYC Service Provider | 500 |

Version must be `1.0`. IFSC is optional in API 3.3; when present it must be 11 letters or digits, otherwise `406`. Gender is mandatory in the bank KYC table and a missing or invalid gender is `408`. An account number that is not 6–18 digits is `401`.

## 5.5 Organisation KYC

| Code | Message | HTTP |
|---|---|---|
| 501 | Invalid Org KYC Request XML | 400 |
| 502 | Invalid Organizational Digital Signature or Org is not registered entity in the eKYC application | 400 |
| 503 | Invalid Version Number | 400 |
| 504 | Invalid Transaction Id (Either empty or size not acceptable by the application) | 400 |
| 505 | Duplicate Transaction Id | 409 |
| 506 | Invalid KYC Org Id (either NULL or the Id is not valid in the application) | 400 |
| 507 | Invalid Organization Name | 400 |
| 508 | Invalid Organization Signatory Name | 400 |
| 509 | Invalid Organizational Signatory Certificate Identifier | 400 |
| 510 | KYC Information is Missing | 400 |
| 511 | Invalid Employee Id | 400 |
| 512 | Invalid Name | 400 |
| 513 | Invalid Mobile Number | 400 |
| 514 | Invalid Email Id | 400 |
| 515 | Invalid Date of Birth | 400 |
| 516 | Invalid Gender | 400 |
| 517 | Invalid PAN | 400 |
| 518 | Invalid Aadhaar | 400 |
| 519 | Invalid Photo Format | 400 |
| 520 | Invalid Photo (Either Missing or photo not as per the defined format in the request) | 400 |
| 521 | Invalid Physical Verification Tag or The Name mentioned in this text and the name provided in the tag doesn't match | 400 |
| 599 | Unknown error / Custom error from eKYC Service Provider | 500 |

API 3.3 deleted the earlier organisational option-1 XML and left this error list in place. `OrgKYC` is the request this project validates against those codes. `physicalVerification` must contain the person's name. Either PAN or the last four digits of Aadhaar is required; a bad PAN is `517` and a missing both is also `517`.

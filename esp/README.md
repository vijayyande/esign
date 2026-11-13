# ESP

eSign Service Provider for API 3.3. Port 8082.

Accepts a signed `Esign` request from an allowed ASP, authenticates the signer, and returns `EsignResp`. The operator console is http://localhost:8082 and has Overview, Operations, Logs, and Service. The signer page is `GET /api/v1/esign/auth?txnref=`.

`esign.allowed-asp-ids` defaults to `ASP001`. `esign.esp-id` defaults to `ESP001` and must match the signer id suffix. `esign.ekyc-url`, `esign.otp-url`, and `esign.ca-url` point at the other two apps.

Pending requests expire after `maxWaitPeriod` minutes. A scheduler marks them with error `113` and callbacks the ASP.

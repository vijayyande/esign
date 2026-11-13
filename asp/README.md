# ASP

Application Service Provider for eSign API 3.3. Port 8081.

The browser page at `/` uploads a document and starts a signing request. The ESP must be running at the URL in `esign.esp-sign-url`.

Configuration is in `src/main/resources/application.yml`:

- `esign.asp-id` — sent as `aspId` (default `ASP001`)
- `esign.public-base-url` — used to build `responseUrl`, `redirectUrl`, and `docUrl`
- `esign.data-dir` — uploaded bytes (`data/asp`)

For `PKCS7`, `PKCS7pdf`, and `PKCS7complete` the service builds a PDF, sends the SHA-256 of the PDF byte range, and writes the returned CMS signature into that PDF.

- `/api/v1/documents/{id}/content` — original upload
- `/api/v1/documents/{id}/prepared` — PDF that was hashed (`docUrl`)
- `/api/v1/documents/{id}/signed.pdf` — signed PDF
- `/api/v1/documents/{id}/signature` — signature bytes, `verified`, and the signed-PDF path

`raw` remains a detached signature over the original file. The upload page defaults to `PKCS7pdf`.

# CA

Certifying Authority used by the ESP. Port 8084.

`POST /api/v1/certificates` creates an end-entity key pair, issues a certificate under `data/ca/ca.p12`, signs each SHA-256 document hash, and drops the end-entity private key. Certificate life defaults to 24 hours (`esign.certificate-validity-hours`).

The keystore password defaults to `changeit`. Change `esign.keystore-password` before using the directory anywhere except this machine.

`raw` signatures are PKCS#1 DigestInfo (RSA) or ECDSA over the 32-byte hash. `PKCS7` is a detached CMS signature containing only the signer certificate. `PKCS7pdf` and `PKCS7complete` add the CA certificate to that set. This service does not embed OCSP or CRL data.

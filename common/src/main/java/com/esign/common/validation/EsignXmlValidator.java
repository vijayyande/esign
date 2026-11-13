package com.esign.common.validation;

import com.esign.common.SpecTexts;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.time.IstTimestamps;
import com.esign.common.xml.EsignRequest;
import com.esign.common.xml.EsignStatusRequest;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class EsignXmlValidator {
    private static final Pattern TXN = Pattern.compile("^[A-Za-z0-9._:-]{1,50}$");
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Set<String> ALGORITHMS = Set.of("RSA", "ECDSA");
    private static final Set<String> SIG_TYPES = Set.of("raw", "PKCS7", "PKCS7pdf", "PKCS7complete");
    private static final int MAX_WAIT_MINUTES = 7 * 24 * 60;

    private final Clock clock;

    public EsignXmlValidator(Clock clock) {
        this.clock = clock;
    }

    public void validateRequest(EsignRequest request, String expectedAspId) {
        if (request == null) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT);
        }
        if (!SpecTexts.ESIGN_VERSION.equals(request.ver)) {
            throw new ApiException(EsignErrorCode.INVALID_VERSION);
        }
        if (request.signerid != null && !request.signerid.isBlank() && SignerIds.parse(request.signerid) == null) {
            throw new ApiException(EsignErrorCode.INVALID_SIGNER_ID);
        }
        requireTxn(request.txn, EsignErrorCode.INVALID_TRANSACTION_ID);
        IstTimestamps.requireFresh(request.ts, Instant.now(clock), EsignErrorCode.INVALID_TIMESTAMP);
        requireWait(request.maxWaitPeriod);
        if (request.aspId == null || request.aspId.isBlank()
                || (expectedAspId != null && !expectedAspId.equals(request.aspId))) {
            throw new ApiException(EsignErrorCode.INVALID_ASP_ID);
        }
        requireHttpUrl(request.responseUrl, EsignErrorCode.INVALID_REQUEST_FORMAT);
        if (request.redirectUrl != null && !request.redirectUrl.isBlank()) {
            requireHttpUrl(request.redirectUrl, EsignErrorCode.INVALID_REQUEST_FORMAT);
        }
        if (!ALGORITHMS.contains(request.signingAlgorithm)) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT);
        }
        if (request.docs == null || request.docs.inputHashes == null || request.docs.inputHashes.isEmpty()) {
            throw new ApiException(EsignErrorCode.DOCUMENT_REQUIRED);
        }
        List<EsignRequest.InputHash> hashes = request.docs.inputHashes;
        if (hashes.size() > SpecTexts.MAX_DOCUMENTS) {
            throw new ApiException(EsignErrorCode.TOO_MANY_DOCUMENTS);
        }
        for (int index = 0; index < hashes.size(); index++) {
            validateHash(hashes.get(index), index + 1);
        }
    }

    public void validateStatus(EsignStatusRequest request, String expectedAspId) {
        if (request == null) {
            throw new ApiException(EsignErrorCode.STATUS_INVALID_FORMAT);
        }
        if (!SpecTexts.ESIGN_VERSION.equals(request.ver)) {
            throw new ApiException(EsignErrorCode.STATUS_INVALID_VERSION);
        }
        if (request.txn == null || request.txn.isBlank()) {
            throw new ApiException(EsignErrorCode.STATUS_INVALID_FORMAT);
        }
        if (!TXN.matcher(request.txn).matches()) {
            throw new ApiException(EsignErrorCode.STATUS_INVALID_FORMAT);
        }
        IstTimestamps.requireFresh(request.ts, Instant.now(clock), EsignErrorCode.STATUS_INVALID_FORMAT);
        if (request.aspId == null || request.aspId.isBlank()
                || (expectedAspId != null && !expectedAspId.equals(request.aspId))) {
            throw new ApiException(EsignErrorCode.STATUS_INVALID_FORMAT);
        }
    }

    private static void validateHash(EsignRequest.InputHash hash, int expectedId) {
        if (hash == null || hash.id == null || !hash.id.equals(Integer.toString(expectedId))) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT);
        }
        if (!SpecTexts.HASH_ALGORITHM.equals(hash.hashAlgorithm)) {
            throw new ApiException(EsignErrorCode.INVALID_HASH_ALGORITHM);
        }
        if (hash.value == null || !SHA256_HEX.matcher(hash.value.trim()).matches()) {
            throw new ApiException(EsignErrorCode.INVALID_DOCUMENT_HASH);
        }
        if (hash.docInfo == null || hash.docInfo.isBlank() || hash.docInfo.length() > SpecTexts.MAX_DOC_INFO_LENGTH) {
            throw new ApiException(EsignErrorCode.INVALID_DOCUMENT_INFO);
        }
        requireHttpUrl(hash.docUrl, EsignErrorCode.INVALID_DOCUMENT_URL);
        if (!SIG_TYPES.contains(hash.responseSigType)) {
            throw new ApiException(EsignErrorCode.INVALID_RESPONSE_SIG_TYPE);
        }
    }

    private static void requireTxn(String txn, EsignErrorCode error) {
        if (txn == null || !TXN.matcher(txn).matches()) {
            throw new ApiException(error);
        }
    }

    private static void requireWait(String value) {
        try {
            int minutes = Integer.parseInt(value);
            if (minutes < 1 || minutes > MAX_WAIT_MINUTES) {
                throw new ApiException(EsignErrorCode.INVALID_MAX_WAIT);
            }
        } catch (NumberFormatException exception) {
            throw new ApiException(EsignErrorCode.INVALID_MAX_WAIT);
        }
    }

    private static void requireHttpUrl(String value, EsignErrorCode error) {
        if (value == null || value.isBlank()) {
            throw new ApiException(error);
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                throw new ApiException(error);
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(error);
        }
    }
}

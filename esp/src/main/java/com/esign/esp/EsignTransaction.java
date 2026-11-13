package com.esign.esp;

import com.esign.common.time.IstTimestamps;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EsignTransaction {
    public final String txn;
    public final String resCode;
    public final String aspId;
    public final String requestSignerId;
    public final String responseUrl;
    public final String redirectUrl;
    public final String signingAlgorithm;
    public final Instant deadline;
    public final String createdAt;
    public final List<DocInput> documents;
    public String status = "2";
    public String error = "";
    public String certificateBase64 = "";
    public final List<DocOutput> results = new ArrayList<>();
    public boolean callbackDelivered;
    public String callbackError = "";

    public EsignTransaction(String txn, String resCode, String aspId, String requestSignerId, String responseUrl,
                            String redirectUrl, String signingAlgorithm, Instant deadline, List<DocInput> documents) {
        this.txn = txn;
        this.resCode = resCode;
        this.aspId = aspId;
        this.requestSignerId = requestSignerId;
        this.responseUrl = responseUrl;
        this.redirectUrl = redirectUrl;
        this.signingAlgorithm = signingAlgorithm;
        this.deadline = deadline;
        this.createdAt = IstTimestamps.now();
        this.documents = List.copyOf(documents);
    }

    public record DocInput(String id, String hashHex, String docInfo, String docUrl, String responseSigType) {
    }

    public record DocOutput(String id, String signatureBase64, String error) {
    }
}

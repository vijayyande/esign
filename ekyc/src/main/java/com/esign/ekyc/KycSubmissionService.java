package com.esign.ekyc;

import com.esign.common.SpecTexts;
import com.esign.common.audit.AuditLog;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.time.IstTimestamps;
import com.esign.common.xml.BankKycRequest;
import com.esign.common.xml.BankKycResponse;
import com.esign.common.xml.OrgKycRequest;
import com.esign.common.xml.OrgKycResponse;
import com.esign.common.xml.XmlCodec;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KycSubmissionService {
    private final XmlSigner xmlSigner;
    private final AuditLog auditLog;
    private final ConcurrentHashMap<String, LocalDate> bankTxns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDate> orgTxns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BankKycRequest> bankRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OrgKycRequest> orgRecords = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public KycSubmissionService(XmlSigner xmlSigner, AuditLog auditLog) {
        this.xmlSigner = xmlSigner;
        this.auditLog = auditLog;
    }

    public String submitBank(String xml) {
        XmlCodec.parse(xml, EsignErrorCode.BANK_INVALID_XML);
        xmlSigner.verify(xml, EsignErrorCode.BANK_INVALID_SIGNATURE, EsignErrorCode.BANK_INVALID_SIGNATURE);
        BankKycRequest request = XmlCodec.unmarshal(xml, BankKycRequest.class, EsignErrorCode.BANK_INVALID_XML);
        BankKycValidator.validate(request);
        reserve(bankTxns, request.txn, EsignErrorCode.BANK_DUPLICATE_TXN);
        bankRecords.put(request.txn, request);
        auditLog.add("BANK_KYC", request.txn, request.bankName);
        return bankResponse(request.txn, "1", null);
    }

    public String bankFailure(String xml, ApiException exception) {
        return bankResponse(XmlCodec.attribute(xml, "txn"), "0", exception);
    }

    public String submitOrg(String xml) {
        XmlCodec.parse(xml, EsignErrorCode.ORG_INVALID_XML);
        xmlSigner.verify(xml, EsignErrorCode.ORG_INVALID_SIGNATURE, EsignErrorCode.ORG_INVALID_SIGNATURE);
        OrgKycRequest request = XmlCodec.unmarshal(xml, OrgKycRequest.class, EsignErrorCode.ORG_INVALID_XML);
        OrgKycValidator.validate(request);
        reserve(orgTxns, request.txn, EsignErrorCode.ORG_DUPLICATE_TXN);
        orgRecords.put(request.txn, request);
        auditLog.add("ORG_KYC", request.txn, request.orgName);
        return orgResponse(request.txn, "1", null);
    }

    public String orgFailure(String xml, ApiException exception) {
        return orgResponse(XmlCodec.attribute(xml, "txn"), "0", exception);
    }

    public BankKycRequest bankRecord(String txn) {
        return bankRecords.get(txn);
    }

    private String bankResponse(String txn, String status, ApiException exception) {
        BankKycResponse response = new BankKycResponse();
        response.ver = SpecTexts.KYC_XML_VERSION;
        response.ts = IstTimestamps.now();
        response.txn = txn;
        response.status = status;
        response.resCode = "BANK" + Integer.toHexString(random.nextInt());
        if (exception != null) {
            response.error = exception.errorCode().code();
            response.errorCode = exception.errorCode().code();
            response.errorMessage = exception.getMessage();
        }
        return xmlSigner.sign(XmlCodec.marshal(response));
    }

    private String orgResponse(String txn, String status, ApiException exception) {
        OrgKycResponse response = new OrgKycResponse();
        response.ver = SpecTexts.KYC_XML_VERSION;
        response.ts = IstTimestamps.now();
        response.txn = txn;
        response.status = status;
        response.resCode = "ORG" + Integer.toHexString(random.nextInt());
        if (exception != null) {
            response.errorCode = exception.errorCode().code();
            response.errorMessage = exception.getMessage();
        }
        return xmlSigner.sign(XmlCodec.marshal(response));
    }

    private static void reserve(ConcurrentHashMap<String, LocalDate> txns, String txn, EsignErrorCode duplicate) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate existing = txns.putIfAbsent(txn, today);
        if (existing != null && existing.equals(today)) {
            throw new ApiException(duplicate);
        }
        if (existing != null) {
            txns.put(txn, today);
        }
    }
}

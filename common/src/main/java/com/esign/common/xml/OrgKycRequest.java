package com.esign.common.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Organisational KYC request used to exercise error codes 501-521.
 * API 3.3 removed the earlier organisational option-1 XML and kept the error catalogue in section 5.5.
 * This element carries the fields those codes validate.
 */
@XmlRootElement(name = "OrgKYC")
@XmlAccessorType(XmlAccessType.FIELD)
public class OrgKycRequest {
    @XmlAttribute
    public String ver;
    @XmlAttribute
    public String ts;
    @XmlAttribute
    public String txn;
    @XmlAttribute
    public String kycOrgId;
    @XmlAttribute
    public String orgName;
    @XmlAttribute
    public String orgUnit;
    @XmlAttribute
    public String orgSigName;
    @XmlAttribute
    public String orgSigCertId;
    @XmlAttribute
    public String employeeId;
    @XmlAttribute
    public String physicalVerification;
    @XmlElement(name = "KYCInfo")
    public BankKycRequest.KycInfo kycInfo;
    @XmlElement(name = "Photo")
    public BankKycRequest.Photo photo;
}

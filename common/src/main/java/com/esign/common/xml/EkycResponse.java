package com.esign.common.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "eKycResp")
@XmlAccessorType(XmlAccessType.FIELD)
public class EkycResponse {
    @XmlAttribute
    public String ver;
    @XmlAttribute
    public String status;
    @XmlAttribute
    public String signerid;
    @XmlAttribute
    public String ts;
    @XmlAttribute
    public String txn;
    @XmlAttribute
    public String error;
    @XmlAttribute
    public String respCode;
    @XmlElement(name = "kycData")
    public KycData kycData;
    @XmlElement(name = "Photo")
    public String photo;
    @XmlElement(name = "OrganisationDetails")
    public OrganisationDetails organisationDetails;
    @XmlElement(name = "Bankdetails")
    public BankDetails bankDetails;

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class KycData {
        @XmlAttribute
        public String name;
        @XmlAttribute
        public String mobile;
        @XmlAttribute
        public String email;
        @XmlAttribute
        public String address;
        @XmlAttribute
        public String stateProvince;
        @XmlAttribute
        public String country;
        @XmlAttribute
        public String postalCode;
        @XmlAttribute(name = "PAN")
        public String pan;
        @XmlAttribute(name = "DOB")
        public String dob;
        @XmlAttribute(name = "Gender")
        public String gender;
        @XmlAttribute(name = "Aadhaar")
        public String aadhaar;
        @XmlAttribute
        public String eKYCtype;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OrganisationDetails {
        @XmlAttribute
        public String txn;
        @XmlAttribute(name = "OrgName")
        public String orgName;
        @XmlAttribute(name = "Orgunit")
        public String orgUnit;
        @XmlAttribute(name = "KycOrgId")
        public String kycOrgId;
        @XmlAttribute
        public String orgSigName;
        @XmlAttribute
        public String orgSigCertId;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BankDetails {
        @XmlAttribute
        public String txn;
        @XmlAttribute
        public String bankIfscCode;
        @XmlAttribute
        public String bankName;
        @XmlAttribute
        public String accountNumber;
    }
}

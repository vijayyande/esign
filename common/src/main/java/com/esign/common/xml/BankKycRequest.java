package com.esign.common.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

@XmlRootElement(name = "BankKYC")
@XmlAccessorType(XmlAccessType.FIELD)
public class BankKycRequest {
    @XmlAttribute
    public String ver;
    @XmlAttribute
    public String ts;
    @XmlAttribute
    public String txn;
    @XmlAttribute
    public String bankIfscCode;
    @XmlAttribute
    public String bankName;
    @XmlAttribute
    public String accountNumber;
    @XmlElement(name = "KYCInfo")
    public KycInfo kycInfo;
    @XmlElement(name = "Photo")
    public Photo photo;

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class KycInfo {
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
        @XmlAttribute
        public String dateOfBirth;
        @XmlAttribute
        public String gender;
        @XmlAttribute
        public String pan;
        @XmlAttribute(name = "Aadhaar")
        public String aadhaar;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Photo {
        @XmlAttribute
        public String format;
        @XmlValue
        public String value;
    }
}

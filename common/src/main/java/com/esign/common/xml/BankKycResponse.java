package com.esign.common.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "BankKYCResp")
@XmlAccessorType(XmlAccessType.FIELD)
public class BankKycResponse {
    @XmlAttribute
    public String ver;
    @XmlAttribute
    public String ts;
    @XmlAttribute
    public String txn;
    @XmlAttribute
    public String status;
    @XmlAttribute
    public String resCode;
    @XmlAttribute
    public String error;
    @XmlAttribute
    public String errorCode;
    @XmlAttribute
    public String errorMessage;
}

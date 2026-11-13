package com.esign.common.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "eKycReq")
@XmlAccessorType(XmlAccessType.FIELD)
public class EkycRequest {
    @XmlAttribute
    public String ver;
    @XmlAttribute
    public String signerid;
    @XmlAttribute
    public String ts;
    @XmlAttribute
    public String txn;
    @XmlAttribute
    public String pinhash;
    @XmlElement(name = "SecondFactor")
    public String secondFactor;
}

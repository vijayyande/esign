package com.esign.common.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "SecondFactorRequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class SecondFactorRequest {
    @XmlAttribute
    public String ver;
    @XmlAttribute
    public String signerid;
    @XmlAttribute
    public String ts;
    @XmlAttribute
    public String txn;
    @XmlAttribute
    public String authenticationType;
    @XmlAttribute
    public String consent;
    @XmlElement(name = "SMSOTP")
    public String smsOtp;
    @XmlElement(name = "TOTP")
    public String totp;
    @XmlElement(name = "MAT")
    public String mat;
    @XmlElement(name = "FIDO")
    public String fido;
    @XmlElement(name = "PUBSIGN")
    public String pubSign;
}

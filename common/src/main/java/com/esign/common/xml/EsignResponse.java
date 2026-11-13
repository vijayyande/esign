package com.esign.common.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "EsignResp")
@XmlAccessorType(XmlAccessType.FIELD)
public class EsignResponse {
    @XmlAttribute
    public String ver;
    @XmlAttribute
    public String status;
    @XmlAttribute
    public String ts;
    @XmlAttribute
    public String txn;
    @XmlAttribute
    public String resCode;
    @XmlAttribute
    public String error;
    @XmlElement(name = "UserX509Certificate")
    public String userX509Certificate;
    @XmlElement(name = "Signatures")
    public Signatures signatures;

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Signatures {
        @XmlElement(name = "DocSignature")
        public List<DocSignature> docSignatures = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DocSignature {
        @XmlAttribute
        public String id;
        @XmlAttribute
        public String sigHashAlgorithm;
        @XmlAttribute
        public String error;
        @XmlValue
        public String value;
    }
}

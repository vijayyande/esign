package com.esign.common.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "Esign")
@XmlAccessorType(XmlAccessType.FIELD)
public class EsignRequest {
    @XmlAttribute
    public String ver;
    @XmlAttribute
    public String signerid;
    @XmlAttribute
    public String ts;
    @XmlAttribute
    public String txn;
    @XmlAttribute
    public String maxWaitPeriod;
    @XmlAttribute
    public String aspId;
    @XmlAttribute
    public String responseUrl;
    @XmlAttribute
    public String redirectUrl;
    @XmlAttribute
    public String signingAlgorithm;
    @XmlElement(name = "Docs")
    public Docs docs;

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Docs {
        @XmlElement(name = "InputHash")
        public List<InputHash> inputHashes = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class InputHash {
        @XmlAttribute
        public String id;
        @XmlAttribute
        public String hashAlgorithm;
        @XmlAttribute
        public String docInfo;
        @XmlAttribute
        public String docUrl;
        @XmlAttribute
        public String responseSigType;
        @XmlValue
        public String value;
    }
}

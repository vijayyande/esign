package com.esign.common.crypto;

import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.xml.XmlCodec;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

public final class XmlSigner {
    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    public XmlSigner(KeyMaterial material) {
        this.privateKey = material.privateKey();
        this.certificate = material.certificate();
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public String sign(String xml) {
        try {
            Document document = XmlCodec.parse(xml, EsignErrorCode.INVALID_REQUEST_FORMAT);
            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            Reference reference = factory.newReference(
                    "",
                    factory.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                    null,
                    null);
            SignedInfo signedInfo = factory.newSignedInfo(
                    factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                    factory.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
                    List.of(reference));
            KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
            X509Data x509Data = keyInfoFactory.newX509Data(List.of(certificate));
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));
            DOMSignContext context = new DOMSignContext(privateKey, document.getDocumentElement());
            factory.newXMLSignature(signedInfo, keyInfo).sign(context);
            return XmlCodec.toString(document);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.INVALID_DIGITAL_SIGNATURE, exception.getMessage());
        }
    }

    public void verify(String xml, EsignErrorCode missing, EsignErrorCode invalid) {
        verifyAndCertificate(xml, missing, invalid);
    }

    public X509Certificate verifyAndCertificate(String xml, EsignErrorCode missing, EsignErrorCode invalid) {
        try {
            Document document = XmlCodec.parse(xml, invalid);
            NodeList nodes = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (nodes.getLength() == 0) {
                throw new ApiException(missing);
            }
            DOMValidateContext context = new DOMValidateContext(new EmbeddedCertificateSelector(), nodes.item(0));
            context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
            XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
            if (!signature.validate(context)) {
                throw new ApiException(invalid);
            }
            X509Certificate certificate = certificateIn(signature.getKeyInfo());
            if (certificate == null) {
                throw new ApiException(invalid, "Signature has no X509 certificate");
            }
            return certificate;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(invalid);
        }
    }

    private static X509Certificate certificateIn(KeyInfo keyInfo) {
        if (keyInfo == null) {
            return null;
        }
        for (XMLStructure structure : keyInfo.getContent()) {
            if (!(structure instanceof X509Data data)) {
                continue;
            }
            for (Object item : data.getContent()) {
                if (item instanceof X509Certificate certificate) {
                    return certificate;
                }
            }
        }
        return null;
    }

    private static final class EmbeddedCertificateSelector extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, javax.xml.crypto.XMLCryptoContext context)
                throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("Signature has no key info");
            }
            for (XMLStructure structure : keyInfo.getContent()) {
                if (!(structure instanceof X509Data data)) {
                    continue;
                }
                for (Object item : data.getContent()) {
                    if (item instanceof X509Certificate certificate) {
                        return certificate::getPublicKey;
                    }
                }
            }
            throw new KeySelectorException("Signature has no X509 certificate");
        }
    }
}

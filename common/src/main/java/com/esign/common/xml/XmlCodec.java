package com.esign.common.xml;

import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XmlCodec {
    private static final JAXBContext CONTEXT = newContext();
    private static final Pattern ATTRIBUTE = Pattern.compile("\\b([A-Za-z0-9:_-]+)\\s*=\\s*\"([^\"]*)\"");

    private XmlCodec() {
    }

    public static String marshal(Object value) {
        try {
            Marshaller marshaller = CONTEXT.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            marshaller.marshal(value, writer);
            return writer.toString();
        } catch (JAXBException exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }

    public static <T> T unmarshal(String xml, Class<T> type, EsignErrorCode formatError) {
        try {
            Document document = parse(xml, formatError);
            removeSignatures(document);
            return type.cast(CONTEXT.createUnmarshaller().unmarshal(document));
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException | JAXBException exception) {
            throw new ApiException(formatError);
        }
    }

    public static Document parse(String xml, EsignErrorCode formatError) {
        if (xml == null || xml.isBlank()) {
            throw new ApiException(formatError);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ApiException(formatError);
        }
    }

    public static String toString(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }

    public static void removeSignatures(Document document) {
        NodeList nodes = document.getElementsByTagNameNS("*", "Signature");
        while (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            node.getParentNode().removeChild(node);
        }
        NodeList plain = document.getElementsByTagName("Signature");
        while (plain.getLength() > 0) {
            Node node = plain.item(0);
            node.getParentNode().removeChild(node);
        }
    }

    public static String attribute(String xml, String name) {
        if (xml == null) {
            return "";
        }
        Matcher matcher = ATTRIBUTE.matcher(xml);
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    private static JAXBContext newContext() {
        try {
            return JAXBContext.newInstance(
                    EsignRequest.class,
                    EsignResponse.class,
                    EsignStatusRequest.class,
                    EkycRequest.class,
                    EkycResponse.class,
                    SecondFactorRequest.class,
                    BankKycRequest.class,
                    BankKycResponse.class,
                    OrgKycRequest.class,
                    OrgKycResponse.class);
        } catch (JAXBException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

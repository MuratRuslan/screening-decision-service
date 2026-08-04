package kg.tunduk.test.senior.screeningdecisionservice.soap;

import kg.tunduk.test.senior.screeningdecisionservice.soap.model.EducationVerificationResult;
import kg.tunduk.test.senior.screeningdecisionservice.soap.model.VerifyEducationRequest;
import kg.tunduk.test.senior.screeningdecisionservice.soap.model.VerifyEducationResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

/** Builds/parses request and response XML by hand via DOM - no JAXB, the schema is tiny enough not to need it. */
public class EducationVerificationXmlCodec {

    private static final String NS = "http://cv-scan.local/education-verification";

    public String marshalRequest(VerifyEducationRequest request) {
        try {
            Document doc = newDocument();
            Element root = element(doc, "VerifyEducationRequest");
            doc.appendChild(root);
            root.appendChild(textElement(doc, "candidateId", request.candidateId()));
            root.appendChild(textElement(doc, "fullName", request.fullName()));
            root.appendChild(textElement(doc, "educationText", request.educationText()));
            return serialize(doc);
        } catch (Exception e) {
            throw new XmlCodecException("Failed to marshal VerifyEducationRequest", e);
        }
    }

    public VerifyEducationRequest unmarshalRequest(String xml) {
        Element root = parse(xml);
        return new VerifyEducationRequest(text(root, "candidateId"), text(root, "fullName"), text(root, "educationText"));
    }

    public String marshalResponse(VerifyEducationResponse response) {
        try {
            Document doc = newDocument();
            Element root = element(doc, "VerifyEducationResponse");
            doc.appendChild(root);
            root.appendChild(textElement(doc, "candidateId", response.candidateId()));
            root.appendChild(textElement(doc, "result", response.result().name()));
            if (response.message() != null) {
                root.appendChild(textElement(doc, "message", response.message()));
            }
            return serialize(doc);
        } catch (Exception e) {
            throw new XmlCodecException("Failed to marshal VerifyEducationResponse", e);
        }
    }

    public VerifyEducationResponse unmarshalResponse(String xml) {
        Element root = parse(xml);
        String message = hasChild(root, "message") ? text(root, "message") : null;
        return new VerifyEducationResponse(text(root, "candidateId"),
                EducationVerificationResult.valueOf(text(root, "result")), message);
    }

    private Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }

    private Element element(Document doc, String localName) {
        return doc.createElementNS(NS, localName);
    }

    private Element textElement(Document doc, String localName, String value) {
        Element el = element(doc, localName);
        el.setTextContent(value == null ? "" : value);
        return el;
    }

    private String serialize(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private Element parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new XmlCodecException("Failed to parse XML", e);
        }
    }

    private String text(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS(NS, localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private boolean hasChild(Element root, String localName) {
        return root.getElementsByTagNameNS(NS, localName).getLength() > 0;
    }
}

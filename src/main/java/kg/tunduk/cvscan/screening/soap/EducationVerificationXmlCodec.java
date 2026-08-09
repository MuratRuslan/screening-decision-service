package kg.tunduk.cvscan.screening.soap;

import kg.tunduk.cvscan.screening.soap.model.EducationVerificationResult;
import kg.tunduk.cvscan.screening.soap.model.VerifyEducationRequest;
import kg.tunduk.cvscan.screening.soap.model.VerifyEducationResponse;
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

/** Собирает и разбирает XML запроса и ответа вручную через DOM — без JAXB, схема слишком маленькая, чтобы он был нужен. */
public class EducationVerificationXmlCodec {

    private static final String NS = "http://cv-scan.local/education-verification";

    public String marshalRequest(final VerifyEducationRequest request) {
        try {
            final Document doc = newDocument();
            final Element root = element(doc, "VerifyEducationRequest");
            doc.appendChild(root);
            root.appendChild(textElement(doc, "candidateId", request.candidateId()));
            root.appendChild(textElement(doc, "fullName", request.fullName()));
            root.appendChild(textElement(doc, "educationText", request.educationText()));
            return serialize(doc);
        } catch (Exception e) {
            throw new XmlCodecException("Failed to marshal VerifyEducationRequest", e);
        }
    }

    public VerifyEducationRequest unmarshalRequest(final String xml) {
        final Element root = parse(xml);
        return new VerifyEducationRequest(text(root, "candidateId"), text(root, "fullName"), text(root, "educationText"));
    }

    public String marshalResponse(final VerifyEducationResponse response) {
        try {
            final Document doc = newDocument();
            final Element root = element(doc, "VerifyEducationResponse");
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

    public VerifyEducationResponse unmarshalResponse(final String xml) {
        final Element root = parse(xml);
        final String message = hasChild(root, "message") ? text(root, "message") : null;
        return new VerifyEducationResponse(text(root, "candidateId"),
                EducationVerificationResult.valueOf(text(root, "result")), message);
    }

    private Document newDocument() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }

    private Element element(final Document doc, final String localName) {
        return doc.createElementNS(NS, localName);
    }

    private Element textElement(final Document doc, final String localName, final String value) {
        final Element el = element(doc, localName);
        el.setTextContent(value == null ? "" : value);
        return el;
    }

    private String serialize(final Document doc) throws Exception {
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        final StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private Element parse(final String xml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(new InputSource(new StringReader(xml)));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new XmlCodecException("Failed to parse XML", e);
        }
    }

    private String text(final Element root, final String localName) {
        final NodeList nodes = root.getElementsByTagNameNS(NS, localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private boolean hasChild(final Element root, final String localName) {
        return root.getElementsByTagNameNS(NS, localName).getLength() > 0;
    }
}

package kg.tunduk.test.senior.screeningdecisionservice.soap;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Validates request/response XML against contract/soap/education-verification.xsd. Uses a
 * schema-validating SAX parse (rather than {@link javax.xml.validation.Validator#validate})
 * so an {@link org.xml.sax.ErrorHandler} can be paired with a {@link org.xml.sax.ContentHandler}
 * that tracks the current open-element path, giving each diagnostic a best-effort location.
 */
public class EducationVerificationXsdValidator {

    private final Schema schema;

    public EducationVerificationXsdValidator(Schema schema) {
        this.schema = schema;
    }

    public static Schema loadSchema(InputStream xsdStream) throws SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return factory.newSchema(new StreamSource(xsdStream));
    }

    public List<XmlDiagnostic> validate(String xml) {
        List<XmlDiagnostic> diagnostics = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();

        DefaultHandler handler = new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                path.addLast(localName);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if (!path.isEmpty()) {
                    path.removeLast();
                }
            }

            @Override
            public void warning(SAXParseException e) {
                // Schema warnings are not contract violations.
            }

            @Override
            public void error(SAXParseException e) {
                diagnostics.add(new XmlDiagnostic(currentPath(path), e.getMessage()));
            }

            @Override
            public void fatalError(SAXParseException e) {
                diagnostics.add(new XmlDiagnostic(currentPath(path), e.getMessage()));
            }
        };

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setSchema(schema);
            SAXParser parser = factory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            diagnostics.add(new XmlDiagnostic("/", "Не удалось разобрать XML: " + e.getMessage()));
        }

        return diagnostics;
    }

    private static String currentPath(Deque<String> path) {
        return path.isEmpty() ? "/" : "/" + String.join("/", path);
    }
}

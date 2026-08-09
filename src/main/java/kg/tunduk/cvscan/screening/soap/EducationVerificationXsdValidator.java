package kg.tunduk.cvscan.screening.soap;

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
 * Проверяет XML запроса/ответа по contract/soap/education-verification.xsd. Использует
 * SAX-парсинг с валидацией по схеме (а не {@link javax.xml.validation.Validator#validate}),
 * чтобы {@link org.xml.sax.ErrorHandler} можно было связать с {@link org.xml.sax.ContentHandler},
 * который отслеживает текущий путь открытых элементов — это даёт примерное расположение для каждой ошибки.
 */
public class EducationVerificationXsdValidator {

    private final Schema schema;

    public EducationVerificationXsdValidator(final Schema schema) {
        this.schema = schema;
    }

    public static Schema loadSchema(final InputStream xsdStream) throws SAXException {
        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return factory.newSchema(new StreamSource(xsdStream));
    }

    public List<XmlDiagnostic> validate(final String xml) {
        final List<XmlDiagnostic> diagnostics = new ArrayList<>();
        final Deque<String> path = new ArrayDeque<>();

        final DefaultHandler handler = new DefaultHandler() {
            @Override
            public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
                path.addLast(localName);
            }

            @Override
            public void endElement(final String uri, final String localName, final String qName) {
                if (!path.isEmpty()) {
                    path.removeLast();
                }
            }

            @Override
            public void warning(final SAXParseException e) {
                // Предупреждения схемы не считаются нарушением контракта.
            }

            @Override
            public void error(final SAXParseException e) {
                diagnostics.add(new XmlDiagnostic(currentPath(path), e.getMessage()));
            }

            @Override
            public void fatalError(final SAXParseException e) {
                diagnostics.add(new XmlDiagnostic(currentPath(path), e.getMessage()));
            }
        };

        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setSchema(schema);
            final SAXParser parser = factory.newSAXParser();
            final XMLReader reader = parser.getXMLReader();
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            diagnostics.add(new XmlDiagnostic("/", "Не удалось разобрать XML: " + e.getMessage()));
        }

        return diagnostics;
    }

    private static String currentPath(final Deque<String> path) {
        return path.isEmpty() ? "/" : "/" + String.join("/", path);
    }
}

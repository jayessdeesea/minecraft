package io.github.jayessdeesea.minecraft.command.ai;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * One XML-tagged section of a model's user prompt - {@code <time>}, {@code <squad name="Alpha">} -
 * written by the JDK's XML writer, so a {@code <} or {@code &} in an operator's mission or remark
 * is escaped rather than read as markup (AGENTS.md, Standard formats go through standard libraries).
 * A prompt is a run of sections, not one document: each is written on its own.
 */
final class PromptSection {

    private PromptSection() {
    }

    /** {@code <name>}, a line break, {@code body} - which ends with its own line break - then {@code </name>} and a line break. */
    static String of(String name, String body) {
        return of(name, Map.of(), body);
    }

    /** As {@link #of(String, String)}, the start tag carrying {@code attributes}. */
    static String of(String name, Map<String, String> attributes, String body) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(out);
            xml.writeStartElement(name);
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                xml.writeAttribute(attribute.getKey(), attribute.getValue());
            }
            xml.writeCharacters("\n" + body);
            xml.writeEndElement();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("couldn't write the prompt's <" + name + "> section", e);
        }
        return out + "\n";
    }
}

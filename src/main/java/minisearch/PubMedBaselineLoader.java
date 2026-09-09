package minisearch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Loads PubMed baseline citations with a title and abstract into MiniSearch documents. */
public final class PubMedBaselineLoader {
    public List<Document> load(Path directory, int limit) throws IOException {
        List<Document> documents = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".xml.gz"))
                    .sorted()
                    .toList()) {
                loadFile(path, limit, documents);
                if (documents.size() == limit) {
                    break;
                }
            }
        }
        return documents;
    }

    private void loadFile(Path path, int limit, List<Document> documents) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);

        try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            String id = null;
            String title = null;
            StringBuilder abstractText = new StringBuilder();
            StringBuilder text = null;
            String target = null;
            int depth = 0;

            while (reader.hasNext() && documents.size() < limit) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if (name.equals("PubmedArticle")) {
                        id = null;
                        title = null;
                        abstractText = new StringBuilder();
                    }
                    if (target != null) {
                        depth++;
                    } else if (name.equals("PMID") || name.equals("ArticleTitle") || name.equals("AbstractText")) {
                        target = name;
                        text = new StringBuilder();
                        depth = 1;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    if (text != null) {
                        text.append(reader.getText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (target != null) {
                        depth--;
                        if (depth == 0) {
                            String value = text.toString().trim();
                            if (target.equals("PMID") && id == null) {
                                id = value;
                            } else if (target.equals("ArticleTitle")) {
                                title = value;
                            } else if (target.equals("AbstractText") && !value.isEmpty()) {
                                if (!abstractText.isEmpty()) {
                                    abstractText.append(' ');
                                }
                                abstractText.append(value);
                            }
                            target = null;
                            text = null;
                        }
                    }
                    if (reader.getLocalName().equals("PubmedArticle")
                            && id != null
                            && title != null
                            && !title.isEmpty()
                            && !abstractText.isEmpty()) {
                        documents.add(new Document(Integer.parseInt(id), title, abstractText.toString()));
                    }
                }
            }
            reader.close();
        } catch (XMLStreamException exception) {
            throw new IOException("Could not read PubMed XML: " + path, exception);
        }
    }
}

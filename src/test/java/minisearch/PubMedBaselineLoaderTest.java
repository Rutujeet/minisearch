package minisearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PubMedBaselineLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsOnlyCitationsWithAnIdTitleAndAbstract() throws IOException {
        Path corpus = tempDir.resolve("pubmed26n0001.xml.gz");
        String xml = """
                <PubmedArticleSet>
                  <PubmedArticle><MedlineCitation><PMID>7</PMID><Article>
                    <ArticleTitle>First <i>title</i></ArticleTitle>
                    <Abstract><AbstractText>Part one.</AbstractText><AbstractText>Part two.</AbstractText></Abstract>
                  </Article></MedlineCitation></PubmedArticle>
                  <PubmedArticle><MedlineCitation><PMID>8</PMID><Article>
                    <ArticleTitle>No abstract</ArticleTitle>
                  </Article></MedlineCitation></PubmedArticle>
                </PubmedArticleSet>
                """;
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(corpus))) {
            output.write(xml.getBytes(StandardCharsets.UTF_8));
        }

        List<Document> documents = new PubMedBaselineLoader().load(tempDir, 100);

        assertEquals(List.of(new Document(7, "First title", "Part one. Part two.")), documents);
    }
}

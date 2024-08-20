package pro.kirillorlov.chat_summarizer.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class OllamaProperties {
    @Value("${ollama.url}")
    private String ollamaUrl;

    @Value("${ollama.modelFile}")
    private String ollamaModelFile;

    @Value("${ollama.path}")
    private String ollamaLocalPath;

    @Value("${ollama.N1}")
    private String N1;
    @Value("${ollama.N2}")
    private String N2;

    public String getN1() {
        return N1;
    }

    public void setN1(String n1) {
        N1 = n1;
    }

    public String getN2() {
        return N2;
    }

    public void setN2(String n2) {
        N2 = n2;
    }

    public String getOllamaUrl() {
        return ollamaUrl;
    }

    public void setOllamaUrl(String ollamaUrl) {
        this.ollamaUrl = ollamaUrl;
    }

    public String getOllamaModelFile() {
        return ollamaModelFile;
    }

    public void setOllamaModelFile(String ollamaModelFile) {
        this.ollamaModelFile = ollamaModelFile;
    }

    public String getOllamaLocalPath() {
        return ollamaLocalPath;
    }

    public void setOllamaLocalPath(String ollamaLocalPath) {
        this.ollamaLocalPath = ollamaLocalPath;
    }

    public String getOllamaModelShortFileName() {
        try {
            return new URI(getOllamaModelFile()).getPath().replace("/", "_");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String getOllamaModelShortName() {
        return getOllamaModelShortFileName().replace(".", "_");
    }
}

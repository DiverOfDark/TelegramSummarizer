package pro.kirillorlov.chat_summarizer.llama;

import dev.langchain4j.internal.Json;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.ollama.OllamaLanguageModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.output.Response;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import pro.kirillorlov.chat_summarizer.properties.OllamaProperties;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LlamaController {
    private static final Logger logger = LogManager.getLogger(LlamaController.class);

    private final LanguageModel languageModel;
    private final OllamaModels models;
    private final OllamaProperties props;

    public LlamaController(OllamaProperties props) throws Exception {
        this.props = props;

        models = new OllamaModels(props.getOllamaUrl(),
                Duration.ofSeconds(60),
                3,
                true,
                true
                );

        List<OllamaModel> listResponse = models.availableModels().content();
        if (listResponse.stream().noneMatch(t -> t.getName().contains(props.getOllamaModelShortName()))) {
            downloadModel(props);
        }
        languageModel = OllamaLanguageModel
                .builder()
                .baseUrl(props.getOllamaUrl())
                .modelName(props.getOllamaModelShortName())
                .timeout(Duration.ofMinutes(30))
                .build();
    }

    private void downloadModel(OllamaProperties props) throws Exception {
        String modelFile = Path.of(props.getOllamaLocalPath(), props.getOllamaModelShortFileName()).toString();
        if (!new File(modelFile).exists()) {
            logger.info("Downloading model file to {}...", modelFile);
            LargeFileDownloader.downloadFile(props.getOllamaModelFile(), modelFile);
        }
        logger.info("Downloaded, loading to Ollama...");

        ConnectionConfig connConfig = ConnectionConfig.custom()
                .setConnectTimeout(2, TimeUnit.HOURS)
                .setSocketTimeout(2, TimeUnit.HOURS)
                .build();
        try(BasicHttpClientConnectionManager cm = new BasicHttpClientConnectionManager()) {
            cm.setConnectionConfig(connConfig);

            // Create the HttpClient
            try (CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build()) {
                // Create a POST request with the payload
                HttpPost request = new HttpPost(props.getOllamaUrl() + "/api/create");
                request.setHeader("Content-Type", "application/json");
                HashMap<String, String> requestParams = new HashMap<>();
                requestParams.put("name", props.getOllamaModelShortName());
                requestParams.put("modelfile", "FROM " + modelFile);

                String requestString = Json.toJson(requestParams);

                request.setEntity(new StringEntity(requestString));

                // Execute the request and handle the response
                try (CloseableHttpResponse response = httpClient.execute(request);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()), 16)) {

                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // Process each line of the response as it arrives
                            try {
                                HashMap hashMap = Json.fromJson(line, HashMap.class);
                                String status = (String) hashMap.get("status");
                                logger.info("Progress: {}", status);
                            } catch (Exception e) {
                                logger.error("Error parsing response: {}", line);
                                throw e;
                            }
                        }

                    } catch (StreamClosedException e) {
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        logger.info("Checking that new model is available");

        List<OllamaModel> listResponse = models.availableModels().content();
        if (listResponse.stream().noneMatch(t -> t.getName().contains(props.getOllamaModelShortName()))) {
            throw new Exception("Model not available after upload");
        }

        logger.info("Model loaded.");
    }

    public String summarizeChatDumps(List<String> chatDumps) {
        List<String> results = new ArrayList<>();
        while(!chatDumps.isEmpty()) {
            logger.info(String.format("Left chunks - %s", chatDumps.size()));
            String chat = chatDumps.removeFirst();
            String result = getCompletion(chat, "\n\n" + props.getN1());

            results.add(result);
            logger.debug("chunk - " + result);
        }

        String together = String.join("\n", results);
        if (!results.isEmpty()) {
            return getCompletion(together, "\n\n" + props.getN2());
        }
        return together;
    }

    private String getCompletion(String chat, String prompt) {
        logger.info("Queried llama...");
        long before = System.currentTimeMillis();
        Response<String> generate = languageModel.generate(chat + prompt);
        String content = generate.content();
        long after = System.currentTimeMillis();
        logger.info(String.format("Query took %s", Duration.ofMillis(after - before)));
        return content;
    }
}

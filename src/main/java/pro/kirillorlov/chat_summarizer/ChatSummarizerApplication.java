package pro.kirillorlov.chat_summarizer;

import com.pengrad.telegrambot.TelegramBot;
import dev.langchain4j.model.ollama.OllamaLanguageModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import pro.kirillorlov.chat_summarizer.properties.ChatProperties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

@SpringBootApplication
public class ChatSummarizerApplication {
    private static final Logger logger = LogManager.getLogger(ChatSummarizerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ChatSummarizerApplication.class, args);
    }

    @Bean
    public OllamaLanguageModel ollamaApi(ChatProperties props, OllamaModels models) throws Exception {
        List<OllamaModel> listResponse = models.availableModels().content();
        if (listResponse.stream().noneMatch(t -> t.getName().equals(props.getOllamaModel()))) {
            throw new Exception("Please pull model " + props.getOllamaModel());
        }
        return OllamaLanguageModel.builder().baseUrl(props.getOllamaUrl()).modelName(props.getOllamaModel()).timeout(Duration.ofMinutes(10)).build();
    }

    @Bean
    public OllamaModels ollamaModels(ChatProperties props) {
        return OllamaModels.builder().baseUrl(props.getOllamaUrl()).build();
    }

    @Bean
    public TelegramBot telegramBot(ChatProperties props, TelegramUpdatesListener updatesListener) {
        TelegramBot telegramBot = new TelegramBot(props.getBotToken());
        telegramBot.setUpdatesListener(updatesListener);
        updatesListener.setBot(telegramBot);
        return telegramBot;
    }

    @Bean
    public SimpleAuthenticationSupplier<?> authenticationSupplier(ChatProperties properties) {
        return AuthenticationSupplier.user(properties.getUserId());
    }

    @Bean
    public SimpleTelegramClient client(SimpleTelegramClientBuilder clientBuilder, SimpleAuthenticationSupplier<?> authenticationData, TelegramUpdatesListener updatesListener) {
        // Add an example update handler that prints when the bot is started
        clientBuilder.addUpdatesHandler(updatesListener);
        clientBuilder.addDefaultExceptionHandler(updatesListener);
        clientBuilder.addUpdateExceptionHandler(updatesListener);
        SimpleTelegramClient build = clientBuilder.build(authenticationData);
        updatesListener.setClient(build);
        return build;
    }

    @Bean
    public SimpleTelegramClientFactory factory() {
        return new SimpleTelegramClientFactory();
    }

    @Bean
    public SimpleTelegramClientBuilder telegramUserBot(ChatProperties props, SimpleTelegramClientFactory factory) throws Exception {
        Init.init();
        Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
        APIToken apiToken = APIToken.example();
        TDLibSettings settings = TDLibSettings.create(apiToken);

        Path sessionPath = Paths.get("example-tdlight-session");
        settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
        settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

        return factory.builder(settings);
    }
}

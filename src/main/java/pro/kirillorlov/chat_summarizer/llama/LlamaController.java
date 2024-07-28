package pro.kirillorlov.chat_summarizer.llama;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.output.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LlamaController {
    private static final Logger logger = LogManager.getLogger(LlamaController.class);

    private final LanguageModel languageModel;
    private final OllamaModels models;

    public LlamaController(LanguageModel languageModel, OllamaModels models) {
        this.languageModel = languageModel;
        this.models = models;
    }

    public String SUMMARIZE_N2_PROMPT = "\n\nЭто несколько списков тезисов что обсуждали сегодня в чате. Исправь нумерацию, чтобы она была консистента. Не оставляй пропуски между пунктами.";

    public String SUMMARIZE_CHAT_PROMPT = "\n\nЭто был отрывок из истории чата до настоящего момента. Ты бот для чата в телеграмме, который пишет summary про всё, о чем общались. " +
            "Твоя задача прислать короткие смешные тезисы о что обсуждали. Не присылай оригинальные сообщения или похожие тезисы." +
            "Ответь списком без повторяющихся элементов, пиши в том же стиле что и люди в чате, на русском языке. В твоем ответе не должно быть ничего кроме списка. Максимум 5 пунктов.";

    public String getCompletion(String chat, String prompt) {
        logger.info("Queried llama...");
        long before = System.currentTimeMillis();
        Response<String> generate = languageModel.generate(chat + prompt);
        String content = generate.content();
        long after = System.currentTimeMillis();
        logger.info(String.format("Query took %s", Duration.ofMillis(after - before)));
        return content;
    }
}

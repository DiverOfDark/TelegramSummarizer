package pro.kirillorlov.chat_summarizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChatSummarizerApplication {
    private static final Logger logger = LogManager.getLogger(ChatSummarizerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ChatSummarizerApplication.class, args);
    }
}

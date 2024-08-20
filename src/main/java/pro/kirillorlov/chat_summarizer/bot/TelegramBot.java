package pro.kirillorlov.chat_summarizer.bot;

import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pro.kirillorlov.chat_summarizer.jpa.Messages;
import pro.kirillorlov.chat_summarizer.jpa.MessagesRepository;
import pro.kirillorlov.chat_summarizer.llama.LlamaController;
import pro.kirillorlov.chat_summarizer.properties.TgBotProperties;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Profile("tgbot")
@Component
public class TelegramBot implements UpdatesListener {

    private final LlamaController llamaController;
    private final MessagesRepository messagesRepository;
    private com.pengrad.telegrambot.TelegramBot bot;

    public TelegramBot(TgBotProperties props, LlamaController llamaController, MessagesRepository messagesRepository) {
        this.llamaController = llamaController;
        this.messagesRepository = messagesRepository;

        this.bot = new com.pengrad.telegrambot.TelegramBot(props.getBotToken());
        bot.setUpdatesListener(this);

        processSavedMessages();
    }

    @Override
    public int process(List<Update> list) {
        int processedId = 0;
        for (Update update : list) {
            if (update.message() != null) {
                Message message = update.message();
                Messages messages = new Messages();
                messages.setSource(message);
                messages.setMessageId(message.messageId());
                messages.setTelegramChatId(message.chat().id());
                messagesRepository.save(messages);
            }
            processedId = update.updateId();
        }
        return processedId;
    }

    public void processSavedMessages() {
        List<Messages> all = messagesRepository.findAll();

        Date threeDaysAgo = new Date(Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli());

        for(var groups: all.stream().collect(Collectors.groupingBy(Messages::getTelegramChatId)).entrySet()) {
            Long chatId = groups.getKey();
            List<Message> messages = groups.getValue().stream().map(Messages::getSource).sorted(Comparator.comparing(MaybeInaccessibleMessage::date)).collect(Collectors.toList());

            List<Message> olderMessages = messages.stream().filter(t -> new Date(t.date() * 1000L).before(threeDaysAgo)).toList();
            for(Message m: olderMessages) {
                messages.remove(m);
                messagesRepository.deleteById(Long.valueOf(m.messageId()));
            }

            if (messages.size() < 50)
                continue;

            List<String> messagesString = messages.stream().map(this::toChatString).toList();
            AtomicInteger counter = new AtomicInteger();
            int chunkSize = 6000;
            List<String> chatDumps = messagesString.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(x -> counter.getAndAdd(x.length()) / chunkSize)).values()
                    .stream().map(t-> String.join("", t)).toList();

            String result = llamaController.summarizeChatDumps(new ArrayList<>(chatDumps));
            if (false) {
                bot.execute(new SendMessage(chatId, result));
                messagesRepository.deleteAllInBatch(groups.getValue());
            }
        }
    }

    private String toChatString(Message message) {
        String content = null;
        if (message.photo() != null) {
            content = "!photo.jpg;";
        }
        if (message.video() != null) {
            content = "!video.jpg";
        }
        if (message.text() != null) {
            if (content == null)
                content = "";
            content += message.text();
        }

        if (content == null)
            return null;

        String sender = message.from().firstName() + " @" + message.from().username();
        String date = new SimpleDateFormat().format(new Date(message.date() * 1000L));
        return String.format("%s %s: %s\n", date, sender, content);
    }
}

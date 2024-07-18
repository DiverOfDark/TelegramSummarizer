package pro.kirillorlov.chat_summarizer.jpa;

import com.google.gson.Gson;
import com.pengrad.telegrambot.model.Message;
import jakarta.persistence.*;

@Entity
@Table(name = "Messages")
public class Messages {
    @Transient
    private static Gson gson = new Gson();

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "chatId")
    private long telegramChatId;

    @Column(name = "source")
    @Lob
    private String source;

    @Column(name = "messageId")
    private long messageId;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(long telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public Message getSource() {
        return gson.fromJson(source, Message.class);
    }

    public void setSource(Message source) {
        this.source = gson.toJson(source);
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }
}


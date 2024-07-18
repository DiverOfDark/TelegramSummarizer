package pro.kirillorlov.chat_summarizer;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.pengrad.telegrambot.request.SendMessage;
import io.micrometer.common.util.StringUtils;
import it.tdlight.ExceptionHandler;
import it.tdlight.client.GenericUpdateHandler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import pro.kirillorlov.chat_summarizer.jpa.Messages;
import pro.kirillorlov.chat_summarizer.jpa.MessagesRepository;
import pro.kirillorlov.chat_summarizer.llama.LlamaController;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TelegramUpdatesListener implements UpdatesListener, GenericUpdateHandler<TdApi.Update>, ExceptionHandler {
    private static final Logger logger = LogManager.getLogger(TelegramUpdatesListener.class);
    private final MessagesRepository messagesRepository;
    private final LlamaController controller;
    private SimpleTelegramClient client;
    private TelegramBot bot;

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public TelegramUpdatesListener(MessagesRepository messagesRepository, LlamaController controller) {
        this.messagesRepository = messagesRepository;
        this.controller = controller;
        // processSavedMessages();
    }

    public void setClient(SimpleTelegramClient client) {
        this.client = client;
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

            String result = summarizeChatDumps(new ArrayList<>(chatDumps));
            if (false) {
                bot.execute(new SendMessage(chatId, result));
                messagesRepository.deleteAllInBatch(groups.getValue());
            }
        }
    }

    @Override
    public void onUpdate(TdApi.Update t) {
        Set<Class<?>> classes = new HashSet<>();
        Stream.of(TdApi.class, TdApi.UpdateNewMessage.class, TdApi.UpdateOption.class, TdApi.UpdateDefaultReactionType.class, TdApi.UpdateConnectionState.class, TdApi.UpdateChatMessageAutoDeleteTime.class,
                TdApi.UpdateAnimationSearchParameters.class, TdApi.UpdateChatThemes.class, TdApi.UpdateChatRemovedFromList.class, TdApi.UpdateSavedMessagesTopic.class, TdApi.UpdateChatActionBar.class,
                TdApi.UpdateAccentColors.class, TdApi.UpdateScopeNotificationSettings.class, TdApi.UpdateMessageEdited.class, TdApi.Messages.class, TdApi.UpdateChatPermissions.class,
                TdApi.UpdateProfileAccentColors.class, TdApi.UpdateChatFolders.class, TdApi.UpdateMessageInteractionInfo.class, TdApi.UpdateChatReadOutbox.class, TdApi.UpdateChatBackground.class,
                TdApi.UpdateSpeechRecognitionTrial.class, TdApi.UpdateUnreadChatCount.class, TdApi.UpdateChatUnreadReactionCount.class, TdApi.UpdateMessageUnreadReactions.class, TdApi.MessageVideoNote.class,
                TdApi.UpdateUser.class, TdApi.UpdateUnreadMessageCount.class, TdApi.UpdateNewChat.class, TdApi.UpdateChatAction.class, TdApi.UpdateChatNotificationSettings.class,
                TdApi.UpdateUserStatus.class, TdApi.UpdateHavePendingNotifications.class, TdApi.UpdateSupergroupFullInfo.class, TdApi.UpdateChatAvailableReactions.class,
                TdApi.UpdateChatPosition.class, TdApi.UpdateSupergroup.class, TdApi.UpdateBasicGroup.class, TdApi.UpdateChatIsTranslatable.class, TdApi.UpdateGroupCall.class,
                TdApi.UpdateAttachmentMenuBots.class, TdApi.UpdateChatLastMessage.class, TdApi.UpdateChatDraftMessage.class, TdApi.UpdateChatMessageSender.class, TdApi.UpdateChatVideoChat.class,
                TdApi.UpdateDefaultBackground.class, TdApi.UpdateChatAddedToList.class, TdApi.UpdateStoryStealthMode.class, TdApi.UpdateChatUnreadMentionCount.class,
                TdApi.UpdateFileDownloads.class, TdApi.UpdateChatReadInbox.class, TdApi.UpdateUserFullInfo.class, TdApi.UpdateMessageMentionRead.class, TdApi.UpdateChatReplyMarkup.class,
                TdApi.UpdateDiceEmojis.class, TdApi.UpdateDeleteMessages.class, TdApi.UpdateMessageContent.class, TdApi.UpdateChatPhoto.class, TdApi.UpdateChatActiveStories.class,
                TdApi.UpdateMessageIsPinned.class, TdApi.UpdateMessageIsPinned.class,
                TdApi.UpdateActiveEmojiReactions.class).forEach(classes::add);

        if (t instanceof TdApi.UpdateAuthorizationState) {
            TdApi.AuthorizationState authorizationState = ((TdApi.UpdateAuthorizationState) t).authorizationState;
            if (authorizationState instanceof TdApi.AuthorizationStateReady) {
                executorService.schedule(this::initUserClient, 5, TimeUnit.SECONDS);
            }
        } else if (classes.contains(t.getClass())) {
            logger.debug(t);
        } else {
            logger.info(t);
        }
    }

    private void initUserClient() {
        logger.info("Main chat list loaded");
        TdApi.GetChats getChats = new TdApi.GetChats();
        getChats.limit = 1000;
        logger.info("Init user client");

        client.send(getChats).whenCompleteAsync((a, b) -> {
            for (long l : a.chatIds) {
                TdApi.GetChat function = new TdApi.GetChat();
                function.chatId = l;
                client.send(function).whenCompleteAsync((c, d) -> {
                    logger.info("{} -> {} chat loaded", c.id, c.title);
                    if ("Предпоследнее пристанище".equals(c.title)) {
                        fetchHistory(c);
                    }
                });
            }
        });
    }

    private void fetchHistory(TdApi.Chat c) {
        fetchHistory(c, new TreeMap<>());
    }

    private void fetchHistory(TdApi.Chat c, TreeMap<Long, TdApi.Message> messages) {
        logger.info("Gathered {} messages", messages.size());
        if (messages.size() > 1) {
            Optional<TdApi.Message> any = messages.values().stream().filter(t -> {
                boolean isOld = new Date(t.date * 1000L).toInstant().isBefore(Instant.now().minus(1, ChronoUnit.DAYS));
                boolean hasDigest = (t.content instanceof TdApi.MessageText) && ((TdApi.MessageText) t.content).text.text.contains("#дайджест");
                return isOld || hasDigest;
            }).findAny();
            if (any.isPresent()) {
                List<Long> badMessages = messages.entrySet().stream().takeWhile(t -> t.getValue() != any.get()).map(t -> t.getKey()).toList();
                badMessages.forEach(messages::remove);
                enrichUsers(messages);
                return;
            }
        }
        TdApi.GetChatHistory function = new TdApi.GetChatHistory();
        function.chatId = c.id;
        function.onlyLocal = false;
        function.offset = -90;
        function.limit = 100;
        if (!messages.isEmpty()) {
            function.fromMessageId = messages.firstKey();
        }

        client.send(function, a -> {
            TdApi.Messages messages1 = a.get();
            for(TdApi.Message msg: messages1.messages) {
                messages.put(msg.id, msg);
            }
            fetchHistory(c, messages);
        });
    }

    private void enrichUsers(TreeMap<Long, TdApi.Message> messages, TreeMap<Long, TdApi.User> users, List<TdApi.GetUser> requests) {
        if (requests.isEmpty()) {
            String summarized = summarize(messages, users);
            logger.info(summarized);
        } else {
            TdApi.GetUser getUser = requests.removeFirst();
            client.send(getUser, onUser -> {
                TdApi.User user = onUser.get();
                users.put(user.id, user);
                enrichUsers(messages,users,requests);
            });
        }
    }

    private void enrichUsers(TreeMap<Long, TdApi.Message> messages) {
        List<TdApi.MessageSender> collect = messages.values().stream().map(t -> t.senderId).distinct().toList();
        List<TdApi.GetUser> requests = collect.stream().filter(t -> t instanceof TdApi.MessageSenderUser).map(t -> new TdApi.GetUser(((TdApi.MessageSenderUser) t).userId)).collect(Collectors.toList());

        TreeMap<Long, TdApi.User> users = new TreeMap<>();
        enrichUsers(messages, users, requests);
    }

    int chunkSize = 8000;
    private String summarize(Map<Long, TdApi.Message> messages, Map<Long, TdApi.User> users) {
        AtomicInteger counter = new AtomicInteger();
        List<String> chatDumps = messages.values().stream().map(x->this.toChatString(x, users)).filter(Objects::nonNull).collect(Collectors.groupingBy(x -> counter.getAndAdd(x.length()) / chunkSize)).values()
                .stream().map(t-> String.join("", t)).toList();

        return summarizeChatDumps(new ArrayList<>(chatDumps));
    }

    private String summarizeChatDumps(List<String> chatDumps) {
        List<String> results = new ArrayList<>();
        while(!chatDumps.isEmpty()) {
            logger.info(STR."Left chunks - \{chatDumps.size()}"); String chat = chatDumps.removeFirst();
            String result = controller.getCompletion(chat, controller.SUMMARIZE_CHAT_PROMPT);

            results.add(result);
            logger.debug("chunk - " + result);
        }

        String together = String.join("\n", results);
        if (results.size() > 0) {
            String result2 = controller.getCompletion(together, controller.SUMMARIZE_N2_PROMPT);
            return result2;
        }
        return together;
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

    private String toChatString(TdApi.Message message, Map<Long, TdApi.User> users) {
        String content = "<unsupported>";
        String sender = "User";
        switch (message.content) {
            case TdApi.MessageText messageText -> content = messageText.text.text;
            case TdApi.MessagePhoto messagePhoto -> content = "!photo.jpg; " + messagePhoto.caption.text;
            case TdApi.MessageSticker ignored -> { return null; }
            case TdApi.MessageAnimation ignored -> { return null; }
            case TdApi.MessageVideo ignored -> { return null; }
            case TdApi.MessageVideoNote ignored -> { return null; }
            case null, default -> logger.warn("Unsupported content " + message.content);
        }
        Date date1 = new Date(message.date * 1000L);
        String date = new SimpleDateFormat("hh:mm").format(date1);
        switch (message.senderId) {
            case TdApi.MessageSenderUser messageSenderUser -> {
                sender = Long.toString(messageSenderUser.userId);
                if (users.containsKey(messageSenderUser.userId)) {
                    TdApi.User userDetails = users.get(messageSenderUser.userId);
                    sender = Stream.of(userDetails.firstName, userDetails.lastName).filter(t->!StringUtils.isEmpty(t)).distinct().toList().getFirst();
                }
            }
            case null, default -> logger.warn("Unsupported content " + message.content);
        }

        return String.format("%s %s: %s\n", date, sender, content);
    }

    @Override
    public void onException(Throwable throwable) {
        logger.error(throwable);
    }

    public void setBot(TelegramBot telegramBot) {
        this.bot = telegramBot;
    }
}

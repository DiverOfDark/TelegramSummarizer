package pro.kirillorlov.chat_summarizer.bot;

import io.micrometer.common.util.StringUtils;
import it.tdlight.ExceptionHandler;
import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import it.tdlight.util.UnsupportedNativeLibraryException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pro.kirillorlov.chat_summarizer.llama.LlamaController;
import pro.kirillorlov.chat_summarizer.properties.UserBotProperties;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.tomcat.util.http.fileupload.FileUtils.deleteDirectory;

@Component
@Profile("userbot")
public class UserBot implements GenericUpdateHandler<TdApi.Update>, ExceptionHandler {
    private static final Logger logger = LogManager.getLogger(UserBot.class);
    private final LlamaController controller;
    private final UserBotProperties props;
    private SimpleTelegramClient client;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public UserBot(LlamaController controller, UserBotProperties props) throws UnsupportedNativeLibraryException, IOException {
        this.controller = controller;
        this.props = props;

        Init.init();
        Log.setLogMessageHandler(2, new Slf4JLogMessageHandler());
        APIToken apiToken = APIToken.example();
        TDLibSettings settings = TDLibSettings.create(apiToken);

        Path sessionPath = Paths.get(props.getDatadir());
        settings.setFileDatabaseEnabled(true);
        Path persistentDataPath = sessionPath.resolve("data");

        settings.setDatabaseDirectoryPath(persistentDataPath);
        settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

        try (SimpleTelegramClientFactory simpleTelegramClientFactory = new SimpleTelegramClientFactory()) {
            SimpleTelegramClientBuilder clientBuilder = simpleTelegramClientFactory.builder(settings);

            // Add an example update handler that prints when the bot is started
            clientBuilder.addUpdatesHandler(this);
            clientBuilder.addDefaultExceptionHandler(this);
            clientBuilder.addUpdateExceptionHandler(this);
            clientBuilder.setClientInteraction((inputParameter, parameterInfo) -> CompletableFuture.supplyAsync(() -> {
                switch (inputParameter) {
                    case NOTIFY_LINK -> {
                        ParameterInfoNotifyLink link = (ParameterInfoNotifyLink) parameterInfo;
                        logger.info("Please confirm this login link on another device: " + link);
                        String qr = QrCodeTerminal.getQr(link.getLink());
                        logger.info(qr);
                        return "";
                    }
                    case ASK_PASSWORD -> {
                        return props.getPassword();
                    }
                    case null, default -> {
                        logger.info("Parameter request: " + parameterInfo);
                        return null;
                    }
                }
            }));

            this.client = clientBuilder.build(AuthenticationSupplier.qrCode());
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
                TdApi.UpdateMessageIsPinned.class, TdApi.UpdateMessageIsPinned.class, TdApi.MessageAnimatedEmoji.class, TdApi.MessagePoll.class,
                TdApi.UpdateActiveEmojiReactions.class, TdApi.MessageVoiceNote.class, TdApi.UpdateAvailableMessageEffects.class, TdApi.UpdateChatViewAsTopics.class, TdApi.UpdateChatTheme.class,
                TdApi.UpdateSuggestedActions.class, TdApi.UpdateReactionNotificationSettings.class
        ).forEach(classes::add);

        if (t instanceof TdApi.UpdateAuthorizationState) {
            TdApi.AuthorizationState authorizationState = ((TdApi.UpdateAuthorizationState) t).authorizationState;
            switch (authorizationState) {
                case TdApi.AuthorizationStateLoggingOut ignored -> {
                    try {
                        deleteDirectory(new File(props.getDatadir()));
                    } catch (IOException e) {
                        logger.error(e);
                    }
                    System.exit(1);
                }
                case TdApi.AuthorizationStateClosed ignored -> { }
                case TdApi.AuthorizationStateWaitPassword ignored -> { }
                case TdApi.AuthorizationStateReady ignored ->
                    executorService.schedule(this::initUserClient, 10, TimeUnit.SECONDS);
                case null, default -> { }
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
        client.send(getChats).whenCompleteAsync((a, ignored) -> {
            for (long l : a.chatIds) {
                TdApi.GetChat function = new TdApi.GetChat();
                function.chatId = l;
                client.send(function).whenCompleteAsync((c, ignored1) -> {
                    logger.info("{} -> {} chat loaded", c.id, c.title);
                    if (props.getChatname().equals(c.title)) {
                        fetchHistory(c);
                    }
                });
            }
        });
    }

    private void fetchHistory(TdApi.Chat c) {
        fetchHistory(c, new TreeMap<>(), null);
    }

    private void fetchHistory(TdApi.Chat c, TreeMap<Long, TdApi.Message> messages, Long idealMessage) {
        logger.info("Gathered {} messages", messages.size());
        if (messages.size() > 1) {
            Optional<TdApi.Message> any = messages.values().stream().filter(t-> t.content instanceof TdApi.MessageText).filter(t -> {
                boolean isOld = new Date(t.date * 1000L).toInstant().isBefore(Instant.now().minus(3, ChronoUnit.DAYS));
                boolean hasDigest = ((TdApi.MessageText) t.content).text.text.contains("#дайджест");
                return isOld || hasDigest;
            }).findAny();
            if (any.isPresent()) {

                String text = ((TdApi.MessageText) any.get().content).text.text;
                String firstLine = text.lines().findFirst().orElse("");
                if (firstLine.contains("$")) {
                    String previousMessageId = firstLine.substring(firstLine.indexOf("$") + 1);
                    idealMessage = Long.valueOf(previousMessageId);
                }

                if (idealMessage == null) {
                    List<Long> badMessages = messages.entrySet().stream().takeWhile(t -> t.getValue() != any.get()).map(Map.Entry::getKey).toList();
                    badMessages.forEach(messages::remove);
                    messages.remove(any.get().id);
                    enrichUsers(messages);
                    return;
                }
            }

            if (idealMessage != null && messages.containsKey(idealMessage)) {
                long idealMessageUnboxed = idealMessage;
                List<Long> badMessages = messages.entrySet().stream().takeWhile(t -> t.getKey() != idealMessageUnboxed).map(Map.Entry::getKey).toList();
                badMessages.forEach(messages::remove);
                messages.remove(any.get().id);
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

        Long finalIdealMessage = idealMessage;

        client.send(function, a -> {
            TdApi.Messages messages1 = a.get();
            for (TdApi.Message msg : messages1.messages) {
                messages.put(msg.id, msg);
            }
            fetchHistory(c, messages, finalIdealMessage);
        });
    }

    private void enrichUsers
            (TreeMap<Long, TdApi.Message> messages, TreeMap<Long, TdApi.User> users, List<TdApi.GetUser> requests) {
        if (requests.isEmpty()) {
            Long msgId = messages.lastEntry().getKey();
            AtomicInteger counter = new AtomicInteger();
            List<String> chatDumps = (
                    (Map<Long, TdApi.Message>) messages)
                    .values()
                    .stream()
                    .map(x -> this.toChatString(x, users))
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(x -> counter.getAndAdd(x.length()) / chunkSize))
                    .values()
                    .stream().map(t -> String.join("", t)).toList();

            String summarized = controller.summarizeChatDumps(new ArrayList<>(chatDumps));
            String messagesCount = getMessagesCountByUser(messages, users, 5);

            String messageToCopyPaste = "#дайджест $" + msgId + "\n" + summarized + "\n\nВ основном писали:\n" + messagesCount;
            logger.info("Message to post:\n{}", messageToCopyPaste);

            TdApi.SendMessage message = new TdApi.SendMessage();
            message.chatId = messages.values().stream().findFirst().orElse(null).chatId;

            TdApi.InputMessageText inputMessageContent = new TdApi.InputMessageText();
            inputMessageContent.text = new TdApi.FormattedText(messageToCopyPaste, new TdApi.TextEntity[0]);

            message.inputMessageContent = inputMessageContent;

            if (!props.isSend()) {
                System.exit(0);
            }

            client.sendMessage(message, false).whenComplete((a, b) -> new Thread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
                System.exit(0);
            }).start());
            logger.info(messageToCopyPaste);
        } else {
            TdApi.GetUser getUser = requests.removeFirst();
            client.send(getUser, onUser -> {
                TdApi.User user = onUser.get();
                users.put(user.id, user);
                enrichUsers(messages, users, requests);
            });
        }
    }

    private void enrichUsers(TreeMap<Long, TdApi.Message> messages) {
        List<TdApi.MessageSender> collect = messages.values().stream().map(t -> t.senderId).distinct().toList();
        List<TdApi.GetUser> requests = collect.stream().filter(t -> t instanceof TdApi.MessageSenderUser).map(t -> new TdApi.GetUser(((TdApi.MessageSenderUser) t).userId)).collect(Collectors.toList());

        TreeMap<Long, TdApi.User> users = new TreeMap<>();
        enrichUsers(messages, users, requests);
    }

    int chunkSize = 4000;

    private String getMessagesCountByUser(Map<Long, TdApi.Message> messages, Map<Long, TdApi.User> users,
                                          int limit) {
        AtomicInteger integer = new AtomicInteger(limit);
        return messages.values()
                .stream()
                .filter(message -> toChatString(message, users) != null)
                .collect(Collectors.groupingBy(message -> getUserAsString(message, users), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(entry -> "%s: %d сообщений".formatted(entry.getKey(), entry.getValue()))
                .takeWhile(ignored -> integer.getAndDecrement() > 0)
                .collect(Collectors.joining("\n"));
    }


    private String toChatString(TdApi.Message message, Map<Long, TdApi.User> users) {
        String content = "<unsupported>";
        switch (message.content) {
            case TdApi.MessageText messageText -> content = messageText.text.text;
            case TdApi.MessagePhoto messagePhoto -> content = "!photo.jpg; " + messagePhoto.caption.text;
            case TdApi.MessageSticker ignored -> {
                return null;
            }
            case TdApi.MessageAnimatedEmoji ignored -> {
                return null;
            }
            case TdApi.MessageLocation ignored -> {
                return null;
            }
            case TdApi.MessageDocument ignored -> {
                return null;
            }
            case TdApi.MessageAnimation ignored -> {
                return null;
            }
            case TdApi.MessageVideo ignored -> {
                return null;
            }
            case TdApi.MessageVideoNote ignored -> {
                return null;
            }
            case null, default -> logger.warn("Unsupported content " + message.content);
        }
        Date date1 = new Date(message.date * 1000L);
        String date = new SimpleDateFormat("hh:mm").format(date1);

        String sender = getUserAsString(message, users);
        return String.format("%s %s: %s\n", date, sender, content);
    }

    private String getUserAsString(TdApi.Message message, Map<Long, TdApi.User> users) {
        String sender = "User";
        switch (message.senderId) {
            case TdApi.MessageSenderUser messageSenderUser -> {
                sender = Long.toString(messageSenderUser.userId);
                if (users.containsKey(messageSenderUser.userId)) {
                    TdApi.User userDetails = users.get(messageSenderUser.userId);
                    sender = Stream.of(userDetails.firstName, userDetails.lastName).filter(t -> !StringUtils.isEmpty(t)).distinct().toList().getFirst();
                }
            }
            case null, default -> logger.warn("Unsupported content {}", message.content);
        }
        return sender;
    }

    @Override
    public void onException(Throwable throwable) {
        logger.error(throwable);
    }
}

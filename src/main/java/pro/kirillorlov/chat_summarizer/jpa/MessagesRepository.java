package pro.kirillorlov.chat_summarizer.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
@Profile("tgbot")
public interface MessagesRepository extends JpaRepository<Messages, Long> {

}

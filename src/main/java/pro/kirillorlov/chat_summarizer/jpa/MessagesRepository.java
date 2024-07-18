package pro.kirillorlov.chat_summarizer.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
public interface MessagesRepository extends JpaRepository<Messages, Long> {

}

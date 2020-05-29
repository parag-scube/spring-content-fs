package gettingstarted;

import java.util.UUID;

import org.springframework.content.commons.renditions.Renderable;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.commons.search.Searchable;
import org.springframework.stereotype.Component;

@Component
public interface FileContentStore extends ContentStore<Document, String>, Searchable<String> {
}

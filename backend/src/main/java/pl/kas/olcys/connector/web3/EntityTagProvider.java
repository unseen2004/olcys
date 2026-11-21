package pl.kas.olcys.connector.web3;

import java.util.List;
import java.util.Optional;

public interface EntityTagProvider {
    Optional<EntityTag> findTag(String address, String network);
    List<EntityTag> getAllTags();
}

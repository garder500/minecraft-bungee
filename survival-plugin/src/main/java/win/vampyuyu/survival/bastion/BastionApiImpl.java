package win.vampyuyu.survival.bastion;

import java.util.Optional;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.BastionApi;

/** The published {@link BastionApi}. */
public final class BastionApiImpl implements BastionApi {
    private final BastionService service;

    public BastionApiImpl(BastionService service) {
        this.service = service;
    }

    @Override
    public boolean isKey(String itemId) {
        return itemId != null && service.cfg().ofKey(itemId).isPresent();
    }

    @Override
    public Optional<String> bastionOfKey(String itemId) {
        return itemId == null ? Optional.empty() : service.cfg().ofKey(itemId).map(BastionConfig.Bastion::id);
    }

    @Override
    public String enter(Player leader, String bastionId) {
        return service.enter(leader, bastionId);
    }
}

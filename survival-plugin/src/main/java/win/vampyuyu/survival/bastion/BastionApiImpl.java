package win.vampyuyu.survival.bastion;

import java.util.Optional;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.BastionApi;

/** The published {@link BastionApi}. */
public final class BastionApiImpl implements BastionApi {
    private final BastionService service;
    private final SealDoors seals;

    public BastionApiImpl(BastionService service, SealDoors seals) {
        this.service = service;
        this.seals = seals;
    }

    @Override
    public boolean isSeal(org.bukkit.block.Block block) {
        return seals != null && seals.siteAt(block) != null;
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

    @Override
    public boolean inArena(java.util.UUID player) {
        BastionRun run = service.runOf(player);
        return run != null && run.world != null && !run.closed;
    }
}

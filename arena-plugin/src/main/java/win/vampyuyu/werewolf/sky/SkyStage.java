package win.vampyuyu.werewolf.sky;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * The life cycle of one game's flying platform: take a slot of the sky world, build the platform for
 * the table's size, and (at the end) clear it and free the slot. Concurrent games get different slots.
 */
public final class SkyStage {
  private final SkyWorld sky;
  private final Supplier<SkyInstance> instance;

  public SkyStage(SkyWorld sky, Supplier<SkyInstance> instance) {
    this.sky = sky;
    this.instance = instance;
  }

  /** The production wiring: the shipped decor, blocks written on the owning region. */
  public static SkyStage forPlugin(Plugin plugin, SkyWorld sky) {
    SkyInstance[] cached = new SkyInstance[1];
    return new SkyStage(
        sky,
        () -> {
          synchronized (cached) {
            // Loaded on first use: a broken decor resource refuses games, it does not stop the plugin.
            if (cached[0] == null)
              cached[0] = SkyInstance.forPlugin(plugin, sky.world(), DecorConfig.loadDefault());
            return cached[0];
          }
        });
  }

  public SkyWorld world() {
    return sky;
  }

  /** Why a game of this size cannot be staged right now, or null. */
  public String problem(int players) {
    if (players < PlatformLayout.MIN_PLAYERS || players > PlatformLayout.MAX_PLAYERS)
      return "A Werewolf table seats "
          + PlatformLayout.MIN_PLAYERS
          + " to "
          + PlatformLayout.MAX_PLAYERS
          + " players, not "
          + players;
    if (sky.loaded() == null)
      return "The sky world "
          + SkyWorld.NAME
          + " is not loaded: restart the server, or pre-create it (see /ww check)";
    return null;
  }

  /**
   * Takes a slot and builds the platform. The handle is given to {@code remember} at once, before
   * the build finishes, so a game that stops half way can still {@link #close} it (which then also
   * frees the slot: until then it is the game's).
   */
  public CompletableFuture<PlatformHandle> open(
      int players, java.util.function.Consumer<PlatformHandle> remember) {
    SkySlot slot = sky.allocate();
    boolean remembered = false;
    try {
      SkyInstance blocks = instance.get();
      List<Location> seats = blocks.seatLocations(slot, players);
      Location spawn = blocks.spawnPoint(slot, players);
      PlatformLayout layout = PlatformLayout.of(players);
      PlatformHandle handle = new PlatformHandle(slot, players, seats, spawn, layout.platformRadius());
      remember.accept(handle);
      remembered = true;
      return blocks.build(slot, players).thenApply(v -> handle);
    } catch (RuntimeException e) {
      if (!remembered) sky.release(slot);
      return CompletableFuture.failedFuture(e);
    }
  }

  /** Removes the platform and frees its slot; harmless when called twice. */
  public CompletableFuture<Void> close(PlatformHandle handle) {
    if (handle == null) return CompletableFuture.completedFuture(null);
    CompletableFuture<Void> cleared;
    try {
      cleared = instance.get().clear(handle.slot());
    } catch (RuntimeException e) {
      cleared = CompletableFuture.failedFuture(e);
    }
    // A slot whose platform could not be cleared stays taken: reusing it would build over stale blocks.
    return cleared.thenRun(() -> sky.release(handle.slot()));
  }
}

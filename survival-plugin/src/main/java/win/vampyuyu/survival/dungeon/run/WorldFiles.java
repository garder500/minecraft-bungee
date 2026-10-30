package win.vampyuyu.survival.dungeon.run;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Safe handling of the world folders of dungeon runs. A folder is only ever deleted when its own name is exactly
 * {@code sb_dungeon_<digits>}, it is a real directory (never a symlink), and its normalised path lies below the
 * world container. Symbolic links inside are removed as links, never followed.
 */
public final class WorldFiles {
    private static final Pattern NAME = Pattern.compile("sb_dungeon_\\d+");

    private WorldFiles() {}

    /** True for {@code sb_dungeon_<digits>} only. */
    public static boolean isDungeonWorldName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** Dungeon world folders found below {@code container} (at most {@code depth} levels deep), symlinks ignored. */
    public static List<Path> findDungeonFolders(Path container, int depth) {
        List<Path> out = new ArrayList<>();
        if (container == null || !Files.isDirectory(container, LinkOption.NOFOLLOW_LINKS)) {
            return out;
        }
        try {
            Files.walkFileTree(container, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), depth,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(container) && isDungeonWorldName(dir.getFileName().toString())
                                && !attrs.isSymbolicLink()) {
                            out.add(dir);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
        } catch (IOException ignored) {
            // an unreadable container only means nothing to clean
        }
        return out;
    }

    /**
     * Deletes a dungeon world folder. Returns false (and deletes nothing) when the safety checks fail; true when
     * the folder is gone afterwards.
     */
    public static boolean deleteWorldFolder(Path container, Path folder) {
        if (container == null || folder == null || folder.getFileName() == null
                || !isDungeonWorldName(folder.getFileName().toString())) {
            return false;
        }
        Path root = container.toAbsolutePath().normalize();
        Path target = folder.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            return false;
        }
        if (Files.isSymbolicLink(target)) {
            return false;
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); // a symlink is deleted itself, its target is untouched
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return !Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        }
        return !Files.exists(target, LinkOption.NOFOLLOW_LINKS);
    }
}

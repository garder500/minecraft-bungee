// Redefines already-loaded classes in a JVM that listens for JDWP. Run by scripts/hotswap.py:
//   java scripts/Redefine.java <port> <classes-dir> <relative/Class.class>...
// Stock HotSpot swaps method bodies only: a changed field/method set (a new or removed lambda counts), a
// new supertype or a changed static initializer refuses the whole batch and needs a restart. Classes the
// JVM has not loaded yet are printed as "SKIPPED <class>": they will load from the old boot jar.
import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import java.nio.file.*;
import java.util.*;

public class Redefine {
    public static void main(String[] args) throws Exception {
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(args[0]);
        VirtualMachine vm = connector.attach(arguments);
        if (!vm.canRedefineClasses()) { System.out.println("FAIL: this JVM cannot redefine classes"); System.exit(2); }
        // One call: HotSpot applies every class or none, so a caller never meets a method added to a class
        // that was refused. Classes the JVM has not loaded are reported: they still carry the old code.
        Map<ReferenceType, byte[]> batch = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        int skipped = 0;
        try {
            for (int i = 2; i < args.length; i++) {
                String name = args[i].replaceAll("\\.class$", "").replace('/', '.');
                List<ReferenceType> loaded = vm.classesByName(name);
                if (loaded.isEmpty()) { skipped++; System.out.println("SKIPPED " + args[i]); continue; }
                batch.put(loaded.get(0), Files.readAllBytes(Path.of(args[1], args[i])));
                names.add(name);
            }
            if (!batch.isEmpty()) vm.redefineClasses(batch);
        } catch (UnsupportedOperationException | LinkageError | VMOutOfMemoryException e) {
            System.out.println("NEEDS RESTART, nothing applied (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            System.out.println("changed classes: " + String.join(", ", names));
            vm.dispose();
            System.exit(1);
        }
        vm.dispose();
        System.out.println("redefined " + batch.size() + ", not loaded " + skipped);
        System.exit(0);
    }
}

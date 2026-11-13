package relay;

import java.io.Console;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;

public final class Main {
    private static final String HELP = """
        Relay IT service desk
          --host HOST       Bind address (default 127.0.0.1)
          --port PORT       Listen port (default 8000)
          --db PATH         SQLite database (default data/itsm.sqlite3)
          --demo            Seed demonstration accounts when creating a database
          --no-demo         Disable demo seed and sign-in shortcuts
          --secure-cookie   Require HTTPS when sending the session cookie
          --create-admin    Create the first administrator interactively and exit
          --help            Show this help
        """;

    private Main() {}

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        Path database = Path.of("data", "itsm.sqlite3");
        int port = 8000;
        Boolean demoFlag = null;
        boolean createAdmin = false;
        boolean secureCookie = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> { System.out.print(HELP); return; }
                case "--port" -> {
                    port = Integer.parseInt(argument(args, ++i));
                    if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be 0 through 65535.");
                }
                case "--host" -> host = argument(args, ++i);
                case "--db" -> database = Path.of(argument(args, ++i));
                case "--demo" -> demoFlag = true;
                case "--no-demo" -> demoFlag = false;
                case "--secure-cookie" -> secureCookie = true;
                case "--create-admin" -> createAdmin = true;
                default -> throw new IllegalArgumentException("Unknown option " + args[i] + ". See --help.");
            }
        }
        InetAddress address = InetAddress.getByName(host);
        boolean demo = !createAdmin && (demoFlag != null ? demoFlag : address.isLoopbackAddress());
        Desk desk = new Desk(database, demo);
        if (createAdmin) {
            createAdministrator(desk);
            return;
        }
        RelayServer server = new RelayServer(new InetSocketAddress(address, port), desk, demo, secureCookie);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "relay-shutdown"));
        server.start();
        String displayHost = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        System.out.printf("Relay is listening at http://%s:%d%n", displayHost, server.port());
    }

    private static String argument(String[] args, int index) {
        if (index >= args.length) throw new IllegalArgumentException("Missing value after " + args[index - 1]);
        return args[index];
    }

    private static void createAdministrator(Desk desk) throws Exception {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Open a terminal to use --create-admin.");
        String name = console.readLine("Administrator name: ");
        String email = console.readLine("Administrator email: ");
        char[] password = console.readPassword("Password: ");
        char[] confirm = console.readPassword("Confirm password: ");
        try {
            if (password == null || !Arrays.equals(password, confirm)) throw new IllegalArgumentException("Passwords do not match.");
            desk.bootstrapAdmin(name, email, new String(password));
            System.out.println("Administrator created.");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirm != null) Arrays.fill(confirm, '\0');
        }
    }
}

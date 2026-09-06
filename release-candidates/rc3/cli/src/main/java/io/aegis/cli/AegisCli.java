package io.aegis.cli;

public final class AegisCli {
    private AegisCli() {}
    public static void main(String[] args) {
        if (args.length == 1 && "health".equals(args[0])) {
            System.out.println("AEGIS-CLI 0.1.0-rc1 OK");
            return;
        }
        System.out.println("Usage: aegis health");
    }
}

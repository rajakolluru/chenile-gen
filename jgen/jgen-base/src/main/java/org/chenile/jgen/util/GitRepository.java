package org.chenile.jgen.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Turns a generated folder into a local Git repository with one commit, using the {@code git} command. The
 * {@code git-initializer} pipeline step uses it when the input map has {@code initGit=true}. It never invents a commit author: if Git has no user name or e-mail
 * configured, the repository is initialised and the files are staged, and the result says what to run.
 */
public final class GitRepository {
    private GitRepository() { }

    /**
     * @param initialised a repository now exists (branch main)
     * @param committed   the files are committed
     * @param nested      the folder is inside another Git working tree (the new repository is separate from it)
     * @param detail      why something did not happen, or null
     */
    public record Result(boolean initialised, boolean committed, boolean nested, String detail) { }

    public static Result initialise(Path folder, String commitMessage) {
        if (!Files.isDirectory(folder)) return new Result(false, false, false, "No such folder " + folder);
        if (Files.exists(folder.resolve(".git")))
            return new Result(false, false, false, folder + " is already a Git repository; left as it is");
        Run version = run(folder, "git", "--version");
        if (version.exitCode != 0) return new Result(false, false, false, "git is not installed or not on the PATH");

        Path parent = folder.toAbsolutePath().getParent();
        boolean nested = parent != null && run(parent, "git", "rev-parse", "--is-inside-work-tree").exitCode == 0;

        for (List<String> command : List.of(
                List.of("git", "init", "-q"),
                List.of("git", "symbolic-ref", "HEAD", "refs/heads/main"),
                List.of("git", "add", "-A"))) {
            Run step = run(folder, command.toArray(String[]::new));
            if (step.exitCode != 0)
                return new Result(command.get(1).equals("add"), false, nested,
                        String.join(" ", command) + " failed: " + step.output.trim());
        }
        Run commit = run(folder, "git", "commit", "-q", "-m", commitMessage);
        if (commit.exitCode != 0)
            return new Result(true, false, nested, "git commit failed (" + commit.output.trim()
                    + "). The files are staged: set user.name and user.email, then run git commit -m \""
                    + commitMessage + "\" in " + folder);
        return new Result(true, true, nested, null);
    }

    /** Adds a remote to an existing repository; returns null on success, or why it failed. */
    public static String addRemote(Path folder, String name, String url) {
        Run run = run(folder, "git", "remote", "add", name, url);
        return run.exitCode == 0 ? null : "git remote add " + name + " " + url + " failed: " + run.output.trim();
    }

    private record Run(int exitCode, String output) { }

    private static Run run(Path directory, String... command) {
        try {
            Process process = new ProcessBuilder(new ArrayList<>(List.of(command)))
                    .directory(directory.toFile()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Run(-1, String.join(" ", command) + " timed out");
            }
            return new Run(process.exitValue(), output);
        } catch (IOException e) {
            return new Run(-1, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Run(-1, "interrupted");
        }
    }
}

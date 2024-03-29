package org.the4thlaw.bm3;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import ch.qos.logback.classic.Level;

public class CLI  {

    private static void setLoggingLevel(Level level) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }

    public static void main(String[] args) throws IOException {
        Options options = new Options();

        options.addRequiredOption("i", "input", true, "Input directory")
                .addRequiredOption("o", "output", true, "Output directory")
                .addOption("p", "playlist-dir", true, "Disable automatic playlist detection and use a specific directory")
                .addOption("b", "use-backslashes", false, "Use backslashes for separators in the playlist files. Known to work on BMW iDrive 5.x."
                + " Default is to use slashes, which is known to work on BMW iDrive 8.x and VLC for Android.")
                .addOption("s", "sync", false, "Synchronize changes rather than copying everything")
                .addOption("d", "dry-run", false, "Don't change the target directory or files, only perform a trial run")
                .addOption("q", "quiet", false, "Quiet mode, outputs only status and warning messages")
                .addOption("v", "verbose", false, "Verbose mode, outputs debug information");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(CLI.class.getName(), options);
            System.exit(1);
        }

        File inputDirectory = new File(cmd.getOptionValue("i"));
        File outputDirectory = new File(cmd.getOptionValue("o"));
        File playlistDirectory = null;
        if (cmd.hasOption("p")) {
            playlistDirectory = new File(cmd.getOptionValue("p"));
        }
        boolean isUseSlashes = !cmd.hasOption("b");
        boolean isSync = cmd.hasOption("s");
        boolean isDryRun = cmd.hasOption("d");

        if (cmd.hasOption("v")) {
            setLoggingLevel(Level.DEBUG);
        } else if (cmd.hasOption("q")) {
            setLoggingLevel(Level.WARN);
        }

        new FileProcessor(inputDirectory, outputDirectory, playlistDirectory, isUseSlashes, isSync, isDryRun)
                .process(new CLIProgressReporter());
    }
}

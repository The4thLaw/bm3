package org.the4thlaw.bm3;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class CLI implements ProgressReporter {

    public static void main(String[] args) throws IOException {
        Options options = new Options();

        options.addRequiredOption("i", "input", true, "Input directory")
                .addRequiredOption("o", "output", true, "Output directory")
                .addOption("s", "sync", false, "Synchronize changes rather than copying everything");

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
        boolean isSync = cmd.hasOption("s");

        CLI instance = new CLI();

        new FileProcessor(inputDirectory, outputDirectory, isSync)
                .process(instance);
    }

    private String status = "";
    private boolean unknown;
    private int step;
    private int total;
    private boolean trackSub;
    private int subStep;
    private int subTotal;

    private void printProgress() {
        String progress = "";
        if (unknown) {
            progress = status;
        } else {
            int numMainDigits = String.valueOf(total).length();
            String mainFmt = "% " + numMainDigits + "d";
            StringBuilder sb = new StringBuilder(
                    String.format("%s [" + mainFmt + "/" + mainFmt + "]", status, step, total));
            if (trackSub) {
                int numSubDigits = String.valueOf(subTotal).length();
                String subFmt = "% " + numSubDigits + "d";
                sb.append(String.format(" [" + subFmt + "/" + subFmt + "]", subStep, subTotal));
            }
            progress = sb.toString();
        }
        progress += "\r";
        System.err.print(progress);
    }

    @Override
    public void setStatus(String status) {
        this.status = status;
        printProgress();
    }

    @Override
    public void setProgressUnknown(boolean unknown) {
        this.unknown = unknown;
        printProgress();
    }

    @Override
    public void setStep(int step) {
        this.step = step;
        printProgress();
    }

    @Override
    public void setTotal(int total) {
        this.total = total;
        printProgress();
    }

    @Override
    public void reportError(String message) {
        System.err.println("Error: " + message);
        printProgress();
    }

    @Override
    public void setSubStep(int step) {
        this.subStep = step;
        printProgress();
    }

    @Override
    public void setSubTotal(int total) {
        this.trackSub = true;
        this.subTotal = total;
    }

    @Override
    public void endSubTracking() {
        this.trackSub = false;
        printProgress();
    }

}

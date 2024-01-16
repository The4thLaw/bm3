package org.the4thlaw.bm3;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bhowell2.debouncer.Debouncer;

public class CLIProgressReporter implements ProgressReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CLIProgressReporter.class);

    private final Debouncer<String> debouncer;
    private String status = "";
    private boolean unknown;
    private int step;
    private int total;
    private boolean trackSub;
    private int subStep;
    private int subTotal;
    private int maxOutputLength = 0;
    private String lastMessage = "";

    public CLIProgressReporter() {
        debouncer = new Debouncer<>( 1);
    }

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

        // Prevent a short status string from leaving behind part of a long one
        if (progress.length() < maxOutputLength) {
            progress = String.format("%-" + maxOutputLength + "s", progress);
        } else {
            maxOutputLength = progress.length();
        }

        lastMessage = progress;
        String progressToLog = progress + "\r";
        debouncer.addRunLast(10, TimeUnit.MILLISECONDS, "ConsoleProgressReport", k -> {
            System.err.print(progressToLog);
        });
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

    @Override
    public void endTracking() {
        // Shut the debouncer down
        debouncer.shutdown();
        // Print the last message which could be lost due to the debouncer shutdown and add a final newline to preserve it
        // (shutdownAndAwaitTermination doesn't seem to do that)
        System.err.println(lastMessage);
    }
}

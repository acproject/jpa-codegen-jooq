package com.owiseman.jpa.util;


import java.util.stream.IntStream;

public class ProcessingBar {
    private int totalTasks;
    private int completedTasks;
    private int barLength;

    public ProcessingBar(int barLength, int totalTasks) {
        this.barLength = barLength;
        this.totalTasks = totalTasks;
        this.completedTasks = 0;
    }

    /**
     * Update the progress bar
     * @param increment
     */
    public void update(int increment) {
        completedTasks += increment;
        printProgress();
    }

    /**
     * print the progress bar
     */
    private void printProgress() {
        double process = (double) completedTasks / totalTasks;
        int filledLength = (int) (process * barLength);

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("=");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");
        bar.append(String.format("%.2f%%", process * 100));
        System.out.print("\r" + bar.toString());
    }

    public void finish() {
        completedTasks = totalTasks;
        printProgress();
        System.out.println("\nTask completed!");
    }

    /**
     * Test the progress bar
     * @param args
     */
    private static void test(String[] args) {
        int totalTasks = 100;
        ProcessingBar bar = new ProcessingBar(50, totalTasks);

        IntStream.range(0, totalTasks).forEach(i -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            bar.update(1);
        });
        bar.finish();
    }

}

package de.jade_hs.afe.AcousticFeatureExtraction;

import android.content.Context;
import android.util.Log;

import org.threeten.bp.Instant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class to implement producers (output), consumers (input) and conducers (in- and output).
 * Data is transferred using queues.
 */

abstract class Stage extends TreeSet {

    final static String LOG = "Stage";

    static Context context;

    final int timeout = 1000; // in ms, wait this long to receive data before stopping a stage.

    static Instant startTime;
    static int samplingrate;
    static int channels;

    boolean hasInput = true; // default mode, use false to bypass receive() & rebuffer()

    private Thread thread;
    private LinkedBlockingQueue<float[][]> inQueue;
    private Set<LinkedBlockingQueue> outQueue = new HashSet<>();

    Stage inStage;
    ArrayList<Stage> consumerSet = new ArrayList<>();

    // params to set via constructor
    int id, blockSize, hopSize, blockSizeOut, hopSizeOut;

    public Stage(HashMap parameter) {

        id = Integer.parseInt((String) parameter.get("id"));

        Log.d("Stage", " Constructing stage ID " + id + ".");

        if (parameter.get("blocksize") == null)
            blockSize = 400;
        else
            blockSize = Integer.parseInt((String) parameter.get("blocksize"));

        if (parameter.get("hopsize") == null)
            hopSize = blockSize;
        else
            hopSize = Integer.parseInt((String) parameter.get("hopsize"));

        if (parameter.get("blockout") == null)
            blockSizeOut = blockSize;
        else
            blockSizeOut = Integer.parseInt((String) parameter.get("blockout"));

        if (parameter.get("hopout") == null)
            hopSizeOut = hopSize;
        else
            hopSizeOut = Integer.parseInt((String) parameter.get("hopout"));

    }


    void start() {

        Log.d("Stage", " Starting stage ID " + id + " (input: " + hasInput + " ).");

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                if (hasInput)
                    rebuffer();
                else
                    process(null);
            }
        };

        thread = new Thread(runnable);
        thread.start();

        // call start() of attached consumer
        for (Stage consumer : consumerSet) {
            consumer.start();
        }

    }


    void stop() {
        thread.interrupt();
    }


    void rebuffer() {

        boolean abort = false;
        int samples = 0;
        int channels = 2;
        float[][] buffer = new float[channels][blockSize];

        Log.d(LOG, id + ": Start processing");

        while (!Thread.currentThread().isInterrupted() & !abort) {

            float[][] dataIn;

            dataIn = receive();

            if (dataIn != null) {

                int k = 0;
                int m = 0;

                try {
                    for (k = 0; k < dataIn[0].length; k++) {
                        for (m = 0; m < dataIn.length; m++) {
                            buffer[m][samples] = dataIn[m][k];
                        }
                        samples++;

                        if (samples >= blockSize) {

                            process(buffer);

                            samples = blockSize - hopSize;

                            for (int i = 0; i < dataIn.length; i++) {
                                System.arraycopy(buffer[i], hopSize, buffer[i], 0, blockSize - hopSize);
                            }
                        }
                    }
                } catch(Exception e) {
                    System.out.println("<-------------------");
                    System.out.println("buffer: " + buffer.length + "|" + buffer[0].length + " dataIn: " + dataIn.length + "|" + dataIn[0].length);
                    System.out.println(e.toString());
                    System.out.println("---> Line: " + e.getStackTrace()[0].getLineNumber());
                    System.out.println("--->" + samples + " | " + m + " | " + k);
                    System.out.println("ID: " + id);
                }

            } else {
                abort = true;
            }
        }

        Log.d(LOG, id + ": Stopped consuming");

    }


    float[][] receive() {

        try {

            //Log.d("Stage", "ID: " + id + " | receive()");
            return inQueue.poll(timeout, TimeUnit.MILLISECONDS);

            // rebuffer and call processing here?

        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.d("Stage", "ID: " + id + " | No elements in queue for " + timeout + " ms. Empty?");
        }

        return null;
    }


    void send(float[][] data) {

        try {
            for (LinkedBlockingQueue queue : outQueue) {
                queue.put(data);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    void addConsumer(Stage consumer) {

        // create new queue for consumer
        LinkedBlockingQueue queue = new LinkedBlockingQueue<>();

        // set queue in new consumer
        consumer.setInQueue(queue);

        // set input/parent stage so we have access to parameters
        // TODO: is accessible another way? Maybe define the whole ArrayList as class variable?
        //  Just passing the stage is simpler, though.
        consumer.setInStage(this);

        // add queue to output queues of this stage
        outQueue.add(queue);

        // add new consumer to consumers for this stage
        consumerSet.add(consumer);
    }

    void setInQueue(LinkedBlockingQueue inQueue) {

        this.inQueue = inQueue;
    }

    void setInStage(Stage inStage) {

        this.inStage = inStage;
    }

    protected abstract void process(float[][] buffer);

    protected void cleanup() {}

}

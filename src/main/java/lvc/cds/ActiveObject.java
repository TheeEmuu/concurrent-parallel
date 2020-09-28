package lvc.cds;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ActiveObject {
    private BlockingQueue<AOTask> jobs;
    private Thread workerThread;
    private boolean shouldTerminate;
    private Scheduler scheduler;

    private static class AOTask<R> {
        Runnable r;
        Supplier<R> s;
        Future<R> future;

        AOTask(Supplier<R> s, Future<R> future) {
            this.r = null;
            this.s = s;
            this.future = future;
        }

        AOTask(Runnable r, Future future) {
            this.r = r;
            this.s = null;
            this.future = future;
        }

        void runAndComplete() {
            if (r != null && s == null) {
                r.run();
                if (future != null) {
                    future.complete(null);
                }
            } else if (r == null && s != null) {
                R result = s.get();
                if (future != null) {
                    future.complete(result);
                }
            }
        }
    }

    public ActiveObject() {
        jobs = new LinkedBlockingQueue<>();
        workerThread = new Thread(this::worker); // this is how you pass an instance method.
        workerThread.start();
        shouldTerminate = false;
    }

    /// initialize an empty queue and our background thread.
    public ActiveObject(Scheduler s) {
        this.scheduler = s;
        jobs = new LinkedBlockingQueue<>();
        workerThread = new Thread(this::worker); // this is how you pass an instance method.
        workerThread.start();
        shouldTerminate = false;
    }

    /**
     * enqueue a task. This can execute any code
     */

    public Future<Void> enqueue(Runnable r) {
        // place r in the queue. Notify the background thread.
        Future<Void> future = new Future<>(this);
        try {
            jobs.put(new AOTask<Void>(r, future));
        } catch (InterruptedException e) {
        }
        return future;

    }

    public <R> Future<R> enqueue(Supplier<R> s) {
        Future<R> future = new Future<>(this);
        try {
            jobs.put(new AOTask<R>(s, future));
        } catch (InterruptedException e) {
        }
        return future;
    }

    public <R> void enqueue(Supplier<R> s, Future<R> future) {
        try {
            jobs.put(new AOTask<R>(s, future));
        } catch (InterruptedException e) {
        }
    }

    /**
     * tell the worker thread to gracefully terminate. We have a choice to make: +
     * abort abruptly, killing whatever's in flight + finish the current job, then
     * killing the thread, even if + more jobs are queued + finish all jobs on the
     * queue at this moment, then terminate.
     *
     * We can play with all three.
     *
     * This version terminates the thread without processing the remaining job
     * (though if there is a job in process when terminate is called, it will
     * complete).
     */
    public void terminate() {
        shouldTerminate = true;
    }

    /**
     * Run a loop to process our queued jobs until someone terminates us.
     */
    public void worker() {
        // run a loop to process jobs on the queue. When the queue is empty,
        // sleep. When the queue has contents, pop them off and run.
        while (true) {
            AOTask r = null;
            while (!shouldTerminate) {
                try {
                    r = jobs.poll(1, TimeUnit.MINUTES);
                } catch (InterruptedException e) {}
            
                r.runAndComplete();
            }
            return;
        }
    }

    // similar to worker(). This is designed to be called from
    // future.get() -- we keep executing tasks on the queue until the
    // future is completed.
    public void workUntilCompleted(Future future) {
        while (!future.isComplete()) {
            AOTask r = null;
            while (!shouldTerminate && !future.isComplete()) {
                try { r = jobs.poll(1, TimeUnit.MINUTES); }
                catch (InterruptedException e) { }
            }
            if (shouldTerminate || future.isComplete())
                return;

            r = jobs.poll();
            r.runAndComplete();
        }
	}

    Thread currentThread() {
        if (workerThread != null && workerThread.isAlive())
            return workerThread;
        else
            return null;
    }

    Scheduler getScheduler() {
        return scheduler;
    }
}

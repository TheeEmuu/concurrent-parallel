package lvc.cds;

import java.util.function.Supplier;

public class Scheduler {
    private ActiveObject[] workers;
    private int current;

    public Scheduler(int num) {
        workers = new ActiveObject[num];
        for (int i=0; i<num; ++i) {
            workers[i] = new ActiveObject(this);
        }

        current = 0;

    }

    public Future<Void> schedule(Runnable r) {
        var fut = workers[current].enqueue(r);
        current = (current+1) % workers.length;
        return fut;
    }

    public <R> Future<R> schedule(Supplier<R> r) {
        var fut = workers[current].enqueue(r);
        current = (current + 1) % workers.length;
        return fut;
    }

    public <R> void schedule(Supplier<R> r, Future<R> f) {
        workers[current].enqueue(r, f);
        current = (current+1) % workers.length;
    }

    public void terminate() {
        for (ActiveObject ao : workers)
            ao.terminate();
    }

    boolean isAWorker(Thread th) {
        for (var w : workers) {
            if (th == w.currentThread()) {
                return true;
            }
        }
        return false;
    }

    void workUntilCompleted(Thread th, Future f) {
        for (var w : workers) {
            if (th == w.currentThread()) {
                w.workUntilCompleted(f);
                return;
            }
        }
    }

}

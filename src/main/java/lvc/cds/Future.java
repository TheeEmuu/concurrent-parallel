package lvc.cds;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/*
synchronization logic.
We of course aim to avoid locking wherever possible.
I rewrote this to show you an example of some of the tools.

One of the reasons Future is a successful pattern is that, while it provides
shared memory, it's a very restricted form of sharing. It's a relationship
between two threads -- the client thread that requests some work, and the
worker thread that does the work.

The client thread creates the future, and both threads store it. The worker
thread completes the future at some point, and the client thread might call get()
to retrieve the future's value. complete and get might happen in any order, and we
also toss then() in there for fun.

Because of these restriction, we can use a little logic to avoid (most) locking.

The Future can be in one of three primary states: NEW, COMPLETING, and COMPLETED.
The future is born in state NEW. When complete() is called, we transition to COMPLETING
until the result is set, and then to COMPLETED. We never make any other transitions
between these states. So, we can use this to prevent data races without locks.

Similarly, the continuation data (function and future) can be in the same three states,
transitioning from NEW to COMPLETING to COMPLETED in .then().

To make this work, we employ "atomic" values. An AtomicBoolean, for instance, is
a boolean that can be updated without locking -- the atomic will ensure that the
update is done in a thread-safe way.

In particular, we use the compareAndSet() method several times. Read about that --
we'll talk about it in class.

The checks of these state variables are enough to ensure that we have no races. That's
not an obviously-true statement. The result is considerably faster than a solution
using locks.
*/

public class Future<R> {
    private static int rootId = 0;
    private static final boolean DEBUG = false;

    private static final int NEW = 0;
    private static final int COMPLETING = 1;
    private static final int COMPLETED = 2;

    private int id;
    private AtomicInteger state;
    private AtomicInteger continuationState;
    private R result;
    private Function continuation;
    private Future continuationFuture;
    // the active object that our task is currently scheduled on
    private ActiveObject ao;
    private Scheduler scheduler;

    Future(ActiveObject ao) {
        this.id = rootId++;
        this.ao = ao;
        this.scheduler = ao.getScheduler();
        result = null;
        state = new AtomicInteger(NEW);
        continuationState = new AtomicInteger(NEW);
        continuation = null;
        continuationFuture = null;
        diag("ctor", "");
    }

    public boolean isComplete() {
        return state.get() == COMPLETED;
    }

    public R get() {
        diag("get", "");
        if (state.get() < COMPLETED) {
            if (scheduler.isAWorker(Thread.currentThread())) {
                diag("get", "waiting in a scheduler thread");
                // we were called on the AO that's handling this task.
                scheduler.workUntilCompleted(Thread.currentThread(), this);
            } else {
                diag("get2", "waiting in a different thread");
                // another thread will complete the task. Wait for it.
                synchronized (this) {
                    while (state.get() < COMPLETED) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        }
        return result;
    }

    public <R2> Future<R2> then(Function<R, R2> c) {
        diag("then", "");
        // can't call this more than once.
        if (continuationState.compareAndSet(NEW, COMPLETING)) {
            continuation = c;
            Future<R2> cf = new Future<>(ao);
            continuationFuture = cf;
            if (continuationState.compareAndSet(COMPLETING, COMPLETED)) {
                if (state.get() == COMPLETED) {
                    scheduleContinuation();
                }
                return cf;
            }
            else {
                throw new IllegalStateException("transition completionState COMPLETING to COMPLETED");
            }
        } else {
            throw new IllegalStateException("transition completionState NEW to COMPLETING");
        }
    }

    void complete(R result) {
        diag("complete", "");
        if (state.compareAndSet(NEW, COMPLETING)) {
            this.result = result;
            scheduleContinuation();
            if (state.compareAndSet(COMPLETING, COMPLETED)) {
                diag("complete", "someone might be waiting. Notify ");
                synchronized (this) {
                    this.notifyAll();
                }
            }
            else {
                throw new IllegalStateException("transition from COMPLETING to COMPLETED");
            }
        }
        else {
            throw new IllegalStateException("transition from NEW to COMPLETING");
        }
    }

    private void scheduleContinuation() {
        diag("scheduleContinuation", "");
        // Note: this private method is only called when state.get() == COMPLETED
        if (continuationState.get() == COMPLETED) {
            scheduler.schedule( () -> continuation.apply(result), continuationFuture);
        }
    }

    // diagnostics -- a cheezy logging tool. Change DEBUG to true to enable output
    private void diag(String mName, String msg) {
        if (DEBUG) {
            System.out.println(id + ": future::" + mName + ", state=" + state.get());

            if (msg != null && !msg.equals("")) {
                System.out.println("\t\t-->" + msg);
            }
        }
    }
}

import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

interface Counter {
    void increment();
    int get();
}

class SerialCounter implements Counter {
    int count;
    public SerialCounter() {
	count = 0;
    }

    public void increment() {
	count++;
    }

    public int get() {
	return count;
    }
}

class AtomicCounter implements Counter {
    AtomicInteger count;
    public AtomicCounter() {
	count = new AtomicInteger(0);
    }

    public void increment() {
	count.getAndIncrement();
    }

    public int get() {
	return count.get();
    }
}

interface Histogram {    
    public void increment(int i);
    public int get(int i);
}

class CoarseLockingHistogram implements Histogram {
    Counter[] counters;
    ReentrantLock lock;
    public CoarseLockingHistogram(int n) {
	counters = new Counter[n];
	for (int i = 0; i < n; i++) {
	    counters[i] = new SerialCounter();
	}
    }
    public void increment (int i) {
	lock.lock();
	counters[i].increment();
	lock.unlock();
    }
    public int get(int i) {
	lock.lock();
	int val = counters[i].get();
	lock.unlock();
	return val;
    }
}


class FineLockingHistogram implements Histogram {
    Counter[] counters;
    ReentrantLock[] lock;
    public FineLockingHistogram(int n) {
	counters = new Counter[n];
	lock = new ReentrantLock[n];
	for (int i = 0; i < n; i++) {
	    counters[i] = new SerialCounter();
	    lock[i] = new ReentrantLock();
	}
    }
    public void increment (int i) {
	lock[i].lock();
	counters[i].increment();
	lock[i].unlock();
    }
    public int get(int i) {
	lock[i].lock();
	int val = counters[i].get();
	lock[i].unlock();
	return val;
    }
}

class StripedLockingHistogram implements Histogram {
    Counter[] counters;
    ReentrantLock[] lock;
    public StripedLockingHistogram(int n, int nThreads) {
	counters = new Counter[n];
	lock = new ReentrantLock[nThreads];
	for (int i = 0; i < n; i++) {
	    counters[i] = new SerialCounter();
	}
	for (int i = 0; i < nThreads; i++) {
	    lock[i] = new ReentrantLock();
	}
    }
    public void increment (int i) {
	lock[i % lock.length].lock();
	counters[i].increment();
	lock[i % lock.length].unlock();
    }
    public int get(int i) {
	lock[i % lock.length].lock();
	int val = counters[i].get();
	lock[i % lock.length].unlock();
	return val;
    }
}

class AtomicHistogram implements Histogram {
    Counter[] counters;

    public AtomicHistogram(int n, int nThreads) {
	counters = new Counter[n];

	for (int i = 0; i < n; i++) {
	    counters[i] = new AtomicCounter();
	}
    }
    public void increment (int i) {

	counters[i].increment();

    }
    public int get(int i) {

	int val = counters[i].get();

	return val;
    }
}

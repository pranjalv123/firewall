import java.util.concurrent.locks.*;

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


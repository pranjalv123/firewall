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

class SerialHistogram implements Histogram {
    Counter[] counters;
    public SerialHistogram(int n) {
	counters = new Counter[n];
	for (int i = 0; i < n; i++) {
	    counters[i] = new SerialCounter();
	}
    }
    public void increment (int i) {
	counters[i].increment();
    }
    public int get(int i) {
	return counters[i].get();
    }
}

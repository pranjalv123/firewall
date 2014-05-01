class WaitFreeQueue {
    volatile int head = 0, tail = 0;
    Packet[] items;
    public WaitFreeQueue(int capacity) {
	items = new Packet[capacity];
	head = 0;
	tail = 0;
    }
    public void enq(Packet x) throws FullException {
	if (tail - head == items.length) {
	    throw new FullException();
	}
	items[tail % items.length] = x;
	tail++;
    }
    public Packet deq() throws EmptyException {
	if (tail - head == 0) {
	    throw new EmptyException();
	}
	Packet x = items[head % items.length];
	head++;
	return x;
    }
}

class FullException extends Exception {
}

class EmptyException extends Exception {
}

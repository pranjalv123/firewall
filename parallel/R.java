import java.util.concurrent.locks.*;
interface R {
    public void processConfigPacket(Config conf);
    public boolean allowPacket(Header h);
    public void lockWrite(int address);
    public void unlockWrite(int address);
    public void lockRead(int address);
    public void unlockRead(int address);
}


class CoarseLockingR implements R {
    IntervalSkipList[] dests;
    ReadWriteLock lock;
    public CoarseLockingR(int a) {
	dests = new IntervalSkipList[a];
	for (int i = 0; i < a; i++) {
	    dests[i] = new IntervalSkipList(a+1);
	}
	lock = new ReentrantReadWriteLock();
    }
    
    public void processConfigPacket(Config conf) {
	dests[conf.address].setRange(conf.addressBegin, conf.addressEnd, conf.acceptingRange);
    }

    public boolean allowPacket(Header h) {
	boolean rv = dests[h.dest].get(h.source);
	return rv;
    }
    public void lockWrite(int address) {
	lock.writeLock().lock();
    }
    public void unlockWrite(int address) {
	lock.writeLock().unlock();
    }
    public void lockRead(int address) {
	lock.readLock().lock();
    }
    public void unlockRead(int address) {
	lock.readLock().unlock();
    }
}

class FineLockingR implements R {
    IntervalSkipList[] dests;
    ReadWriteLock[] locks;
    public FineLockingR(int a) {
	dests = new IntervalSkipList[a];
	locks = new ReentrantReadWriteLock[a];
	for (int i = 0; i < a; i++) {
	    dests[i] = new IntervalSkipList(a+1);
	    locks[i] = new ReentrantReadWriteLock();
	}
    }
    
    public void processConfigPacket(Config conf) {
	dests[conf.address].setRange(conf.addressBegin, conf.addressEnd, conf.acceptingRange);	
    }

    public boolean allowPacket(Header h) {
	boolean rv = dests[h.dest].get(h.source);
	return rv;
    }
    public void lockWrite(int address) {
	locks[address].writeLock().lock();
    }
    public void unlockWrite(int address) {
	locks[address].writeLock().unlock();
    }
    public void lockRead(int address) {
	locks[address].readLock().lock();
    }
    public void unlockRead(int address) {
	locks[address].readLock().unlock();
    }
}

class StripedLockingR implements R {
    IntervalSkipList[] dests;
    ReadWriteLock[] locks;
    public StripedLockingR(int a, int nWorkers) {
	dests = new IntervalSkipList[a];
	locks = new ReentrantReadWriteLock[nWorkers];
	for (int i = 0; i < a; i++) {
	    dests[i] = new IntervalSkipList(a+1);
	}
	for (int i = 0; i < nWorkers; i++) {
	    locks[i] = new ReentrantReadWriteLock();
	}
    }
    
    public void processConfigPacket(Config conf) {
	dests[conf.address].setRange(conf.addressBegin, conf.addressEnd, conf.acceptingRange);	
    }

    public boolean allowPacket(Header h) {
	boolean rv = dests[h.dest].get(h.source);
	return rv;
    }

    public void lockWrite(int address) {
	locks[address % locks.length].writeLock().lock();
    }
    public void unlockWrite(int address) {
	locks[address % locks.length].writeLock().unlock();
    }
    public void lockRead(int address) {
	locks[address % locks.length].readLock().lock();
    }
    public void unlockRead(int address) {
	locks[address % locks.length].readLock().unlock();
    }
}

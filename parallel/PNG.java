import java.util.concurrent.locks.*;

interface PNG {
    public void processConfigPacket(Config conf);
    public boolean allowPacket(Header h);
    public void lockWrite(int address);
    public void unlockWrite(int address);
    public void lockRead(int address);
    public void unlockRead(int address);
}


class CoarseLockingPNG implements PNG {
    boolean[] allowed;
    ReadWriteLock lock;
    public CoarseLockingPNG(int a) {
	allowed = new boolean[a];
	lock = new ReentrantReadWriteLock();
    }
    
    public void processConfigPacket(Config conf) {
	allowed[conf.address] = conf.personaNonGrata;
    }
    public boolean allowPacket(Header h) {
	boolean rv =  allowed[h.source];
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



class FineLockingPNG implements PNG {
    boolean[] allowed;
    ReadWriteLock[] locks;
    public FineLockingPNG(int a) {
	allowed = new boolean[a];
	locks = new ReentrantReadWriteLock[a];
	for (int i = 0; i < a; i++) {
	    locks[i] = new ReentrantReadWriteLock();
	}
    }
    public void processConfigPacket(Config conf) {	
	allowed[conf.address] = conf.personaNonGrata;
    
    }
    public boolean allowPacket(Header h) {
	boolean rv =  allowed[h.source];
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

class StripedLockingPNG implements PNG {
    boolean[] allowed;
    ReadWriteLock[] locks;
    public StripedLockingPNG(int a, int nWorkers) {
	allowed = new boolean[a];
	locks = new ReentrantReadWriteLock[nWorkers];
	for (int i = 0; i < nWorkers; i++) {
	    locks[i] = new ReentrantReadWriteLock();
	}
    }
    public void processConfigPacket(Config conf) {
	locks[conf.address % locks.length].writeLock().lock();
	
	allowed[conf.address] = conf.personaNonGrata;

	locks[conf.address % locks.length].writeLock().unlock();
    
    }
    public boolean allowPacket(Header h) {


	boolean rv =  allowed[h.source];
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

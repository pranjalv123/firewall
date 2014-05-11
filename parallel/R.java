import java.util.concurrent.locks.*;
interface R {
    public void processConfigPacket(Config conf);
    public boolean allowPacket(Header h);
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
	lock.writeLock().lock();
	dests[conf.address].setRange(conf.addressBegin, conf.addressEnd, conf.acceptingRange);	
	lock.writeLock().unlock();    
    }

    public boolean allowPacket(Header h) {
	lock.readLock().lock();
	boolean rv = dests[h.dest].get(h.source);
	lock.readLock().unlock();
	return rv;
    }
}

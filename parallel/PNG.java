import java.util.concurrent.locks.*;

interface PNG {
    public void processConfigPacket(Config conf);
    public boolean allowPacket(Header h);
}


class CoarseLockingPNG implements PNG {
    boolean[] allowed;
    ReadWriteLock lock;
    public CoarseLockingPNG(int a) {
	allowed = new boolean[a];
	lock = new ReentrantReadWriteLock();
    }
    public void processConfigPacket(Config conf) {
	lock.writeLock().lock();
	
	allowed[conf.address] = conf.personaNonGrata;

	lock.writeLock().unlock();
    
    }
    public boolean allowPacket(Header h) {
	lock.readLock().lock();

	boolean rv =  allowed[h.source];
	lock.readLock().unlock();
	return rv;
    }
}

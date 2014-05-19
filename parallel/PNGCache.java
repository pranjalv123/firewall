import java.lang.*;
import java.util.*;

//this cache is per-thread, but they should all share the same R

class RCache implements R{
    R r;
    RCache[] others;
    Set<Integer> validDests;  
    LinkedHashMap<AbstractMap.SimpleEntry<Integer, Integer>, Boolean> lru;

    RCache(R r) {
	this.r = r;
	this.validDests = new HashSet<Integer>();
	lru = new LinkedHashMap<AbstractMap.SimpleEntry<Integer, Integer>, Boolean>  () { 
	    protected boolean removeEldestEntry(Map.Entry eldest) {
		return size() > 100;
	    }
	};
    }
    
    void setOtherCaches(RCache[] others) {
	this.others = others;	    
    }

    void invalidate(int address) {
	validDests.remove(address);
    }

    //must already be locked if we don't want bad things to happen
    public void processConfigPacket(Config conf) {
	r.processConfigPacket(conf);
	for (int i = 0; i < others.length; i++) {
	    others[i].invalidate(conf.address);
	}
    }

    //should not be locked
    public boolean allowPacket(Header h) {
	if (validDests.contains(h.dest)) {
	    Boolean rv = lru.get(new AbstractMap.SimpleEntry<Integer, Integer>(h.source, h.dest));
	    if (rv != null) {
		return rv;
	    }
	}
	r.lockRead(h.dest);
	boolean rv = r.allowPacket(h); 
	r.unlockRead(h.dest);
	validDests.add(h.dest);
	lru.put(new AbstractMap.SimpleEntry<Integer, Integer>(h.source, h.dest), rv);
	return rv;
    }
    public void lockWrite(int address) {
	r.lockWrite(address);
    }
    public void unlockWrite(int address) {
	r.unlockWrite(address);
    }
    public void lockRead(int address) {
	//	r.lockRead(address);
    }
    public void unlockRead(int address){
	//	r.unlockRead(address);
    }
}

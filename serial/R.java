interface R {
    public void processConfigPacket(Config conf);
    public boolean allowPacket(Header h);
}

class SerialR implements R {
    IntervalSkipList[] dests;
    public SerialR(int a) {
	dests = new IntervalSkipList[a];
	for (int i = 0; i < a; i++) {
	    dests[i] = new IntervalSkipList(a+1);
	}
    }
    
    public void processConfigPacket(Config conf) {
	dests[conf.address].setRange(conf.addressBegin, conf.addressEnd, conf.acceptingRange);
    }

    public boolean allowPacket(Header h) {
	return dests[h.dest].get(h.source);
    }
}

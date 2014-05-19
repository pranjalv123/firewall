import java.lang.*;
import java.util.*;

interface PacketProcessor {
    public void process(Packet p);
    public void processConfigPacket(Config conf);
    public void processDataPacket(Header header, Body body);
}

class LockingPacketProcessor implements PacketProcessor {
    Histogram h;
    PNG png;
    R r;
    Fingerprint f;
    public LockingPacketProcessor(Histogram h,  PNG png, R r) {
	this.h = h;
	this.png = png;
	this.r = r;
	f = new Fingerprint();
    }
    public void process(Packet p) {
	if (p.type == Packet.MessageType.ConfigPacket) {
	    processConfigPacket(p.config);
	} else if (p.type == Packet.MessageType.DataPacket) {
	    processDataPacket(p.header, p.body);
	}
    }
    public void processConfigPacket(Config conf) {
	png.lockWrite(conf.address);
	r.lockWrite(conf.address);
	png.processConfigPacket(conf);
	r.processConfigPacket(conf);
	r.unlockWrite(conf.address);
	png.unlockWrite(conf.address);
    }
    public void processDataPacket(Header header, Body body) {
	png.lockRead(header.source);
	r.lockRead(header.dest);
	boolean allowed = png.allowPacket(header) && r.allowPacket(header);
	r.unlockRead(header.dest);
	png.unlockRead(header.source);
	if (allowed) {
	    f.getFingerprint(body.iterations, body.seed);
	}
    }
    
}



class ParallelPacketWorker implements Runnable {
    PaddedPrimitive<Boolean> done;
    WaitFreeQueue q;
    PacketProcessor processor;
    int count;
    public ParallelPacketWorker(WaitFreeQueue q, 
				     PaddedPrimitive<Boolean> done,
				     PacketProcessor worker) {
	this.done = done;
	this.q = q;
	count = 0;
	processor = worker;
    }
    
    public void run() {
	while (!done.value) {
	    try {
		Packet p = q.deq();
		count++;
		processor.process(p);
	    } catch (EmptyException f) {
	    }
	}
    }
}

interface Dispatcher extends Runnable{
    public int count();
}

class DumbDispatcher implements Dispatcher {
    PaddedPrimitive<Boolean> done;
    WaitFreeQueue[] qs;
    PacketGenerator gen;
    int count;
    public DumbDispatcher(PaddedPrimitive<Boolean> done, WaitFreeQueue[] qs, PacketGenerator gen) {
	count = 0;
	this.gen = gen;
	this.qs = qs;
	this.done = done;
    }
    public void run() {
	int i = 0;
	Packet p = gen.getPacket();
	while (!done.value) {	    
	    try {		
		qs[i].enq(p);
		p = gen.getPacket();
		count++;
	    } catch (FullException f) {
	    }
	    i++;
	    i %= qs.length;
	}
    }
    public int count() {
	return count;
    }
}


class SmartDispatcher implements Dispatcher {
    PaddedPrimitive<Boolean> done;
    WaitFreeQueue[] qs;
    PacketGenerator gen;
    int count;
    SmartDispatcher(PaddedPrimitive<Boolean> done, WaitFreeQueue[] qs, PacketGenerator gen) {
	count = 0;
	this.gen = gen;
	this.qs = qs;
	this.done = done;
    }
    public void run() {
	System.out.println(qs.length);
	int i = 0;
	Packet p = gen.getPacket();
	while (!done.value) {
	    if (p.type == Packet.MessageType.DataPacket) {
		try {
		    qs[p.header.dest % qs.length].enq(p);
		    p = gen.getPacket();
		    count++;
		} catch (FullException f) {
		}
	    } else {
		try {
		    qs[p.config.address % qs.length].enq(p);
		    p = gen.getPacket();
		    count++;
		} catch (FullException f) {
		}		
	    }
	}
    }
    public int count() {
	return count;
    }
}

class LockingPacket {
    static Histogram getHistogram(int i, int nAddresses, int nThreads) {
	switch(i) {
	case 0:
	    return new CoarseLockingHistogram(nAddresses);
	case 1:
	    return new FineLockingHistogram(nAddresses);
	case 2:
	    return new StripedLockingHistogram(nAddresses, nThreads);
	case 3:
	    return new AtomicHistogram(nAddresses, nThreads);
	}
	return new CoarseLockingHistogram(nAddresses);
    }


    static R getR(int i, int nAddresses, int nThreads) {
	switch(i) {
	case 0:
	    return new CoarseLockingR(nAddresses);
	case 1:
	    return new FineLockingR(nAddresses);
	case 2:
	    return new StripedLockingR(nAddresses, nThreads);
	}
	return new CoarseLockingR(nAddresses);
    }


    static PNG getPNG(int i, int nAddresses, int nThreads) {
	switch(i) {
	case 0:
	    return new CoarseLockingPNG(nAddresses);
	case 1:
	    return new FineLockingPNG(nAddresses);
	case 2:
	    return new StripedLockingPNG(nAddresses, nThreads);
	}
	return new CoarseLockingPNG(nAddresses);
    }



    public static void main(String[] args) {

	final int numMilliseconds = Integer.parseInt(args[0]);    
	int numAddressesLog = Integer.parseInt(args[1]);
	int numTrainsLog = Integer.parseInt(args[2]);
	double meanTrainSize =  Double.parseDouble(args[3]);
	double meanTrainsPerComm = Double.parseDouble(args[4]);
	int meanWindow =  Integer.parseInt(args[5]);
	int meanCommsPerAddress =  Integer.parseInt(args[6]);
	int meanWork =  Integer.parseInt(args[7]);
	double configFraction = Double.parseDouble(args[8]);
	double pngFraction =  Double.parseDouble(args[9]);
	double acceptingFraction =  Double.parseDouble(args[10]);
	int nWorkers = Integer.parseInt(args[11]);
	int lockType = Integer.parseInt(args[12]);
	int cacheR = Integer.parseInt(args[13]);
	int dispType = Integer.parseInt(args[14]);

	PacketGenerator gen = new PacketGenerator(numAddressesLog,
						  numTrainsLog,
						  meanTrainSize,
						  meanTrainsPerComm,
						  meanWindow,
						  meanCommsPerAddress,
						  meanWork,
						  configFraction,
						  pngFraction,
						  acceptingFraction);
	StopWatch timer = new StopWatch();
	ParallelPacketWorker[] workers = new ParallelPacketWorker[nWorkers];
	WaitFreeQueue[] queues = new WaitFreeQueue[nWorkers];
	int numAddresses = (1<<numAddressesLog);
	Histogram h = getHistogram(lockType, numAddresses + 1, nWorkers);
	PNG png = getPNG(lockType, numAddresses + 1, nWorkers);
	R r = getR(lockType, numAddresses + 1, nWorkers);
	PacketProcessor processor = new LockingPacketProcessor(h, png, r);
	for (int i = 0; i < numAddresses * Math.sqrt(numAddresses); i++) {
	    processor.processConfigPacket(gen.getConfigPacket().config);
	}
	PaddedPrimitive<Boolean> wDone = new PaddedPrimitive<Boolean>(false);
	PaddedPrimitive<Boolean> dDone = new PaddedPrimitive<Boolean>(false);
	RCache[] caches;
	caches = new RCache[nWorkers];
	if (cacheR > 0) {
	    for (int i = 0; i < nWorkers; i++) {
		caches[i] = new RCache(r);
	    }
	    for (int i = 0; i < nWorkers; i++) {
		caches[i].setOtherCaches(caches);
	    }
	    
	}
	for (int i = 0; i < nWorkers; i++) {
	    queues[i] = new WaitFreeQueue(8);	   
	    LockingPacketProcessor proc ;
	    if (cacheR > 0) {
		proc = new LockingPacketProcessor(h, png, caches[i]);
	    } else {
		proc = new LockingPacketProcessor(h, png, r);
	    }
	    workers[i] = new ParallelPacketWorker(queues[i], wDone, proc);
	}
	Thread[] workerThreads = new Thread[nWorkers];
	for (int i = 0; i < nWorkers; i++) {
	     workerThreads[i] = new Thread(workers[i]);
	     workerThreads[i].start();
	}
	Dispatcher disp;
	if (dispType == 0) {
	    disp = new DumbDispatcher(dDone, queues, gen);
	} else {
	    disp = new SmartDispatcher(dDone, queues, gen);
	}
	Thread dispThread = new Thread(disp);
	dispThread.start();
	try {
	    timer.startTimer();
	    Thread.sleep(numMilliseconds);
	    dDone.value = true;
	    dispThread.join();
	    wDone.value = true;
	    for (int i = 0; i < workerThreads.length; i++) 
		workerThreads[i].join();
	} catch (InterruptedException e) {}
	timer.stopTimer();
	System.out.println("==");
	System.out.println("NumAddressesLog: " + numAddressesLog);
	System.out.println("NumTrainsLog: " + numTrainsLog);
	System.out.println("MeanTrainSize: " + meanTrainSize);
	System.out.println("MeanTrainsPerComm: " + meanTrainsPerComm);
	System.out.println("MeanWindow: " + meanWindow);
	System.out.println("MeanCommsPerAddress: " + meanCommsPerAddress);
	System.out.println("MeanWork: " + meanWork);
	System.out.println("ConfigFraction: " + configFraction);
	System.out.println("PNGFraction: " + pngFraction);
	System.out.println("AcceptingFraction: " + acceptingFraction);
	System.out.println("nWorkers: " + nWorkers);
	System.out.println("lockType:" + lockType);
	System.out.println("cacheR:" + cacheR);
	System.out.println("dispType:" + dispType);
	System.out.println("Packets/ms: " + (double)(disp.count())/timer.getElapsedTime());
    }
}


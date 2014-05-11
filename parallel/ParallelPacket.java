import java.lang.*;
import java.util.*;

interface PacketProcessor {
    public void process(Packet p);
    public void processConfigPacket(Config conf);
    public void processDataPacket(Header header, Body body);
}

class CoarseLockingPacketProcessor implements PacketProcessor {
    CoarseLockingHistogram h;
    CoarseLockingPNG png;
    CoarseLockingR r;
    Fingerprint f;
    public CoarseLockingPacketProcessor(CoarseLockingHistogram h,  CoarseLockingPNG png, CoarseLockingR r) {
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
	png.lock.writeLock().lock();
	r.lock.writeLock().lock();
	png.processConfigPacket(conf);
	r.processConfigPacket(conf);
	r.lock.writeLock().unlock();
	png.lock.writeLock().unlock();
    }
    public void processDataPacket(Header header, Body body) {
	png.lock.readLock().lock();
	r.lock.readLock().lock();
	boolean allowed = png.allowPacket(header) && r.allowPacket(header);
	r.lock.readLock().unlock();
	png.lock.readLock().unlock();
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


class Dispatcher implements Runnable {
    PaddedPrimitive<Boolean> done;
    WaitFreeQueue[] qs;
    PacketGenerator gen;
    int count;
    public Dispatcher(PaddedPrimitive<Boolean> done, WaitFreeQueue[] qs, PacketGenerator gen) {
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
}

class CoarseLockingPacket {
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
	CoarseLockingHistogram h = new CoarseLockingHistogram(numAddresses + 1);
	CoarseLockingPNG png = new CoarseLockingPNG(numAddresses + 1);
	CoarseLockingR r = new CoarseLockingR(numAddresses + 1);
	PacketProcessor processor = new CoarseLockingPacketProcessor(h, png, r);
	for (int i = 0; i < numAddresses * Math.sqrt(numAddresses); i++) {
	    processor.processConfigPacket(gen.getConfigPacket().config);
	}
	PaddedPrimitive<Boolean> wDone = new PaddedPrimitive<Boolean>(false);
	PaddedPrimitive<Boolean> dDone = new PaddedPrimitive<Boolean>(false);
	for (int i = 0; i < nWorkers; i++) {
	    
	    CoarseLockingPacketProcessor proc = new CoarseLockingPacketProcessor(h, png, r);
	    workers[i] = new ParallelPacketWorker(queues[i], wDone, proc);
	}
	Thread[] workerThreads = new Thread[nWorkers];
	for (int i = 0; i < nWorkers; i++) {
	     workerThreads[i] = new Thread(workers[i]);
	     workerThreads[i].start();
	}
	Dispatcher disp = new Dispatcher(dDone, queues, gen);
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
	System.out.println((double)(disp.count)/timer.getElapsedTime());
    }
}
import java.lang.*;
import java.util.*;

class PacketProcessor {
    Histogram h;
    PNG png;
    R r;
    Fingerprint f;
    public PacketProcessor(Histogram h, PNG png, R r) {
	this.h = h;
	this.png = png;
	this.r = r;
	f = new Fingerprint();
    }
    void process(Packet p) {
	if (p.type == Packet.MessageType.ConfigPacket) {
	    processConfigPacket(p.config);
	} else if (p.type == Packet.MessageType.DataPacket) {
	    processDataPacket(p.header, p.body);
	}
    }
    void processConfigPacket(Config conf) {
	png.processConfigPacket(conf);
	r.processConfigPacket(conf);
    }
    void processDataPacket(Header header, Body body) {
	if (png.allowPacket(header) && r.allowPacket(header)) {
	    f.getFingerprint(body.iterations, body.seed);
	}
    }
    
}


class STMPacketWorker implements Runnable {
    PaddedPrimitive<Boolean> done;
    WaitFreeQueue q;
    PacketProcessor processor;
    int count;
    public STMPacketWorker(WaitFreeQueue q, 
			      PaddedPrimitive<Boolean> done,
			      Histogram h,
			      PNG png,
			      R r) {
	this.done = done;
	this.q = q;
	count = 0;

	processor = new PacketProcessor(h, png, r);
    }
    
    public void run() {
	while (!done.value) {
	    count++;
	    Packet p = q.deq();
	    processor.process(p);
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

class STMPacket {
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
	STMPacketWorker[] workers = new STMPacketWorker[nWorkers];
	WaitFreeQueue[] queues = new WaitFreeQueue[nWorkers];
	int numAddresses = (1<<numAddresses);
	Histogram h = new SerialHistogram(numAddresses + 1);
	PNG png = new SerialPNG(numAddresses + 1);
	R r = new SerialR(numAddresses + 1);
	PaddedPrimitive<Boolean> wDone = new PaddedPrimitive<Boolean>(false);
	PaddedPrimitive<Boolean> dDone = new PaddedPrimitive<Boolean>(false);
	for (int i = 0; i < nWorkers; i++) {
	    workers[i] = new STMPacketWorker(queues[i], wDone, h, png, r);
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

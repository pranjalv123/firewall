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


class SerialPacketWorker implements Runnable {
    PacketGenerator gen;
    public volatile boolean done;
    PacketProcessor processor;
    int count;
    public SerialPacketWorker(PacketGenerator gen, int numAddresses) {
	this.gen = gen;
	this.done = false;
	count = 0;
	Histogram h = new SerialHistogram(numAddresses + 1);
	PNG png = new SerialPNG(numAddresses + 1);
	R r = new SerialR(numAddresses + 1);
	processor = new PacketProcessor(h, png, r);
    }
    
    public void run() {
	while (!done) {
	    count++;
	    Packet p = gen.getPacket();
	    processor.process(p);
	}
    }
}


class SerialPacket {
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
	SerialPacketWorker worker = new SerialPacketWorker(gen, (1<<numAddressesLog));
	Thread workerThread = new Thread(worker);
	workerThread.start();
	try {
	    timer.startTimer();
	    Thread.sleep(numMilliseconds);
	    worker.done = true;
	    workerThread.join();
	    timer.stopTimer();
	} catch (InterruptedException e) {}
	System.out.println((double)(worker.count)/timer.getElapsedTime());
    }
}

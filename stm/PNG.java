import org.deuce.Atomic;
interface PNG {
    public void processConfigPacket(Config conf);
    public boolean allowPacket(Header h);
}

class SerialPNG implements PNG {
    boolean[] allowed;
    public SerialPNG(int a) {
	allowed = new boolean[a];
    }
    @Atomic
    public void processConfigPacket(Config conf) {
	allowed[conf.address] = conf.personaNonGrata;
    }
    @Atomic
    public boolean allowPacket(Header h) {
	return allowed[h.source];
    }
}

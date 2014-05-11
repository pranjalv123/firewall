import java.util.Random;
import org.deuce.Atomic;


class IntervalSkipList {    
    final int MAX_LEVEL = 5;    
    class Node {
	public int randomLevel() {
	    double P = 0.5;
	    int lvl = (int)(Math.log(1.-Math.random())/Math.log(1.-P));
	    return Math.min(lvl, MAX_LEVEL);
	} 
	int level;
	public Node[] next;
	int value;
	boolean tf;
	Node(int value, boolean tf) {
	    this.level = randomLevel() + 1;
	    this.next = new Node[level];
	    this.value = value;
	    this.tf = tf;	    
	}
	Node(int level, int value, boolean tf) {
	    this.level = level + 1;
	    this.next = new Node[this.level];
	    this.value = value;
	    this.tf = tf;	    
	}
	void Print() {
	    System.out.println(value + " " + tf);
	    for (int i = 0; i < level; i++) {
		if (next[i] != null) {
		    System.out.println(" -> " + next[i].value);
		} else {
		    System.out.println(" -> " + "null");
		}
	    }
	}
    }
    
    Node head;
    
    public IntervalSkipList(int naddr) {
	head = new Node(MAX_LEVEL, -1, true);
	Node tail = new Node(MAX_LEVEL, naddr, false);
	for (int i = 0; i < head.level; i++) {
	    head.next[i] = tail;
	    //	    System.out.println(i + " --> " + head.next[i].value);
	}
    }       
    @Atomic
    public void setRange(int start, int end, boolean tf) {
	//find start of range
	Node pred = head;
	Node curr = head;
	
	Node preds[] = new Node[MAX_LEVEL + 1]; 
	Node succs[] = new Node[MAX_LEVEL + 1]; 
	
	Node begin = null;	
	
	for (int level = MAX_LEVEL; level >= 0; level--) {
	    curr = pred.next[level];
	    while (curr.value < start) {
		pred = curr;
		curr = curr.next[level];
	    }
	    preds[level] = pred;
	    if (curr.value == start && begin == null) {
		begin = curr;
	    }
	}
	
	if (begin == null) {
	    if (preds[0].tf == tf) {
		//meld with previous value
	    }
	    else {
		//insert new node
		begin = new Node(start, tf);
		for (int level = 0; level < begin.level; level++) {
		    begin.next[level] = preds[level].next[level];
		    preds[level].next[level] = begin;
		    preds[level] = begin;
		}
	    }
	} else { //we found a node that already has start as a value
	    //	    System.out.println("Found begin!");
	    if (begin.tf == tf) { 
		for (int level = 0; level < begin.level; level++) {
		    preds[level] = begin;
		}
	    } else {
		//we can meld with the previous value
		begin = preds[0];
	    }
	}
	
	//find end node
	Node last = null;
	curr = head;
	pred = head;
	
	
	for (int level = MAX_LEVEL; level >= 0; level--) {
	    curr = pred.next[level];
	    while (curr.value < end) {
		pred = curr;
		curr = curr.next[level];
	    }
	    succs[level] = curr;
	    if (curr.value == end && last == null) {
		last = curr;
	    }
	}
	
	//succs contains the nodes immediately outside the range in each sublist
	
	if (last == null) {
	    if (succs[0] != null && succs[0].tf != tf) {
		//we can link to this node
		last = succs[0];
	    } else {
		last = new Node(end, !tf);
		for (int level = 0; level < last.level; level++) {
		    last.next[level] = succs[level];
		    succs[level] = last;
		}
	    }
	} else {
	    //	    System.out.println("Found end!");
	    //we already found a node with end as the value
	    if (last.tf == tf) { 
		//we can meld with the next value
		for (int i = 0; i < last.level; i++) {
		    succs[i] = succs[i].next[i];
		}

	    } else {
		//we can just connect to the succs as is, since the
		//closest one has the right parity
	    }
	}

	for (int i = 0; i < preds.length; i++) {
	    //	    System.out.println("Connecting " + preds[i].value + " to " + succs[i].value);
	    preds[i].next[i] = succs[i];
	}
    }
    @Atomic
    public boolean get(int val) {
	Node pred = head;
	Node curr = head;
	for (int level = MAX_LEVEL; level >= 0; level--) {
	    curr = pred.next[level];
	    while (curr != null && curr.value < val) {
		pred = curr;
		curr = curr.next[level];
	    }
	    if (curr.value == val) {
		return curr.tf;
	    }
	}	
	return pred.tf;
    }
    
    public boolean verifyAlternating() {
	Node pred = head;
	Node curr = head.next[0];
	while (curr != null) {
	    if (pred.tf == curr.tf) {
		System.out.println("Failed to verify alternating!");
		return false;
	    }
	    pred = curr;
	    curr = curr.next[0];
	}
	return true;
    }

    public void Print() {
	Node curr = head;
	System.out.println("------------------------");
	while (curr != null) {
	    curr.Print();
	    curr = curr.next[0];
	}
	System.out.println("------------------------");
    }
}

class SkipListTest {
    public static void main (String[] args) {
	IntervalSkipList sl = new IntervalSkipList(101);
	//	sl.Print();
	Random r = new Random();
	for (int i = 0; i < 1000; i++) {
	    int a = r.nextInt(100);
	    int b = r.nextInt(100);
	    if (a == b) {
		continue;
	    }
	    if (a > b) {
		int tmp = a;
		a = b;
		b = tmp;
	    }
	    boolean tf = r.nextBoolean();
	    
	    //	    System.out.println(a + "; " +  b + "; " + tf);
	    sl.setRange(a, b, tf);
	    if (a <= 50  && b > 50) {
		if (sl.get(50) != tf) {
		    System.out.println("Failed to get right result " + a +" " +  b);
		}
	    }
	    sl.verifyAlternating();
	    //	    sl.Print();
	}
    }
}

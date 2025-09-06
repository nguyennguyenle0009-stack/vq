package rt.server.input;

import rt.server.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InputQueue {
    private final Queue<InputEvent> q = new ConcurrentLinkedQueue<>();
    public void offer(InputEvent e){ q.offer(e); }
    public List<InputEvent> drain(){
        var list = new ArrayList<InputEvent>();
        for (InputEvent e; (e=q.poll())!=null; ) list.add(e);
        return list;
    }
}
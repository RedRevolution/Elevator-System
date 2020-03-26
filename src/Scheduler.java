import com.oocourse.elevator3.PersonRequest;

import java.util.ArrayList;

public class Scheduler extends Thread {
    private ArrayList<Runelevator> elevators;
    private ArrayList<PersonRequest> transRequests;
    private boolean exit;
    private Object exitSignal;
    private Object trIsEmpty; //transRequests

    public Scheduler() {
        elevators = new ArrayList<>();
        transRequests = new ArrayList<>();
        exitSignal = new Object();
        trIsEmpty = new Object();
    }

    public void addElevator(Runelevator elevator) {
        elevators.add(elevator);
    }

    //receive request from IO and alloc
    public void recAndAlloc(PersonRequest request) {
        ArrayList<Integer> effEle = search(request);
        if (!effEle.isEmpty()) {
            choose(effEle).receiveRequest(request, 0);
        } else {
            deCompose(request);
        }
    }

    //signal exit, ends up scheduler thread
    public void toExit() {
        exit = true;
        synchronized (exitSignal) {
            exitSignal.notifyAll();
        }
    }

    //search for elevators which can cope with this request
    private ArrayList search(PersonRequest request) {
        ArrayList<Integer> eles = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Runelevator elevator = elevators.get(i);
            if (elevator.isValidFloor(request.getFromFloor())
                    && elevator.isValidFloor(request.getToFloor())) {
                eles.add(i);
            }
        }
        return eles;
    }

    //choose optimal elevator from elevators which deal with this req
    private Runelevator choose(ArrayList<Integer> eles) {
        if (eles.size() == 1) {
            return elevators.get(eles.get(0));
        }
        int index = 0;
        int reqNum = elevators.get(eles.get(index)).getReqNum();
        for (int i = 1; i < eles.size(); i++) {
            Runelevator elevator = elevators.get(eles.get(i));
            if (elevator.getReqNum() < reqNum) {
                reqNum = elevator.getReqNum();
                index = i;
            }
        }
        return elevators.get(eles.get(index));
    }

    //deCompose transRequest
    private void deCompose(PersonRequest request) {
        int from = request.getFromFloor();
        int to = request.getToFloor();
        int id = request.getPersonId();
        int transFloor;
        boolean up = request.getFromFloor() < request.getToFloor();

        if (from > 15 || (from > 1 && from < 15 && up)) {
            transFloor = 15;
        } else {
            transFloor = 1;
        }

        PersonRequest request1 = new PersonRequest(from, transFloor, id);
        PersonRequest request2 = new PersonRequest(transFloor, to, id);
        choose(search(request1)).receiveRequest(request1, 1);
        transRequests.add(request2);
    }

    //if request1 has been settled, call request2
    public void response(PersonRequest request1) {
        for (PersonRequest req : transRequests) {
            if (req.getPersonId() == request1.getPersonId()) {
                choose(search(req)).receiveRequest(req, 1);
                transRequests.remove(req);
                break;
            }
        }
        synchronized (trIsEmpty) {
            trIsEmpty.notifyAll();
        }
    }

    @Override
    public void run() {
        exit = false;

        //run all elevators
        for (Runelevator elevator : elevators) {
            elevator.start();
        }

        //wait for signal exit
        while (!exit) {
            synchronized (exitSignal) {
                try {
                    exitSignal.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        //if transReq is not empty, wait
        while (!transRequests.isEmpty()) {
            synchronized (trIsEmpty) {
                try {
                    trIsEmpty.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        //send exit signal to all elevators
        for (Runelevator ele : elevators) {
            ele.toExit();
        }
    }
}

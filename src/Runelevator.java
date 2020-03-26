import com.oocourse.TimableOutput;
import com.oocourse.elevator3.PersonRequest;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class Runelevator extends Thread {
    private volatile String eleName;
    private volatile int runTime;
    private volatile int limit;
    private volatile int openTime = 200;
    private volatile int closeTime = 200;
    private volatile int[] validFloor;
    private volatile boolean exit; //signal to stop
    private LinkedBlockingQueue<PersonRequest> personInEle;
    private LinkedBlockingQueue<PersonRequest> requests;
    private ArrayList<PersonRequest> toResponse;
    private Scheduler scheduler;
    private volatile int[] state = new int[3];
    //state[0] = currentFloor
    //state[1] = running state(0:still  1:up -1:down)
    //state[2] = current number of individuals in elevator

    public Runelevator(String eleName, int runTime,
                       int limit, int... validFloor) {
        this.eleName = eleName;
        this.runTime = runTime;
        this.limit = limit;
        this.validFloor = validFloor;
        state[0] = 1; //initial current floor
        state[1] = 0; //init: elevator is still
        state[2] = 0; //init: current personNum
        requests = new LinkedBlockingQueue<>();
        personInEle = new LinkedBlockingQueue<>();
        toResponse = new ArrayList<>();
    }

    public void link(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    //return unfinished request num
    public int getReqNum() {
        return requests.size();
    }

    public boolean isValidFloor(int floor) {
        for (int i = 0; i < validFloor.length; i++) {
            if (floor == validFloor[i]) {
                return true;
            }
        }
        return false;
    }

    public void toExit() {
        exit = true;
        synchronized (requests) {
            requests.notifyAll();
        }
    }

    //flag == 1 : it is a transRequest
    public void receiveRequest(PersonRequest request, int flag) {
        synchronized (requests) {
            synchronized (state) {
                /**
                 * elevator is still
                 * new requests has been input
                 * decide elevator run up or down
                 */
                if (state[1] == 0 && requests.isEmpty()) {
                    int from = request.getFromFloor();
                    int to = request.getToFloor();
                    if ((from > state[0])
                            || (from == state[0] && from < to)) {
                        state[1] = 1;  //up
                    } else {
                        state[1] = -1; //down
                    }
                }
                requests.offer(request);
                if (flag == 1) {
                    toResponse.add(request);
                }
                requests.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        exit = false;
        while (true) {
            while (!exit && requests.isEmpty()) {
                synchronized (requests) {
                    try {
                        requests.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (exit && requests.isEmpty()) {
                break;
            }

            try {
                elevatorRun();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    //persons who can get into the elevator in currentFloor
    private ArrayList enterEle() {
        synchronized (requests) {
            synchronized (state) {
                ArrayList<PersonRequest> personIn = new ArrayList<>();
                boolean flag = false;
                for (PersonRequest req : requests) {
                    if ((req.getFromFloor() > state[0] && state[1] == 1)
                            || (req.getFromFloor() < state[0]
                            && state[1] == -1)) {
                        flag = true;
                        break;
                    }
                }
                if (flag) {
                    for (PersonRequest req : requests) {
                        boolean up = req.getFromFloor() < req.getToFloor();
                        if ((req.getFromFloor() == state[0]
                                && up && state[1] == 1)
                                || (req.getFromFloor() == state[0]
                                && !up && state[1] == -1)) {
                            personIn.add(req);
                        }
                    }
                } else {
                    boolean flag2 = false;
                    for (PersonRequest req : requests) {
                        boolean up = req.getFromFloor() < req.getToFloor();
                        if ((req.getFromFloor() == state[0]
                                && up && state[1] == 1)
                                || (req.getFromFloor() == state[0]
                                && !up && state[1] == -1)) {
                            personIn.add(req);
                            flag2 = true;
                        }
                    }
                    if (!flag2) {
                        for (PersonRequest req : requests) {
                            boolean up = req.getFromFloor() < req.getToFloor();
                            if ((req.getFromFloor() == state[0]
                                    && up && state[1] == -1)
                                    || (req.getFromFloor() == state[0]
                                    && !up && state[1] == 1)) {
                                personIn.add(req);
                            }
                        }
                    }
                }
                return personIn;
            }
        }
    }

    private void elevatorRun() throws InterruptedException {
        while (true) {
            boolean out = false;
            ArrayList<PersonRequest> personIn = enterEle();
            for (PersonRequest req : personInEle) {
                if (req.getToFloor() == state[0]) {
                    out = true;
                }
            }
            if (!personIn.isEmpty() || out) {
                TimableOutput.println(
                        String.format("OPEN-%d-%s",
                                state[0], eleName));
                for (PersonRequest req : personInEle) {
                    if (req.getToFloor() == state[0]) {
                        TimableOutput.println(
                                String.format("OUT-%d-%d-%s",
                                        req.getPersonId(), state[0], eleName));
                        if (toResponse.contains(req)) {
                            scheduler.response(req);
                        }
                        personInEle.remove(req);
                        state[2] -= 1; //person_num--
                    }
                }
                sleep(openTime + closeTime);
                personInAndChangeState();

                if (state[1] == 0) {
                    break;
                }
            }

            sleep(runTime);
            state[0] += state[1];
            if (state[0] == 0) {
                state[0] += state[1];
            }
            TimableOutput.println(
                    String.format("ARRIVE-%d-%s",
                            state[0], eleName));
        }
    }

    private void personInAndChangeState() {
        synchronized (requests) {
            synchronized (state) {
                //person enter elevator
                ArrayList<PersonRequest> personIn = enterEle();
                for (PersonRequest req : personIn) {
                    if (state[2] >= limit) {
                        break;
                    }
                    personInEle.add(req);
                    requests.remove(req);
                    state[2] += 1;
                    TimableOutput.println(
                            String.format("IN-%d-%d-%s",
                                    req.getPersonId(), state[0], eleName));
                }
                TimableOutput.println(
                        String.format("CLOSE-%d-%s",
                                state[0], eleName));

                //change state
                if (personInEle.isEmpty() && requests.isEmpty()) {
                    state[1] = 0;
                }

                if (personInEle.isEmpty() && !requests.isEmpty()) {
                    boolean changeDirection = true;
                    for (PersonRequest req : requests) {
                        int from = req.getFromFloor();
                        if ((from > state[0] && state[1] == 1)
                                || (from < state[0] && state[1] == -1)) {
                            changeDirection = false;
                            break;
                        }
                    }
                    if (changeDirection) {
                        state[1] *= -1;
                    }
                }

                if (!personInEle.isEmpty()) {
                    PersonRequest req = personInEle.peek();
                    if (req.getFromFloor() < req.getToFloor()) {
                        state[1] = 1;
                    } else {
                        state[1] = -1;
                    }
                }

            }
        }
    }
}

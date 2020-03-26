import com.oocourse.TimableOutput;
import com.oocourse.elevator3.ElevatorInput;
import com.oocourse.elevator3.PersonRequest;

import java.io.IOException;

import static java.lang.Thread.sleep;

public class Main {
    public static void main(String[] args) throws IOException {
        TimableOutput.initStartTimestamp();
        Runelevator elevatorA = new Runelevator("A",400, 6,
                new int[]{-3, -2, -1, 1, 15, 16, 17, 18, 19, 20});
        Runelevator elevatorB = new Runelevator("B",500, 8,
                new int[]{-2, -1, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12,
                          13, 14, 15});
        Runelevator elevatorC = new Runelevator("C",600, 7,
                new int[]{1, 3, 5, 7, 9, 11, 13, 15});
        //create a scheduler thread to control elevators
        Scheduler scheduler = new Scheduler();
        //link
        scheduler.addElevator(elevatorA);
        scheduler.addElevator(elevatorB);
        scheduler.addElevator(elevatorC);
        elevatorA.link(scheduler);
        elevatorB.link(scheduler);
        elevatorC.link(scheduler);
        //scheduler begins working
        scheduler.start();

        try {
            sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ElevatorInput elevatorInput = new ElevatorInput(System.in);
        while (true) {
            PersonRequest request = elevatorInput.nextPersonRequest();
            if (request == null) {
                scheduler.toExit();
                break;
            } else {
                scheduler.recAndAlloc(request);
            }
        }
        elevatorInput.close();
    }
}

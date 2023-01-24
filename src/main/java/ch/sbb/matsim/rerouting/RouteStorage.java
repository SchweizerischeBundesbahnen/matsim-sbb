package ch.sbb.matsim.rerouting;


public class RouteStorage {

    String startID;
    String endID;
    String startTime;
    String endTime;

    static int idCounter = 0;
    int id;

    @Override
    public boolean equals(Object o) {

        // If the object is compared with itself then return true
        if (o == this) {
            return true;
        }

        /* Check if o is an instance of Complex or not
          "null instanceof [type]" also returns false */
        if (!(o instanceof RouteStorage rS)) {
            return false;
        }

        // typecast o to Complex so that we can compare data members

        // Compare the data members and return accordingly
        return startID.equals(rS.startID)
            && endID.equals(rS.endID)
            && startTime.equals(rS.startTime)
            && endTime.equals(rS.endTime);
    }
}
